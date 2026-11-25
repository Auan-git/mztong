package com.ahu.ahutong.data.chaoxing

import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 超星教务解析的离线单元测试。
 *
 * 全部使用虚构数据，不包含任何真实学号、姓名或课表。
 */
class ChaoxingParsingTest {

    @Test
    fun `strips html links from course fields`() {
        val raw = """<a href="javascript:void(0);" onclick="openKckb('x')">高等数学</a>"""
        assertEquals("高等数学", ChaoxingParsing.text(raw))
        assertEquals("张老师", ChaoxingParsing.text("""<a href="#">张老师</a>"""))
        assertEquals("", ChaoxingParsing.text(null))
    }

    @Test
    fun `parses week ranges`() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18),
            ChaoxingParsing.parseWeeks("1-5,7-11,13-18"),
        )
        assertEquals(listOf(3), ChaoxingParsing.parseWeeks("3周"))
        assertEquals(
            listOf(1, 3, 5, 7, 9),
            ChaoxingParsing.parseWeeks("1-10(单)"),
        )
        assertEquals(
            listOf(2, 4, 6, 8, 10),
            ChaoxingParsing.parseWeeks("1-10（双）"),
        )
    }

    @Test
    fun `rejects invalid week text`() {
        assertFailsWith<ChaoxingException> { ChaoxingParsing.parseWeeks("周次待定") }
        assertFailsWith<ChaoxingException> { ChaoxingParsing.parseWeeks("9-3") }
        assertFailsWith<ChaoxingException> { ChaoxingParsing.parseWeeks("") }
    }

    @Test
    fun `parses period configuration and normalizes times`() {
        val json = """
            {"ret":0,"msg":"操作成功","data":{
              "jcsjszList":[
                {"kssj":"8:10","jssj":"8:55","jc":"1","jcbm":"一","djs":"1"},
                {"kssj":"9:05","jssj":"9:50","jc":"2","jcbm":"二","djs":"1"},
                {"kssj":"12:00","jssj":"12:45","jc":"5","jcbm":"中午1","djs":"3"}
              ],
              "zclist":[{"zc":"1","minrq":"2026-08-31 00:00:00","maxrq":"2026-09-06 00:00:00"}],
              "dqzc":"13"
            }}
        """.trimIndent()
        val type = com.google.gson.reflect.TypeToken.getParameterized(
            ChaoxingResponse::class.java,
            ChaoxingTimeConfig::class.java,
        ).type
        val response: ChaoxingResponse<ChaoxingTimeConfig> = Gson().fromJson(json, type)
        val config = assertNotNull(response.data)

        val periods = ChaoxingParsing.parsePeriods(config)
        assertEquals(3, periods.size)
        assertEquals("08:10", periods[0].start)
        assertEquals("09:50", periods[1].end)
        assertEquals(3, periods[2].block)
        assertEquals("2026-08-31", ChaoxingParsing.semesterStartDate(config))
        assertEquals(13, ChaoxingParsing.currentWeek(config))
    }

    @Test
    fun `merges rows of the same course into one meeting`() {
        val periods = listOf(
            ChaoxingPeriod(1, 1, "一", "08:10", "08:55"),
            ChaoxingPeriod(2, 1, "二", "09:05", "09:50"),
            ChaoxingPeriod(3, 2, "三", "10:10", "10:55"),
            ChaoxingPeriod(4, 2, "四", "11:05", "11:50"),
        )
        val rows = listOf(
            row(period = 1, day = 1, name = "高等数学", weeks = "1-5"),
            row(period = 2, day = 1, name = "高等数学", weeks = "1-5"),
            row(period = 3, day = 2, name = "大学英语", weeks = "1-3"),
        )
        val schedule = ChaoxingParsing.normalize(
            rows = rows,
            periods = periods,
            semester = "2026-2027-1",
            campusId = "campus-1",
            campusName = "大兴校区",
        )

        assertEquals(2, schedule.meetings.size)
        val math = schedule.meetings.first { it.courseName == "高等数学" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startPeriod)
        assertEquals(2, math.endPeriod)
        assertEquals(listOf(1, 2, 3, 4, 5), math.weeks)
        assertEquals("大兴校区", schedule.campusName)
        assertEquals(mapOf(1 to "08:10-08:55", 2 to "09:05-09:50", 3 to "10:10-10:55", 4 to "11:05-11:50"), schedule.toTimetable())
    }

    @Test
    fun `parses hidden input fields and login form detection`() {
        val html = """
            <html><body>
            <input id="xhid" value="student-token" />
            <input id="xnxq" value="2026-2027-1" />
            <input id="xqdm" value="campus-1" />
            </body></html>
        """.trimIndent()
        val inputs = ChaoxingParsing.parseHiddenInputs(html)
        assertEquals("student-token", inputs["xhid"])
        assertEquals("2026-2027-1", inputs["xnxq"])
        assertEquals("campus-1", inputs["xqdm"])
        assertFalse(ChaoxingParsing.containsLoginForm(html))
        assertTrue(ChaoxingParsing.containsLoginForm("<form id=\"loginform\"></form>"))
    }

    @Test
    fun `extracts rsa public key and rejects missing one`() {
        val html = """<script>var publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A";</script>"""
        assertEquals("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A", ChaoxingParsing.extractPublicKey(html))
        assertNull(ChaoxingParsing.extractPublicKey("<html></html>"))
    }

    @Test
    fun `detects session expired responses`() {
        // 超星会话失效时返回的 JSON 没有 ret 字段，只有 msg（实测样本）。
        val timeoutJson = """{"msg":"登录超时,请重新登录！","show_forget_pwd":"1","publicKey":"MIGfMA0GCSqGSIb3"}"""
        assertTrue(ChaoxingParsing.isSessionExpiredBody(timeoutJson))
        assertTrue(ChaoxingParsing.isSessionExpiredBody("""<form id="loginform"></form>"""))
        assertTrue(ChaoxingParsing.isSessionExpiredBody("""{"msg":"请重新登录"}"""))
        assertFalse(ChaoxingParsing.isSessionExpiredBody("""{"ret":0,"msg":"操作成功"}"""))
        assertFalse(ChaoxingParsing.isSessionExpiredBody(null))
        assertFalse(ChaoxingParsing.isSessionExpiredBody(""))
    }

    @Test
    fun `login timeout json parses with ret default minus one`() {
        val timeoutJson = """{"msg":"登录超时,请重新登录！","publicKey":"MIGfMA0GCSqGSIb3"}"""
        val type = com.google.gson.reflect.TypeToken.getParameterized(
            ChaoxingResponse::class.java,
            ChaoxingTimeConfig::class.java,
        ).type
        val response: ChaoxingResponse<ChaoxingTimeConfig> = Gson().fromJson(timeoutJson, type)
        assertEquals(-1, response.ret)
        assertTrue(response.msg.orEmpty().contains("登录超时"))
        assertNull(response.data)
    }

    private fun row(
        period: Int,
        day: Int,
        name: String,
        weeks: String,
    ) = ChaoxingLessonRow(
        semester = "2026-2027-1",
        courseName = name,
        courseCode = "C-${name.hashCode()}",
        teacher = "老师",
        classroom = "教学楼 101",
        building = "综合楼",
        campus = "大兴校区",
        dayOfWeek = day,
        period = period,
        weekText = null,
        weekRange = weeks,
    )
}