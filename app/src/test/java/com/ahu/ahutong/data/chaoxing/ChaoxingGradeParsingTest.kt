package com.ahu.ahutong.data.chaoxing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 超星成绩查询解析的离线单元测试（全部虚构数据）。
 */
class ChaoxingGradeParsingTest {

    private fun samplePage(): String = """
        <html><body>
        <input type="hidden" id="queryxsdcjcxGridIdFields" value="&quot;kcmc&quot;:&quot;课程名称&quot;,&quot;kcxz&quot;:&quot;课程性质&quot;">
        <select id="startXnxq">
            <option value="001">入学以来</option>
            <option value="2025-2026-1">2025-2026-1</option>
            <option value="2025-2026-2">2025-2026-2</option>
        </select>
        <select id="endXnxq">
            <option value="001">至今</option>
            <option value="2025-2026-1">2025-2026-1</option>
        </select>
        <select id="kcxz"><option value="13">专业拓展课</option></select>
        <select id="kclx"><option value="16">专业（技能）课</option></select>
        <select id="kclb"><option value="1">B类（理论+实践）课</option></select>
        <script>var api = '/admin/xsd/xsdcjcx/xsdQueryXscjList?fxbz=0&gridtype=jqgrid';</script>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses grade page metadata`() {
        val meta = ChaoxingParsing.parseGradePage(samplePage())
        assertTrue(meta.queryFields.contains("kcmc"))
        assertEquals(listOf("2025-2026-1", "2025-2026-2"), meta.availableSemesters)
        assertEquals("专业拓展课", meta.courseNature["13"])
        assertEquals("专业（技能）课", meta.courseType["16"])
        assertEquals("B类（理论+实践）课", meta.courseCategory["1"])
    }

    @Test
    fun `rejects pages without query fields or semester options`() {
        assertFailsWith<ChaoxingException> {
            ChaoxingParsing.parseGradePage("<html>没有成绩接口</html>")
        }
        assertFailsWith<ChaoxingException> {
            ChaoxingParsing.parseGradePage(
                """<input id="queryxsdcjcxGridIdFields" value="x">
                   <script>/admin/xsd/xsdcjcx/xsdQueryXscjList</script>""",
            )
        }
    }

    @Test
    fun `normalizes grades keeping original text and never faking grade points`() {
        val meta = ChaoxingParsing.parseGradePage(samplePage())
        val records = ChaoxingParsing.normalizeGrades(
            listOf(
                ChaoxingGradeRow(
                    id = "1",
                    courseName = "[111102003] 社交礼仪与人际沟通",
                    courseCode = "111102003",
                    semester = "2025-2026-1",
                    score = "92.5",
                    credits = "2",
                    earnedCredits = "2",
                    gradePoint = null,
                    creditGradePoint = "8.6",
                    courseNatureCode = "13",
                    makeup = "0",
                ),
                ChaoxingGradeRow(
                    id = "2",
                    courseName = "新时代民政工作",
                    courseCode = "101006039",
                    semester = "2025-2026-2",
                    score = "优秀",
                    credits = "1",
                    earnedCredits = "1",
                    gradePoint = "4.0",
                    makeup = "1",
                ),
            ),
            meta,
        )

        assertEquals(2, records.size)
        val first = records.first { it.recordId == "1" }
        assertEquals("社交礼仪与人际沟通", first.courseName)
        assertEquals("92.5", first.grade)
        assertNull(first.gradePoint) // 只有 xfjd 时不能当成单科绩点
        assertEquals(false, first.isMakeupExam)
        assertEquals("专业拓展课", first.courseNature)

        val second = records.first { it.recordId == "2" }
        assertEquals("优秀", second.grade)
        assertEquals(4.0, second.gradePoint)
        assertEquals(true, second.isMakeupExam)
    }

    @Test
    fun `rejects invalid rows`() {
        val meta = ChaoxingParsing.parseGradePage(samplePage())
        assertFailsWith<ChaoxingException> {
            ChaoxingParsing.normalizeGrades(
                listOf(ChaoxingGradeRow(id = "1", courseName = "课程", semester = "2025-2026-3")),
                meta,
            )
        }
        assertFailsWith<ChaoxingException> {
            ChaoxingParsing.normalizeGrades(
                listOf(ChaoxingGradeRow(id = "", courseName = "课程", semester = "2025-2026-1")),
                meta,
            )
        }
    }

    @Test
    fun `maps records to grade model by semester`() {
        val records = listOf(
            ChaoxingGradeRecord(
                recordId = "1", semester = "2025-2026-1", courseCode = "A1", courseName = "课程一",
                grade = "92.5", credits = 2.0, earnedCredits = 2.0, gradePoint = 4.0,
                courseNature = "专业拓展课", isMakeupExam = false, detail = null,
            ),
            ChaoxingGradeRecord(
                recordId = "2", semester = "2025-2026-1", courseCode = "A2", courseName = "课程二",
                grade = "优秀", credits = 1.0, earnedCredits = 1.0, gradePoint = null,
                courseNature = null, isMakeupExam = null, detail = null,
            ),
            ChaoxingGradeRecord(
                recordId = "3", semester = "2025-2026-2", courseCode = "B1", courseName = "课程三",
                grade = "60", credits = 2.0, earnedCredits = 2.0, gradePoint = 1.0,
                courseNature = null, isMakeupExam = true, detail = null,
            ),
        )
        val grade = ChaoxingGradeRepository.toGrade(
            ChaoxingGrades(availableSemesters = listOf("2025-2026-1", "2025-2026-2"), records = records),
        )

        val terms = grade.termGradeList
        assertEquals(2, terms.size)
        assertEquals("2025-2026", terms[0].schoolYear)
        assertEquals("1", terms[0].term)
        assertEquals("3", terms[0].termTotalCredit.let { if (it.endsWith(".0")) it.dropLast(2) else it })
        // 该学期包含无绩点课程 → 不计算平均绩点
        assertNull(terms[0].termGradePointAverage)
        assertEquals("2025-2026", terms[1].schoolYear)
        assertEquals("2", terms[1].term)
        // 第二学期有绩点 → 加权平均 1.0
        assertEquals("1.00", terms[1].termGradePointAverage)
        // 补考记录标注
        assertTrue(terms[1].gradeList.first().grade.contains("（补考）"))
    }
}