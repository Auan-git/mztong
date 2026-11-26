package com.ahu.ahutong.data.chaoxing

import com.google.gson.annotations.SerializedName
import org.jsoup.Jsoup

/** 评教任务（zbmbxxid/jxbid/tid 三元组唯一标识一份评价）。 */
data class ChaoxingEvaluationTask(
    val taskKey: String,
    val templateId: String,
    val teachingClassId: String,
    val teacherId: String,
    val courseName: String,
    val teacherName: String,
    val templateName: String,
    val semester: String,
    val courseCode: String,
    val statusCode: String,
    val status: String,
    val score: Double?,
)

data class ChaoxingEvaluations(val tasks: List<ChaoxingEvaluationTask>) {
    val notEvaluatedCount: Int get() = tasks.count { it.statusCode == "0" }
    val savedCount: Int get() = tasks.count { it.statusCode == "1" }
    val submittedCount: Int get() = tasks.count { it.statusCode == "2" }
}

data class ChaoxingEvaluationOption(
    val optionId: String,
    val label: String,
)

data class ChaoxingEvaluationQuestion(
    val questionKey: String,
    val sectionTitle: String,
    val prompt: String,
    val type: String,
    val required: Boolean,
    val currentAnswer: String,
    val options: List<ChaoxingEvaluationOption>,
    val minLength: Int?,
    val maxLength: Int?,
)

data class ChaoxingEvaluationQuestionnaire(
    val taskKey: String,
    val courseName: String,
    val teacherName: String,
    val statusCode: String,
    val status: String,
    val readOnly: Boolean,
    val questions: List<ChaoxingEvaluationQuestion>,
) {
    val requiredCount: Int get() = questions.count { it.required }
}

/** 评教列表 JSON（/admin/xsd/pj/xspj/customAjaxList?gridtype=jqgrid）。 */
data class ChaoxingEvaluationListResponse(
    val ret: Int? = null,
    val msg: String? = null,
    val page: Int = 0,
    val rows: Int = 0,
    val total: Int = 0,
    @SerializedName("totalPages") val totalPages: Int = 0,
    val results: List<ChaoxingEvaluationRow>? = null,
)

data class ChaoxingEvaluationRow(
    val id: String? = null,
    @SerializedName("zbmbxxid") val templateId: String? = null,
    @SerializedName("jxbid") val teachingClassId: String? = null,
    @SerializedName("tid") val teacherId: String? = null,
    @SerializedName("pjzt") val statusCode: String? = null,
    @SerializedName("kcmc") val courseName: String? = null,
    @SerializedName("jsname") val teacherName: String? = null,
    @SerializedName("xnxq") val semester: String? = null,
    @SerializedName("kcbh") val courseCode: String? = null,
    @SerializedName("zbmbmc") val templateName: String? = null,
    @SerializedName("xsType") val courseType: String? = null,
    @SerializedName("pjzf") val score: String? = null,
)

object ChaoxingEvaluationParsing {

    private const val STATUS_NOT_EVALUATED = "未评"
    private const val STATUS_SAVED = "已保存"
    private const val STATUS_SUBMITTED = "已提交"

    private val IDENTIFIER = Regex("^[A-Za-z0-9_-]+$")
    private val INPUT_TAG = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val H3_TAG = Regex("<h3\\b[^>]*>([\\s\\S]*?)</h3>", RegexOption.IGNORE_CASE)
    private val PRE_TAG = Regex("<pre\\b([^>]*)>([\\s\\S]*?)</pre>", RegexOption.IGNORE_CASE)
    private val TEXTAREA_TAG = Regex("<textarea\\b([^>]*)>([\\s\\S]*?)</textarea>", RegexOption.IGNORE_CASE)

    fun normalizeTask(row: ChaoxingEvaluationRow): ChaoxingEvaluationTask {
        val templateId = identifier(row.templateId, "zbmbxxid")
        val teachingClassId = identifier(row.teachingClassId, "jxbid")
        val teacherId = identifier(row.teacherId, "tid")
        val statusCode = row.statusCode?.trim().orEmpty()
        val status = when (statusCode) {
            "0" -> STATUS_NOT_EVALUATED
            "1" -> STATUS_SAVED
            "2" -> STATUS_SUBMITTED
            else -> throw ChaoxingException(
                ChaoxingErrorKind.SCHEMA_CHANGED,
                "评教任务出现未知状态：$statusCode",
            )
        }
        val courseName = text(row.courseName.orEmpty())
        val teacherName = text(row.teacherName.orEmpty())
        if (courseName.isBlank() || teacherName.isBlank()) {
            throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "评教任务缺少课程或教师名称")
        }
        return ChaoxingEvaluationTask(
            taskKey = "$templateId/$teachingClassId/$teacherId",
            templateId = templateId,
            teachingClassId = teachingClassId,
            teacherId = teacherId,
            courseName = courseName,
            teacherName = teacherName,
            templateName = text(row.templateName.orEmpty()),
            semester = text(row.semester.orEmpty()),
            courseCode = text(row.courseCode.orEmpty()),
            statusCode = statusCode,
            status = status,
            score = row.score?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull(),
        )
    }

    fun parseQuestionnaire(html: String, task: ChaoxingEvaluationTask): ChaoxingEvaluationQuestionnaire {
        if (ChaoxingParsing.containsLoginForm(html) || html.contains("登录超时,请重新登录")) {
            throw ChaoxingException(ChaoxingErrorKind.SESSION_EXPIRED, "评教问卷登录已过期，请重新登录")
        }
        val hidden = hiddenInputs(html)
        if (hidden["zbmbxxid"] != task.templateId ||
            hidden["jxbid"] != task.teachingClassId ||
            hidden["tid"] != task.teacherId
        ) {
            throw ChaoxingException(
                ChaoxingErrorKind.SCHEMA_CHANGED,
                "评教问卷标识与所选任务不一致，登录可能已过期",
            )
        }

        val markers = INPUT_TAG.findAll(html).mapNotNull { match ->
            val attrs = inputAttrs(match.value)
            val classes = attrs["class"]?.split(Regex("\\s+")).orEmpty()
            if (classes.contains("anwser")) {
                InputMarker(start = match.range.first, end = match.range.last + 1, attrs = attrs)
            } else {
                null
            }
        }.toList()
        if (markers.isEmpty()) {
            throw ChaoxingException(
                ChaoxingErrorKind.SCHEMA_CHANGED,
                "没有找到可识别的评教题目；已提交详情或模板结构可能不同",
            )
        }

        val seen = mutableSetOf<String>()
        val questions = markers.mapIndexed { index, marker ->
            val sectionId = identifier(marker.attrs["data-yjzb"], "data-yjzb")
            val indicatorId = identifier(marker.attrs["data-ejzb"], "data-ejzb")
            val questionKey = "$sectionId/$indicatorId"
            if (!seen.add(questionKey)) {
                throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "评教问卷出现重复题目标识")
            }
            val segmentEnd = markers.getOrNull(index + 1)?.start ?: html.length
            val segment = html.substring(marker.end.coerceAtMost(segmentEnd), segmentEnd)
            val prefix = html.substring(0, marker.start)

            val sectionTitle = H3_TAG.findAll(prefix).lastOrNull()
                ?.groupValues?.getOrNull(1)
                ?.let(::text)
                .orEmpty()

            val promptTag = PRE_TAG.findAll(segment).firstOrNull { match ->
                tagAttrs("pre", "<pre${match.groupValues[1]}>")["class"]
                    ?.split(Regex("\\s+"))?.contains("ejzbPre") == true
            } ?: throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "评教题目缺少描述文本")
            val prompt = text(promptTag.groupValues[2])
            if (prompt.isBlank()) {
                throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "评教题目描述为空")
            }

            fun belongs(attrs: Map<String, String>): Boolean =
                attrs["data-yjzb"] == sectionId && attrs["data-ejzb"] == indicatorId

            val controls = INPUT_TAG.findAll(segment)
                .map { inputAttrs(it.value) }
                .filter(::belongs)
            val options = controls
                .filter { it["type"] == "radio" || it["type"] == "checkbox" }
                .map { control ->
                    val optionId = identifier(control["value"], "选项 value")
                    val label = text(control["title"].orEmpty())
                    if (label.isBlank()) {
                        throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "评教选项缺少文字")
                    }
                    ChaoxingEvaluationOption(optionId = optionId, label = label)
                }
                .toList()
            if (options.map { it.optionId }.distinct().size != options.size) {
                throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "评教选项标识重复")
            }

            val textareaAttrs = TEXTAREA_TAG.findAll(segment)
                .map { tagAttrs("textarea", "<textarea${it.groupValues[1]}>") }
                .firstOrNull(::belongs)

            val type = when {
                options.isNotEmpty() && controls.any { it["type"] == "checkbox" } -> "multiple-choice"
                options.isNotEmpty() -> "single-choice"
                textareaAttrs != null -> "text"
                else -> throw ChaoxingException(
                    ChaoxingErrorKind.SCHEMA_CHANGED,
                    "评教题目类型暂不支持（既无选项也无意见框）",
                )
            }
            val requiredFlag = marker.attrs["data-sfbt"]?.trim()
            if (requiredFlag != "0" && requiredFlag != "1") {
                throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "评教题目必填标记无效")
            }
            val minLength = textLength(textareaAttrs?.get("data-min"))
            val maxLength = textLength(textareaAttrs?.get("data-max"))
            if (minLength != null && maxLength != null && minLength > maxLength) {
                throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "评教意见字数范围无效")
            }
            ChaoxingEvaluationQuestion(
                questionKey = questionKey,
                sectionTitle = sectionTitle,
                prompt = prompt,
                type = type,
                required = requiredFlag == "1",
                currentAnswer = marker.attrs["value"].orEmpty(),
                options = options,
                minLength = minLength,
                maxLength = maxLength,
            )
        }

        return ChaoxingEvaluationQuestionnaire(
            taskKey = task.taskKey,
            courseName = task.courseName,
            teacherName = task.teacherName,
            statusCode = task.statusCode,
            status = task.status,
            readOnly = task.statusCode == "2",
            questions = questions,
        )
    }

    private fun textLength(raw: String?): Int? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // 官网会把整数限制渲染为 0.0、10000.0；拒绝小数、负数和溢出。
        return runCatching { value.toBigDecimal().intValueExact() }.getOrNull()
            ?.takeIf { it >= 0 }
            ?: throw ChaoxingException(ChaoxingErrorKind.SCHEMA_CHANGED, "评教意见字数限制无效")
    }

    private data class InputMarker(
        val start: Int,
        val end: Int,
        val attrs: Map<String, String>,
    )

    private fun identifier(value: String?, field: String): String {
        val trimmed = value?.trim().orEmpty()
        if (!IDENTIFIER.matches(trimmed)) {
            throw ChaoxingException(
                ChaoxingErrorKind.SCHEMA_CHANGED,
                "评教任务/问卷标识无效（$field=$trimmed）",
            )
        }
        return trimmed
    }

    /** 评教页面隐藏域同时兼容 id / name，避免页面结构差异导致误判。 */
    private fun hiddenInputs(html: String): Map<String, String> {
        return Jsoup.parse(html)
            .select("input[id], input[name]")
            .mapNotNull { element ->
                val key = element.id().ifBlank { element.attr("name") }.trim()
                if (key.isEmpty()) null else key to element.attr("value")
            }
            .toMap()
    }

    private fun inputAttrs(tag: String): Map<String, String> = tagAttrs("input", tag)

    private fun tagAttrs(tagName: String, rawTag: String): Map<String, String> {
        val element = Jsoup.parseBodyFragment(rawTag).selectFirst(tagName) ?: return emptyMap()
        return element.attributes().associate { it.key to it.value }
    }

    private fun text(fragment: String): String = Jsoup.parse(fragment).text().trim()
}
