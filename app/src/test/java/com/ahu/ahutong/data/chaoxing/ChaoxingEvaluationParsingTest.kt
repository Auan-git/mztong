package com.ahu.ahutong.data.chaoxing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 评教列表/问卷解析（离线样本，不访问教务网站）。 */
class ChaoxingEvaluationParsingTest {

    private val task = ChaoxingEvaluationTask(
        taskKey = "T1/C1/R1",
        templateId = "T1",
        teachingClassId = "C1",
        teacherId = "R1",
        courseName = "社会工作概论",
        teacherName = "张老师",
        templateName = "学生评教",
        semester = "2026-2027-1",
        courseCode = "SOC101",
        statusCode = "0",
        status = "未评",
        score = null,
    )

    private val questionnaireHtml = """
        <html><body>
        <input type="hidden" id="zbmbxxid" name="zbmbxxid" value="T1">
        <input type="hidden" id="jxbid" name="jxbid" value="C1">
        <input type="hidden" id="tid" name="tid" value="R1">
        <input type="hidden" id="t" name="t" value="dynamic-token">
        <h3>教学态度</h3>
        <input class="anwser" data-yjzb="1" data-ejzb="1" data-sfbt="1" value="">
        <pre class="ejzbPre">老师授课是否认真负责？</pre>
        <input type="radio" data-yjzb="1" data-ejzb="1" name="a1" value="11" title="非常满意">
        <input type="radio" data-yjzb="1" data-ejzb="1" name="a1" value="12" title="满意">
        <h3>意见建议</h3>
        <input class="anwser" data-yjzb="1" data-ejzb="2" data-sfbt="0" value="">
        <pre class="ejzbPre">对课程的其他建议</pre>
        <textarea data-yjzb="1" data-ejzb="2" data-min="5" data-max="200"></textarea>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses questionnaire questions options and text limits`() {
        val result = ChaoxingEvaluationParsing.parseQuestionnaire(questionnaireHtml, task)
        assertEquals("T1/C1/R1", result.taskKey)
        assertEquals(2, result.questions.size)
        assertEquals(1, result.requiredCount)

        val choice = result.questions[0]
        assertEquals("教学态度", choice.sectionTitle)
        assertEquals("老师授课是否认真负责？", choice.prompt)
        assertEquals("single-choice", choice.type)
        assertTrue(choice.required)
        assertEquals(listOf("11", "12"), choice.options.map { it.optionId })
        assertEquals(listOf("非常满意", "满意"), choice.options.map { it.label })

        val text = result.questions[1]
        assertEquals("text", text.type)
        assertEquals(false, text.required)
        assertEquals(5, text.minLength)
        assertEquals(200, text.maxLength)
    }

    @Test
    fun `rejects questionnaire whose task ids mismatch`() {
        val wrongTask = task.copy(teacherId = "R2", taskKey = "T1/C1/R2")
        assertFailsWith<ChaoxingException> {
            ChaoxingEvaluationParsing.parseQuestionnaire(questionnaireHtml, wrongTask)
        }
    }

    @Test
    fun `accepts decimal rendering of whole number text limits`() {
        val html = questionnaireHtml.replace("data-min=\"5\"", "data-min=\"0.0\"")
            .replace("data-max=\"200\"", "data-max=\"10000.0\"")
        val question = ChaoxingEvaluationParsing.parseQuestionnaire(html, task).questions.last()
        assertEquals(0, question.minLength)
        assertEquals(10000, question.maxLength)
    }

    @Test
    fun `rejects invalid text bounds instead of silently dropping them`() {
        for (limit in listOf("-1", "1.5", "NaN", "2147483648", "201")) {
            val html = questionnaireHtml.replace("data-min=\"5\"", "data-min=\"$limit\"")
            assertEquals(
                ChaoxingErrorKind.SCHEMA_CHANGED,
                assertFailsWith<ChaoxingException> {
                    ChaoxingEvaluationParsing.parseQuestionnaire(html, task)
                }.kind,
            )
        }
    }

    @Test
    fun `detects expired session before parsing questionnaire`() {
        assertEquals(
            ChaoxingErrorKind.SESSION_EXPIRED,
            assertFailsWith<ChaoxingException> {
                ChaoxingEvaluationParsing.parseQuestionnaire("登录超时,请重新登录", task)
            }.kind,
        )
    }

    @Test
    fun `rejects duplicate question keys`() {
        val html = questionnaireHtml.replace("data-ejzb=\"2\"", "data-ejzb=\"1\"")
        assertFailsWith<ChaoxingException> {
            ChaoxingEvaluationParsing.parseQuestionnaire(html, task)
        }
    }

    @Test
    fun `normalizes task row and validates status`() {
        val row = ChaoxingEvaluationRow(
            id = "100",
            templateId = "T1",
            teachingClassId = "C1",
            teacherId = "R1",
            statusCode = "1",
            courseName = "社会工作概论",
            teacherName = "张老师",
            semester = "2026-2027-1",
            courseCode = "SOC101",
            templateName = "学生评教",
        )
        val normalized = ChaoxingEvaluationParsing.normalizeTask(row)
        assertEquals("T1/C1/R1", normalized.taskKey)
        assertEquals("已保存", normalized.status)
        assertEquals("社会工作概论", normalized.courseName)
        // 官网不同教师可能复用同一个列表 id，不能因此丢掉一份评教。
        assertNotEquals(
            normalized.taskKey,
            ChaoxingEvaluationParsing.normalizeTask(row.copy(teacherId = "R2")).taskKey,
        )

        assertFailsWith<ChaoxingException> {
            ChaoxingEvaluationParsing.normalizeTask(row.copy(statusCode = "9"))
        }
    }
}
