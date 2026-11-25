package com.ahu.ahutong.data.chaoxing

import android.util.Base64
import android.util.Log
import com.ahu.ahutong.data.SchoolConfig
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Type
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher

/** 一次登录后的会话信息（不保存密码）。 */
data class ChaoxingSession(
    val username: String,
    val studentId: String,
    val semester: String,
    val campusId: String,
    val studentName: String?,
)

/**
 * 民政职业大学 · 超星教务 HTTP 客户端。
 *
 * 流程（与 `chaoxing-schedule` 脚本实测一致）：
 * 1. `GET /admin?sfjrxk=1` 取登录页与 RSA 公钥；
 * 2. `password` 用 RSA/ECB/PKCS1Padding 加密后 Base64，POST `/admin/login`；
 * 3. `GET /admin/pkgl/xskb/queryKbForXsd` 读取 `xhid` / `xnxq` / `xqdm`；
 * 4. 按页面顺序 POST `checkSfkc`、`checkWfwSfpj`；
 * 5. `GET /admin/api/getZclistByXnxq` 取作息与校历；
 * 6. `GET /admin/pkgl/xskb/sdpkkbList` 取排课明细。
 *
 * 重要：5、6 两个接口必须带 `X-Requested-With: XMLHttpRequest` 与
 * `Accept: application/json`，否则服务端会返回 HTTP 500（实测）。
 * Cookie 复用工程里持久化的 [CookieManager.cookieJar]，冷启动可直接复用会话。
 */
class ChaoxingClient(
    private val baseUrl: String = SchoolConfig.CHAOXING_BASE_URL,
    private val cookieJar: CookieJar = CookieManager.cookieJar,
) {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseHttpUrl: HttpUrl = baseUrl.toHttpUrl()

    fun hasSessionCookies(): Boolean =
        runCatching { cookieJar.loadForRequest(baseHttpUrl).isNotEmpty() }.getOrDefault(false)

    suspend fun login(username: String, password: String): ChaoxingSession = withContext(Dispatchers.IO) {
        require(username.isNotBlank()) { "请输入学号" }
        require(password.isNotBlank()) { "请输入密码" }

        val loginPage = get(SchoolConfig.CHAOXING_LOGIN_PAGE_PATH)
        val publicKey = ChaoxingParsing.extractPublicKey(loginPage)
            ?: throw ChaoxingException(
                ChaoxingErrorKind.SCHEMA_CHANGED,
                "登录页没有预期的 RSA 公钥，教务系统可能已改版",
            )
        val encryptedPassword = encryptPassword(password, publicKey)
        val loginResponse = postForm(
            path = SchoolConfig.CHAOXING_LOGIN_PATH,
            form = linkedMapOf(
                "username" to username.trim(),
                "password" to encryptedPassword,
                "vcode" to "",
                "jcaptchaCode" to "",
                "rememberMe" to "",
            ),
        )
        val finalUrl = loginResponse.finalUrl
        if (finalUrl.contains("jcaptchaError=1", ignoreCase = true) ||
            finalUrl.contains("jcaptchaDefect=1", ignoreCase = true)
        ) {
            throw ChaoxingException(
                ChaoxingErrorKind.CAPTCHA_REQUIRED,
                "教务系统要求验证码，请稍后在网页端完成验证或更换网络重试",
            )
        }
        if (ChaoxingParsing.containsLoginForm(loginResponse.body)) {
            throw ChaoxingException(
                ChaoxingErrorKind.LOGIN_FAILED,
                extractLoginError(loginResponse.body) ?: "登录失败，请检查学号与密码",
            )
        }

        val studentPage = get(SchoolConfig.CHAOXING_STUDENT_TABLE_PATH)
        val page = parseStudentPage(studentPage, expectedSemester = null)
        ChaoxingSession(
            username = username.trim(),
            studentId = page.studentId,
            semester = page.semester,
            campusId = page.campusId,
            studentName = null,
        )
    }

    suspend fun fetchSchedule(semester: String? = null): ChaoxingSchedule = withContext(Dispatchers.IO) {
        val target = semester?.trim()?.takeIf { it.isNotEmpty() }
        if (target != null && !Regex("""^\d{4}-\d{4}-[12]$""").matches(target)) {
            throw ChaoxingException(ChaoxingErrorKind.INVALID_SEMESTER, "学期格式应为 2026-2027-1")
        }

        val pageUrl = (SchoolConfig.CHAOXING_BASE_URL + SchoolConfig.CHAOXING_STUDENT_TABLE_PATH).toHttpUrl()
        val studentPageUrl = if (target == null) pageUrl else pageUrl.newBuilder()
            .addQueryParameter("xnxq", target)
            .build()
        val studentPage = get(studentPageUrl.toString())
        val page = parseStudentPage(studentPage, expectedSemester = target)

        // 与官方课表页一致：先通过「可否查看课表」「是否已评教」检查，被拒绝时直接报错。
        ensureApiOk(
            postForm(
                path = "/admin/xsd/kcapcx/checkSfkc",
                form = linkedMapOf("xnxq" to page.semester, "type" to "1"),
                ajax = true,
            ).body,
            "课表查询被拒绝",
        )
        ensureApiOk(
            postForm(
                path = "/admin/xsd/pkgl/xskb/checkWfwSfpj",
                form = linkedMapOf("xnxq" to page.semester),
                ajax = true,
            ).body,
            "未完成评教，教务系统拒绝了课表查询",
        )

        val timeConfigUrl = (SchoolConfig.CHAOXING_BASE_URL + SchoolConfig.CHAOXING_TIME_CONFIG_PATH)
            .toHttpUrl().newBuilder()
            .addQueryParameter("xnxq", page.semester)
            .addQueryParameter("role", "")
            .addQueryParameter("userId", "")
            .addQueryParameter("xqid", page.campusId)
            .build()
        val timeConfig = parseTimeConfig(getJson(timeConfigUrl.toString()))

        val lessonUrl = (SchoolConfig.CHAOXING_BASE_URL + SchoolConfig.CHAOXING_LESSON_LIST_PATH)
            .toHttpUrl().newBuilder()
            .addQueryParameter("xnxq", page.semester)
            .addQueryParameter("xhid", page.studentId)
            .addQueryParameter("xqdm", page.campusId)
            .addQueryParameter("zdzc", "")
            .addQueryParameter("zxzc", "")
            .addQueryParameter("xskbxslx", "0")
            .build()
        val rows = parseLessonRows(getJson(lessonUrl.toString()))

        val periods = ChaoxingParsing.parsePeriods(timeConfig)
        ChaoxingParsing.normalize(
            rows = rows,
            periods = periods,
            semester = page.semester,
            campusId = page.campusId,
            campusName = "",
            semesterStartDate = ChaoxingParsing.semesterStartDate(timeConfig),
            currentWeek = ChaoxingParsing.currentWeek(timeConfig),
        )
    }

    // ------------------------------------------------------------------ 成绩查询

    /**
     * 查询成绩（默认「入学以来至今」的本人主修成绩）。
     *
     * 流程对齐 `chaoxing-schedule/grades.mjs`：
     * 成绩页取 queryFields 与学期列表 → 读评教设置 → 逐页 POST 成绩接口 →
     * 校验分页/去重/总数 → 归一化。任何异常都不当成空成绩。
     */
    suspend fun fetchGrades(semester: String? = null): ChaoxingGrades = withContext(Dispatchers.IO) {
        val target = semester?.trim()?.takeIf { it.isNotEmpty() }
        if (target != null && !Regex("""^\d{4}-\d{4}-[12]$""").matches(target)) {
            throw ChaoxingException(ChaoxingErrorKind.INVALID_SEMESTER, "学期格式应为 2025-2026-2")
        }
        val meta = ChaoxingParsing.parseGradePage(get(GRADES_PAGE_PATH))
        if (target != null && target !in meta.availableSemesters) {
            throw ChaoxingException(
                ChaoxingErrorKind.SEMESTER_UNAVAILABLE,
                "成绩查询页未提供 $target 学期，不能据此认定成绩为空",
            )
        }
        // 与官网一致：先读评教设置（读取失败不阻断，最终以成绩接口的 ret 为准）
        runCatching { getJson(EVALUATION_SETTINGS_PATH) }

        val pageSize = 100
        var expected: Triple<Int, Int, Int>? = null
        val collected = mutableListOf<ChaoxingGradeRow>()
        val seen = mutableSetOf<String>()
        for (pn in 1..MAX_GRADE_PAGES) {
            val body = postForm(
                path = GRADES_API_PATH,
                form = linkedMapOf(
                    "page.pn" to pn.toString(),
                    "page.size" to pageSize.toString(),
                    "sort" to "id",
                    "order" to "asc",
                    "startXnxq" to (target ?: "001"),
                    "endXnxq" to (target ?: "001"),
                    "kcmc" to "",
                    "sfjg" to "",
                    "kcxz" to "",
                    "kclx" to "",
                    "kclb" to "",
                    "kcgs" to "",
                    "queryFields" to meta.queryFields,
                ),
                ajax = true,
            ).body
            val page = parseGradePageResponse(body, pn)
            val snapshot = Triple(page.total, page.totalPages, page.pageSize)
            expected = expected ?: snapshot
            if (snapshot != expected) {
                throw ChaoxingException(
                    ChaoxingErrorKind.INVALID_RESPONSE,
                    "读取期间成绩总数或分页发生变化，请重新查询",
                )
            }
            page.results.orEmpty().forEach { row ->
                val id = row.id?.trim().orEmpty()
                if (id.isEmpty()) {
                    throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "成绩记录缺少唯一编号")
                }
                if (!seen.add(id)) {
                    throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "成绩分页出现重复记录，请重新查询")
                }
                if (target != null && row.semester?.trim() != target) {
                    throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "成绩接口没有正确应用学期筛选")
                }
                collected.add(row)
            }
            if (pn >= page.totalPages) break
        }
        val expectedTotal = expected?.first ?: 0
        if (collected.size != expectedTotal) {
            throw ChaoxingException(
                ChaoxingErrorKind.INVALID_RESPONSE,
                "实际读取的成绩条数（${collected.size}）与服务器总数（$expectedTotal）不一致",
            )
        }
        ChaoxingGrades(
            availableSemesters = meta.availableSemesters,
            records = ChaoxingParsing.normalizeGrades(collected, meta),
        )
    }

    private fun parseGradePageResponse(body: String, requestedPage: Int): ChaoxingGradePage {
        if (looksLikeSessionExpired(body)) throw sessionExpired()
        val page = try {
            gson.fromJson(body, ChaoxingGradePage::class.java)
        } catch (e: Exception) {
            null
        } ?: throw ChaoxingException(
            ChaoxingErrorKind.INVALID_RESPONSE,
            "成绩接口未返回预期 JSON，登录可能已过期",
        )
        if (page.ret != 0) {
            val message = ChaoxingParsing.text(page.msg)
            if (message.contains("登录") || message.contains("会话")) throw sessionExpired()
            throw ChaoxingException(
                ChaoxingErrorKind.API_REJECTED,
                message.ifBlank { "成绩查询被拒绝，可能需要先完成评教" },
            )
        }
        val resultCount = page.results.orEmpty().size
        if (page.page != requestedPage ||
            page.pageSize < 1 ||
            page.total < 0 ||
            page.totalPages < 0 ||
            resultCount > page.pageSize ||
            (page.total > 0 && page.totalPages != (page.total + page.pageSize - 1) / page.pageSize) ||
            (page.total == 0 && page.totalPages > 1) ||
            page.page > maxOf(1, page.totalPages)
        ) {
            throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "成绩接口分页信息不一致")
        }
        return page
    }

    // ------------------------------------------------------------------ 请求与解析

    private data class Page(val body: String, val finalUrl: String)

    private data class StudentPage(val studentId: String, val semester: String, val campusId: String)

    private fun parseStudentPage(html: String, expectedSemester: String?): StudentPage {
        if (ChaoxingParsing.containsLoginForm(html) || looksLikeSessionExpired(html)) {
            throw sessionExpired()
        }
        val inputs = ChaoxingParsing.parseHiddenInputs(html)
        val studentId = inputs["xhid"].orEmpty().trim()
        val semester = inputs["xnxq"].orEmpty().trim()
        val campusId = inputs["xqdm"].orEmpty().trim()
        if (studentId.isEmpty() || semester.isEmpty()) {
            throw ChaoxingException(
                ChaoxingErrorKind.SESSION_EXPIRED,
                "未能读取本人课表页参数（xhid/xnxq），请重新登录",
            )
        }
        if (expectedSemester != null && semester != expectedSemester) {
            throw ChaoxingException(
                ChaoxingErrorKind.SEMESTER_UNAVAILABLE,
                "教务系统未提供 $expectedSemester 学期课表",
            )
        }
        return StudentPage(studentId, semester, campusId)
    }

    private fun ensureApiOk(body: String, fallbackMessage: String) {
        parseEnvelope<Any>(
            body = body,
            type = object : TypeToken<ChaoxingResponse<Any>>() {}.type,
            endpoint = fallbackMessage,
        )
    }

    private fun parseTimeConfig(body: String): ChaoxingTimeConfig {
        val response = parseEnvelope<ChaoxingTimeConfig>(
            body = body,
            type = object : TypeToken<ChaoxingResponse<ChaoxingTimeConfig>>() {}.type,
            endpoint = "作息接口",
        )
        return response.data
            ?: throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "作息接口缺少 data")
    }

    private fun parseLessonRows(body: String): List<ChaoxingLessonRow> {
        val response = parseEnvelope<List<ChaoxingLessonRow>>(
            body = body,
            type = object : TypeToken<ChaoxingResponse<List<ChaoxingLessonRow>>>() {}.type,
            endpoint = "课表接口",
        )
        return response.data.orEmpty()
    }

    /**
     * 解析超星统一响应壳（ret / msg / data），并区分「会话过期」与「接口被拒绝」。
     * 超星在会话失效时会返回带 `登录超时,请重新登录！` 的 JSON（无 ret 字段），
     * 必须单独识别，才能触发自动重登。
     */
    private fun <T> parseEnvelope(body: String, type: Type, endpoint: String): ChaoxingResponse<T> {
        if (body.isBlank()) {
            throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "$endpoint 返回为空（会话可能已过期）")
        }
        if (looksLikeSessionExpired(body)) throw sessionExpired()
        val response: ChaoxingResponse<T>? = try {
            gson.fromJson(body, type)
        } catch (e: Exception) {
            null
        }
        if (response == null) {
            throw ChaoxingException(
                ChaoxingErrorKind.INVALID_RESPONSE,
                "$endpoint 返回内容无法解析，教务系统可能已改版",
            )
        }
        if (response.ret != 0) {
            val message = ChaoxingParsing.text(response.msg)
            if (message.contains("登录") || message.contains("会话")) throw sessionExpired()
            throw ChaoxingException(
                ChaoxingErrorKind.API_REJECTED,
                message.ifBlank { "$endpoint 拒绝了请求" },
            )
        }
        return response
    }

    private fun looksLikeSessionExpired(body: String): Boolean =
        ChaoxingParsing.isSessionExpiredBody(body)

    private fun sessionExpired(): ChaoxingException =
        ChaoxingException(ChaoxingErrorKind.SESSION_EXPIRED, "教务会话已过期，正在尝试重新登录")

    /** 从登录失败页面提取服务端提示（如「密码错误，请重试」）。 */
    private fun extractLoginError(html: String): String? {
        val match = Regex("""(密码错误[^<"]{0,40}|用户名或密码[^<"]{0,40}|账号[^<"]{0,30}冻结)""")
            .find(html) ?: return null
        return ChaoxingParsing.text(match.value)
    }

    private fun get(pathOrUrl: String): String = execute(
        Request.Builder()
            .url(pathOrUrl.toAbsoluteUrl())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", SchoolConfig.CHAOXING_BASE_URL + "/admin")
            .get()
            .build(),
    ).body

    /** JSON 接口必须带 X-Requested-With，否则超星返回 HTTP 500（实测）。 */
    private fun getJson(pathOrUrl: String): String = execute(
        Request.Builder()
            .url(pathOrUrl.toAbsoluteUrl())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", SchoolConfig.CHAOXING_BASE_URL + "/admin")
            .get()
            .build(),
    ).body

    private fun postForm(path: String, form: Map<String, String>, ajax: Boolean = false): Page = execute(
        Request.Builder()
            .url(path.toAbsoluteUrl())
            .header("User-Agent", USER_AGENT)
            .header("Accept", if (ajax) "application/json" else "text/html,application/xhtml+xml")
            .header("Referer", SchoolConfig.CHAOXING_BASE_URL + "/admin")
            .header("Origin", SchoolConfig.CHAOXING_BASE_URL)
            .apply { if (ajax) header("X-Requested-With", "XMLHttpRequest") }
            .post(
                form.entries.joinToString("&") { (key, value) ->
                    "${java.net.URLEncoder.encode(key, "UTF-8")}=${java.net.URLEncoder.encode(value, "UTF-8")}"
                }.toRequestBody(FORM_MEDIA_TYPE),
            )
            .build(),
    )

    private fun execute(request: Request): Page {
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw ChaoxingException(ChaoxingErrorKind.NETWORK, "教务网站连接失败或请求超时", e)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                if (looksLikeSessionExpired(body)) throw sessionExpired()
                Log.w(TAG, "HTTP ${it.code} for ${it.request.url.encodedPath} (body suppressed)")
                throw ChaoxingException(
                    ChaoxingErrorKind.NETWORK,
                    "教务接口返回 HTTP ${it.code}，可能系统已升级需要重新适配",
                )
            }
            return Page(body, it.request.url.toString())
        }
    }

    private fun String.toAbsoluteUrl(): String =
        if (startsWith("http://") || startsWith("https://")) this
        else SchoolConfig.CHAOXING_BASE_URL + this

    private fun encryptPassword(password: String, publicKeyBase64: String): String = try {
        val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        Base64.encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "encryptPassword failed", e)
        throw ChaoxingException(ChaoxingErrorKind.LOGIN_FAILED, "密码加密失败，请稍后重试", e)
    }

    private companion object {
        const val TAG = "ChaoxingClient"

        /** 成绩查询页（含 queryFields 与学期筛选项）。 */
        const val GRADES_PAGE_PATH = "/admin/xsd/xsdcjcx/qbcjcx"

        /** 成绩列表接口（fxbz=0 为主修，gridtype=jqgrid）。 */
        const val GRADES_API_PATH = "/admin/xsd/xsdcjcx/xsdQueryXscjList?fxbz=0&gridtype=jqgrid"

        /** 评教设置（与官网一致，先读一次）。 */
        const val EVALUATION_SETTINGS_PATH = "/admin/xsd/pj/xspj/getPjxtsz"

        /** 防御性上限：正常一个学生不会超过 100 页成绩。 */
        const val MAX_GRADE_PAGES = 100
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
    }
}