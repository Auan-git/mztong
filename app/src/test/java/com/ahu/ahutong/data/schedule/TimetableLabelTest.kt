package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.chaoxing.ChaoxingPeriod
import com.ahu.ahutong.data.chaoxing.ChaoxingSchedule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 节次显示名：中午一/中午二，下午第一节为第5节。
 */
class TimetableLabelTest {

    @Test
    fun `default labels match the school timetable`() {
        assertEquals("第1节", TimetableProvider.label(1))
        assertEquals("第2节", TimetableProvider.label(2))
        assertEquals("第3节", TimetableProvider.label(3))
        assertEquals("第4节", TimetableProvider.label(4))
        assertEquals("中午一", TimetableProvider.label(5))
        assertEquals("中午二", TimetableProvider.label(6))
        assertEquals("第5节", TimetableProvider.label(7))
        assertEquals("第6节", TimetableProvider.label(8))
        assertEquals("第9节", TimetableProvider.label(11))
        assertEquals("第10节", TimetableProvider.label(12))
    }

    @Test
    fun `normalizes school aliases`() {
        assertEquals("第1节", TimetableProvider.normalizeAlias("一", 1))
        assertEquals("第4节", TimetableProvider.normalizeAlias("四", 4))
        assertEquals("中午一", TimetableProvider.normalizeAlias("中午1", 5))
        assertEquals("中午二", TimetableProvider.normalizeAlias("中午2", 6))
        assertEquals("中午一", TimetableProvider.normalizeAlias("中午一", 5))
        assertEquals("第5节", TimetableProvider.normalizeAlias("五", 7))
        assertEquals("第6节", TimetableProvider.normalizeAlias("六", 8))
        assertEquals("第10节", TimetableProvider.normalizeAlias("十", 12))
        // 无法识别时按序号兜底
        assertEquals("中午一", TimetableProvider.normalizeAlias("", 5))
        assertEquals("第5节", TimetableProvider.normalizeAlias(null, 7))
    }

    @Test
    fun `period range label keeps noon periods readable`() {
        assertEquals("第1-2节", TimetableProvider.periodRangeLabel(1, 2))
        assertEquals("第3-4节", TimetableProvider.periodRangeLabel(3, 2))
        assertEquals("中午一", TimetableProvider.periodRangeLabel(5, 1))
        assertEquals("中午一-中午二", TimetableProvider.periodRangeLabel(5, 2))
        assertEquals("第5-6节", TimetableProvider.periodRangeLabel(7, 2))
        assertEquals("第9-10节", TimetableProvider.periodRangeLabel(11, 2))
    }

    @Test
    fun `schedule periods map to display labels`() {
        val schedule = ChaoxingSchedule(
            semester = "2025-2026-1",
            campusId = "campus",
            campusName = "大兴校区",
            semesterStartDate = null,
            currentWeek = null,
            periods = listOf(
                ChaoxingPeriod(1, 1, "一", "08:10", "08:55"),
                ChaoxingPeriod(5, 3, "中午1", "12:00", "12:45"),
                ChaoxingPeriod(6, 3, "中午2", "12:55", "13:40"),
                ChaoxingPeriod(7, 4, "五", "14:00", "14:45"),
            ),
            meetings = emptyList(),
        )
        val labels = schedule.toPeriodLabels()
        assertEquals("第1节", labels[1])
        assertEquals("中午一", labels[5])
        assertEquals("中午二", labels[6])
        assertEquals("第5节", labels[7])
    }
}