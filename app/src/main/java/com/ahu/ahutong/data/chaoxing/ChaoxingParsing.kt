package com.ahu.ahutong.data.chaoxing

import org.jsoup.Jsoup

/**
 * 超星教务响应解析（纯函数，便于单元测试）。
 *
 * 协议与边界处理对齐 `chaoxing-schedule` 抓取脚本：
 * - 课程名 / 教师 / 教室字段带 HTML 链接，统一去标签；
 * - 周次支持 `1-5,7-11,13-18` 与 `1-20(单)` / `1-20(双)` 两种写法；
 * - 节次以作息接口的 `jc`（小节）、`djs`（大节）、`kssj` / `jssj`（起止时间）为准，
 *   不按名称猜测；
 * - 解析异常一律抛错，不返回空课表掩盖问题。
 */
object ChaoxingParsing {

    private val weekPartRegex = Regex("""^(\d+)(?:[-~至](\d+))?(?:[（(]?([单双])[）)]?)?$""")
    private val timeRegex = Regex("""^(\d{1,2}):(\d{2})$""")
    private val publicKeyRegex = Regex("""\bvar\s+publicKey\s*=\s*["']([A-Za-z\d+/=]+)["']""")

    /** 去掉 HTML 标签与实体，返回纯文本。 */
    fun text(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        if (!value.contains('<') && !value.contains('&')) return value
        return Jsoup.parse(value).text().trim()
    }

    /** 解析周次，返回升序周次列表。 */
    fun parseWeeks(raw: String?): List<Int> {
        val source = text(raw)
            .replace("周", "")
            .replace(Regex("""\s+"""), "")
            .replace(Regex("""[，、]"""), ",")
        if (source.isEmpty()) {
            throw ChaoxingException(ChaoxingErrorKind.INVALID_WEEKS, "排课记录缺少周次")
        }
        val weeks = sortedSetOf<Int>()
        for (part in source.split(",")) {
            if (part.isEmpty()) continue
            val match = weekPartRegex.matchEntire(part)
                ?: throw ChaoxingException(
                    ChaoxingErrorKind.INVALID_WEEKS,
                    "遇到未支持的周次格式：$part",
                )
            val start = match.groupValues[1].toIntOrNull() ?: continue
            val endText = match.groupValues[2]
            val end = if (endText.isBlank()) start else endText.toIntOrNull() ?: start
            val parity = match.groupValues[3]
            if (start < 1 || end > 60 || start > end) {
                throw ChaoxingException(ChaoxingErrorKind.INVALID_WEEKS, "周次范围不合法：$part")
            }
            for (week in start..end) {
                val matched = when (parity) {
                    "单" -> week % 2 == 1
                    "双" -> week % 2 == 0
                    else -> true
                }
                if (matched) weeks.add(week)
            }
        }
        if (weeks.isEmpty()) {
            throw ChaoxingException(ChaoxingErrorKind.INVALID_WEEKS, "周次解析结果为空")
        }
        return weeks.toList()
    }

    /** 解析作息节次；`jc` 为小节序号，`djs` 为大节分组。 */
    fun parsePeriods(config: ChaoxingTimeConfig?): List<ChaoxingPeriod> {
        val rows = config?.periodRows
        if (rows.isNullOrEmpty()) {
            throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "作息接口未返回节次配置")
        }
        val periods = rows.mapNotNull { row ->
            val index = row.period?.trim()?.toIntOrNull() ?: return@mapNotNull null
            if (index < 1) return@mapNotNull null
            val block = row.block?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: index
            val alias = text(row.alias).ifBlank { index.toString() }
            ChaoxingPeriod(
                index = index,
                block = block,
                alias = alias,
                start = normalizeTime(row.start, index),
                end = normalizeTime(row.end, index),
            )
        }.sortedBy { it.index }
        if (periods.isEmpty()) {
            throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "作息节次解析结果为空")
        }
        if (periods.map { it.index }.distinct().size != periods.size) {
            throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "作息节次序号重复")
        }
        return periods
    }

    /** 解析学生课表页的隐藏字段（xhid / xnxq / xqdm 等）。 */
    fun parseHiddenInputs(html: String): Map<String, String> {
        if (html.isBlank()) return emptyMap()
        val document = Jsoup.parse(html)
        return document.select("input[id]").mapNotNull { element ->
            val id = element.id().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to element.attr("value")
        }.toMap()
    }

    /** 登录页里的 RSA 公钥（SPKI Base64）。 */
    fun extractPublicKey(html: String?): String? =
        html?.let { publicKeyRegex.find(it)?.groupValues?.getOrNull(1)?.trim() }

    /** 是否仍是登录页（会话失效）。 */
    fun containsLoginForm(html: String?): Boolean =
        html != null && Regex("""<form\b[^>]*id=["']loginform["']""", RegexOption.IGNORE_CASE)
            .containsMatchIn(html)

    /**
     * 教务接口返回「会话失效」内容。
     *
     * 超星在会话过期时会返回带 `登录超时,请重新登录！` 的 JSON（没有 ret 字段），
     * 也可能是重定向后的登录页 HTML；两种情况都要识别成会话失效，才能触发自动重登。
     */
    fun isSessionExpiredBody(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        if (containsLoginForm(body)) return true
        return body.contains("登录超时") ||
            body.contains("请重新登录") ||
            body.contains("会话已退出") ||
            body.contains("已在另一端登录")
    }

    /** 校历第 1 周周一，取 minrq 的 yyyy-MM-dd。 */
    fun semesterStartDate(config: ChaoxingTimeConfig?): String? =
        config?.weekRows.orEmpty()
            .firstOrNull { it.week?.trim()?.toIntOrNull() == 1 }
            ?.startDate
            ?.trim()
            ?.take(10)
            ?.takeIf { it.length == 10 }

    /** 服务器返回的当前周次。 */
    fun currentWeek(config: ChaoxingTimeConfig?): Int? =
        config?.currentWeek?.trim()?.toIntOrNull()?.takeIf { it in 1..60 }

    /** 把排课明细规范化成可渲染的上课安排。 */
    fun normalize(
        rows: List<ChaoxingLessonRow>,
        periods: List<ChaoxingPeriod>,
        semester: String,
        campusId: String,
        campusName: String,
        semesterStartDate: String? = null,
        currentWeek: Int? = null,
    ): ChaoxingSchedule {
        val periodByIndex = periods.associateBy { it.index }
        val weekCache = HashMap<String, List<Int>>()
        val groups = LinkedHashMap<String, MutableList<ChaoxingLessonRow>>()

        for (row in rows) {
            if (row.period <= 0) {
                throw ChaoxingException(ChaoxingErrorKind.UNKNOWN_PERIOD, "排课记录缺少节次")
            }
            val period = periodByIndex[row.period]
                ?: throw ChaoxingException(
                    ChaoxingErrorKind.UNKNOWN_PERIOD,
                    "排课记录包含未知节次：${row.period}",
                )
            if (row.dayOfWeek !in 1..7) {
                throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "排课记录的星期无效")
            }
            val rowSemester = row.semester?.trim().orEmpty()
            if (rowSemester.isNotEmpty() && rowSemester != semester) {
                throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "课表数据与请求学期不一致")
            }
            val courseName = text(row.courseName)
            if (courseName.isEmpty()) {
                throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "排课记录缺少课程名称")
            }
            val weekKey = row.weekText?.takeIf { it.isNotBlank() } ?: row.weekRange.orEmpty()
            val weeks = weekCache.getOrPut(weekKey) { parseWeeks(weekKey) }
            val groupKey = listOf(
                text(row.courseCode),
                courseName,
                text(row.teacher),
                text(row.building),
                text(row.classroom),
                text(row.campus),
                row.dayOfWeek.toString(),
                weeks.joinToString(","),
                period.block.toString(),
            ).joinToString("|")
            groups.getOrPut(groupKey) { mutableListOf() }.add(row)
        }

        val meetings = groups.values.map { group ->
            val first = group.first()
            val periodIndexes = group.map { it.period }.distinct().sorted()
            val weekKey = first.weekText?.takeIf { it.isNotBlank() } ?: first.weekRange.orEmpty()
            val weeks = weekCache.getOrPut(weekKey) { parseWeeks(weekKey) }
            ChaoxingMeeting(
                courseCode = text(first.courseCode),
                courseName = text(first.courseName),
                teacher = text(first.teacher),
                location = buildLocation(first),
                campus = text(first.campus),
                dayOfWeek = first.dayOfWeek,
                weeks = weeks,
                startPeriod = periodIndexes.first(),
                endPeriod = periodIndexes.last(),
            )
        }.sortedWith(
            compareBy<ChaoxingMeeting> { it.dayOfWeek }
                .thenBy { it.startPeriod }
                .thenBy { it.weeks.firstOrNull() ?: Int.MAX_VALUE },
        )

        val resolvedCampus = campusName.ifBlank {
            meetings.firstOrNull { it.campus.isNotBlank() }?.campus.orEmpty()
        }
        return ChaoxingSchedule(
            semester = semester,
            campusId = campusId,
            campusName = resolvedCampus,
            semesterStartDate = semesterStartDate,
            currentWeek = currentWeek,
            periods = periods,
            meetings = meetings,
        )
    }

    private fun buildLocation(row: ChaoxingLessonRow): String {
        val building = text(row.building)
        val classroom = text(row.classroom)
        return when {
            building.isNotEmpty() && classroom.isNotEmpty() -> "$building $classroom"
            classroom.isNotEmpty() -> classroom
            building.isNotEmpty() -> building
            else -> "未知"
        }
    }

    // ------------------------------------------------------------------ 成绩查询

    private val semesterRegex = Regex("""^\d{4}-\d{4}-[12]$""")

    /** 成绩查询页的筛选项元数据（queryFields / 学期列表 / 课程性质等中文名称）。 */
    data class GradePageMeta(
        val queryFields: String,
        val availableSemesters: List<String>,
        val courseNature: Map<String, String>,
        val courseType: Map<String, String>,
        val courseCategory: Map<String, String>,
    )

    /** 解析成绩查询页（/admin/xsd/xsdcjcx/qbcjcx）。 */
    fun parseGradePage(html: String): GradePageMeta {
        // 会话过期时会被重定向到登录页：必须识别成 SESSION_EXPIRED，
        // 才能触发仓库层的「用本地学号/密码自动重登」流程。
        if (html.isBlank() || isSessionExpiredBody(html)) {
            throw ChaoxingException(
                ChaoxingErrorKind.SESSION_EXPIRED,
                "成绩查询页登录已过期，正在尝试重新登录",
            )
        }
        if (!html.contains("/admin/xsd/xsdcjcx/xsdQueryXscjList")) {
            throw ChaoxingException(
                ChaoxingErrorKind.SCHEMA_CHANGED,
                "未进入成绩查询页，页面结构可能已变化",
            )
        }
        val document = Jsoup.parse(html)
        val fields = document.selectFirst("input#queryxsdcjcxGridIdFields")
            ?.attr("value")
            ?.trim()
            .orEmpty()
        if (fields.isEmpty()) {
            throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "成绩页缺少查询字段 queryFields")
        }
        fun options(id: String): Map<String, String> = document.select("select#$id option")
            .mapNotNull { option ->
                val value = option.attr("value").trim()
                if (value.isEmpty()) null else value to this.text(option.text())
            }
            .toMap()
        val semesters = options("startXnxq").keys.filter { semesterRegex.matches(it) }
        if (semesters.isEmpty()) {
            throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "成绩页缺少学期筛选项")
        }
        return GradePageMeta(
            queryFields = fields,
            availableSemesters = semesters,
            courseNature = options("kcxz"),
            courseType = options("kclx"),
            courseCategory = options("kclb"),
        )
    }

    /** 把成绩原始记录归一化：保留成绩原文，不把等级制折算成数字，不伪造绩点。 */
    fun normalizeGrades(
        rows: List<ChaoxingGradeRow>,
        meta: GradePageMeta,
    ): List<ChaoxingGradeRecord> = rows.map { row ->
        val id = row.id?.trim().orEmpty()
        if (id.isEmpty()) {
            throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "成绩记录缺少唯一编号")
        }
        val semester = row.semester?.trim().orEmpty()
        if (!semesterRegex.matches(semester)) {
            throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "成绩记录学期无效：$semester")
        }
        val code = text(row.courseCode)
        var name = text(row.courseName)
        if (code.isNotEmpty() && name.startsWith("[$code]")) {
            name = name.removePrefix("[$code]").trim()
        }
        if (name.isEmpty()) {
            throw ChaoxingException(ChaoxingErrorKind.INVALID_RESPONSE, "成绩记录缺少课程名称")
        }
        val natureCode = row.courseNatureCode?.trim()
        ChaoxingGradeRecord(
            recordId = id,
            semester = semester,
            courseCode = code,
            courseName = name,
            grade = text(row.score).ifBlank { null },
            credits = parseDecimal(row.credits),
            earnedCredits = parseDecimal(row.earnedCredits),
            gradePoint = parseDecimal(row.gradePoint),
            courseNature = meta.courseNature[natureCode]
                ?: meta.courseType[row.courseTypeCode?.trim()]
                ?: meta.courseCategory[row.courseCategoryCode?.trim()],
            isMakeupExam = when (row.makeup?.trim()) {
                "1" -> true
                "0" -> false
                else -> null
            },
            detail = listOf(text(row.components), text(row.specialGrade))
                .filter { it.isNotBlank() }
                .joinToString("；")
                .ifBlank { null },
        )
    }.sortedWith(compareBy({ it.semester }, { it.courseName }))

    /** 数字文本解析；「优秀」「合格」等非数字一律返回 null，不擅自折算。 */
    private fun parseDecimal(raw: String?): Double? =
        text(raw).takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    private fun normalizeTime(raw: String?, index: Int): String {
        val value = raw?.trim().orEmpty()
        val match = timeRegex.matchEntire(value)
            ?: throw ChaoxingException(
                ChaoxingErrorKind.SCHEMA_CHANGED,
                "第 $index 节的作息时间格式异常：$value",
            )
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour > 23 || minute > 59) {
            throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "第 $index 节的作息时间不合法")
        }
        return "%02d:%02d".format(hour, minute)
    }
}