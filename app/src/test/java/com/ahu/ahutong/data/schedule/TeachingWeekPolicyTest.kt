package com.ahu.ahutong.data.schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeachingWeekPolicyTest {

    @Test
    fun `in-semester response infers term start from current teaching week`() {
        val position = TeachingWeekPolicy.resolveRemotePosition(
            now = LocalDate.of(2026, 3, 18),
            remoteWeekIndex = 3,
            remoteIsInSemester = true,
            cachedStartDate = null,
            officialStartDate = null
        )

        assertTrue(position.isInSemester)
        assertEquals(3, position.week)
        assertEquals(LocalDate.of(2026, 3, 2), position.startDate)
    }

    @Test
    fun `holiday response uses official date and never becomes week one`() {
        val position = TeachingWeekPolicy.resolveRemotePosition(
            now = LocalDate.of(2026, 7, 21),
            remoteWeekIndex = 0,
            remoteIsInSemester = false,
            cachedStartDate = LocalDate.of(2026, 7, 20),
            officialStartDate = LocalDate.of(2026, 3, 2)
        )

        assertFalse(position.isInSemester)
        assertEquals(TeachingWeekPolicy.SCHEDULE_WEEK_COUNT, position.week)
        assertEquals(LocalDate.of(2026, 3, 2), position.startDate)
    }

    @Test
    fun `pre-semester holiday opens the first week with its real dates`() {
        val position = TeachingWeekPolicy.resolveRemotePosition(
            now = LocalDate.of(2026, 8, 15),
            remoteWeekIndex = 0,
            remoteIsInSemester = false,
            cachedStartDate = null,
            officialStartDate = LocalDate.of(2026, 9, 7)
        )

        assertFalse(position.isInSemester)
        assertEquals(1, position.week)
        assertEquals(LocalDate.of(2026, 9, 7), position.startDate)
    }

    @Test
    fun `stale holiday state expires when the semester begins`() {
        assertTrue(
            TeachingWeekPolicy.resolveLocalSemesterState(
                startDate = LocalDate.of(2026, 9, 7),
                date = LocalDate.of(2026, 9, 7),
                cachedState = false,
                stateObservedOn = LocalDate.of(2026, 9, 6)
            )
        )
    }

    @Test
    fun `stale in-semester state expires after the schedule range`() {
        assertFalse(
            TeachingWeekPolicy.resolveLocalSemesterState(
                startDate = LocalDate.of(2026, 3, 2),
                date = LocalDate.of(2026, 7, 20),
                cachedState = true,
                stateObservedOn = LocalDate.of(2026, 7, 19)
            )
        )
    }

    @Test
    fun `teaching week derives the real semester start date`() {
        // 2026-11-23 是周一，教务返回第 13 周，则第 1 周周一应为 2026-08-31
        assertEquals(
            LocalDate.of(2026, 8, 31),
            TeachingWeekPolicy.startDateFromTeachingWeek(LocalDate.of(2026, 11, 23), 13)
        )
        // 抓取日不是周一时，先归一到所在周的周一
        assertEquals(
            LocalDate.of(2026, 8, 31),
            TeachingWeekPolicy.startDateFromTeachingWeek(LocalDate.of(2026, 11, 25), 13)
        )
        // 第 1 周就是抓取日所在周
        assertEquals(
            LocalDate.of(2026, 8, 31),
            TeachingWeekPolicy.startDateFromTeachingWeek(LocalDate.of(2026, 9, 2), 1)
        )
    }

    @Test
    fun `week number counts from monday and rolls over correctly`() {
        val start = LocalDate.of(2026, 8, 31)
        assertEquals(1, TeachingWeekPolicy.weekForDate(start, LocalDate.of(2026, 8, 31)))
        assertEquals(1, TeachingWeekPolicy.weekForDate(start, LocalDate.of(2026, 9, 6)))
        assertEquals(2, TeachingWeekPolicy.weekForDate(start, LocalDate.of(2026, 9, 7)))
        assertEquals(13, TeachingWeekPolicy.weekForDate(start, LocalDate.of(2026, 11, 23)))
        assertEquals(20, TeachingWeekPolicy.weekForDate(start, LocalDate.of(2027, 1, 11)))
        // 超出 20 周按 20 周截断
        assertEquals(20, TeachingWeekPolicy.weekForDate(start, LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun `same-day remote holiday state remains authoritative`() {
        val date = LocalDate.of(2026, 10, 1)
        assertFalse(
            TeachingWeekPolicy.resolveLocalSemesterState(
                startDate = LocalDate.of(2026, 9, 7),
                date = date,
                cachedState = false,
                stateObservedOn = date
            )
        )
    }
}
