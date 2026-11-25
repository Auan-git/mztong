package com.ahu.ahutong.data.chaoxing

import android.util.Log
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.Grade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 民政职业大学超星教务成绩仓库。
 *
 * - 数据来源：`/admin/xsd/xsdcjcx/qbcjcx` 成绩查询页 + `xsdQueryXscjList` 分页接口；
 * - 学期划分：把 `2025-2026-1` 拆成 schoolYear=`2025-2026`、term=`1`，与 App 成绩页一致；
 * - 绩点：仅当接口返回 `jd` 时使用；只用数字成绩、平均分或 `xfjd` 替代绩点，
 *   以避免与原项目「平均学分绩点」字段混淆（参见 chaoxing-schedule/README 成绩说明）。
 */
object ChaoxingGradeRepository {

    private const val TAG = "ChaoxingGradeRepository"

    @Volatile
    private var cachedSession: ChaoxingSession? = null

    private fun client(): ChaoxingClient = ChaoxingClient()

    /** 查询全部学期成绩并转换为 App 的 [Grade] 模型。 */
    suspend fun fetchGrade(): Result<Grade> = withContext(Dispatchers.IO) {
        try {
            val client = client()
            if (cachedSession == null && !client.hasSessionCookies()) {
                loginWithCachedCredential(client)
            }
            val grades = try {
                client.fetchGrades()
            } catch (e: ChaoxingException) {
                if (e.kind != ChaoxingErrorKind.SESSION_EXPIRED) throw e
                Log.i(TAG, "Session expired, re-login with cached credential")
                loginWithCachedCredential(client)
                client.fetchGrades()
            }
            Result.success(toGrade(grades))
        } catch (t: Throwable) {
            Log.w(TAG, "fetchGrade failed: ${t.message}")
            Result.failure(t)
        }
    }

    /** 成绩记录 → App 的 [Grade]（按学期分组，学期升序）。 */
    internal fun toGrade(grades: ChaoxingGrades): Grade {
        val grouped = grades.records
            .groupBy { it.semester }
            .toSortedMap()
        val termList = grouped.map { (semester, records) ->
            val schoolYear = semester.take(9)
            val term = semester.takeLast(1)
            val beans = records.map { record ->
                Grade.TermGradeListBean.GradeListBean().apply {
                    setCourse(record.courseName)
                    setCourseNum(record.courseCode)
                    setCredit(record.credits?.let(::formatDecimal).orEmpty())
                    // 成绩原文保留（可能是「优秀」「合格」），补考记录标注出来
                    val gradeText = buildString {
                        append(record.grade.orEmpty())
                        if (record.isMakeupExam == true) append("（补考）")
                    }
                    setGrade(gradeText.ifBlank { null })
                    setGradePoint(record.gradePoint?.let(::formatDecimal))
                    setCourseNature(record.courseNature.orEmpty())
                    setSemesterId(grouped.keys.indexOf(semester))
                    setGradeDetail(record.detail)
                }
            }
            val totalCredit = records.sumOf { it.credits ?: 0.0 }
            val gradePoints = records.mapNotNull { it.gradePoint }
            val averageGpa = if (gradePoints.size == records.size && totalCredit > 0) {
                val weighted = records.sumOf { (it.gradePoint ?: 0.0) * (it.credits ?: 0.0) }
                String.format(Locale.CHINA, "%.2f", weighted / totalCredit)
            } else {
                null
            }
            Grade.TermGradeListBean().apply {
                setSchoolYear(schoolYear)
                setTerm(term)
                setTermTotalCredit(formatDecimal(totalCredit))
                setTermGradePointAverage(averageGpa)
                setGradeList(beans)
            }
        }
        return Grade().apply {
            setTotalCredit(
                formatDecimal(termList.sumOf { it.termTotalCredit?.toDoubleOrNull() ?: 0.0 }),
            )
            setTotalGradePointAverage(null)
            setTotalGradePoint(null)
            setTermGradeList(termList)
        }
    }

    private suspend fun loginWithCachedCredential(client: ChaoxingClient): ChaoxingSession {
        val user = AHUCache.getCurrentUser()
            ?: throw ChaoxingException(ChaoxingErrorKind.LOGIN_FAILED, "请先登录教务系统")
        val username = user.xh?.takeIf { it.isNotBlank() }
            ?: throw ChaoxingException(ChaoxingErrorKind.LOGIN_FAILED, "本地账号信息缺失，请重新登录")
        val password = AHUCache.getWisdomPassword()?.takeIf { it.isNotBlank() }
            ?: throw ChaoxingException(ChaoxingErrorKind.LOGIN_FAILED, "本地密码已失效，请重新登录")
        val session = client.login(username, password)
        cachedSession = session
        return session
    }

    /** 数字格式化：整数不带小数点（2.0 → 2），小数最多保留两位。 */
    private fun formatDecimal(value: Double): String {
        val text = String.format(Locale.CHINA, "%.2f", value)
        return text.trimEnd('0').trimEnd('.')
    }
}