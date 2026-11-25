package com.ahu.ahutong.data.schedule

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal object TeachingWeekPolicy {
    const val SCHEDULE_WEEK_COUNT = 20

    data class Position(
        val week: Int,
        val isInSemester: Boolean,
        val startDate: LocalDate
    )

    fun resolveRemotePosition(
        now: LocalDate,
        remoteWeekIndex: Int,
        remoteIsInSemester: Boolean,
        cachedStartDate: LocalDate?,
        officialStartDate: LocalDate?
    ): Position {
        val mondayOfCurrentWeek = now.minusDays((now.dayOfWeek.value - 1).toLong())
        val isInSemester = remoteIsInSemester && remoteWeekIndex > 0
        val startDate = when {
            isInSemester -> mondayOfCurrentWeek.minusWeeks((remoteWeekIndex - 1).toLong())
            officialStartDate != null -> officialStartDate
            cachedStartDate != null -> cachedStartDate
            else -> mondayOfCurrentWeek
        }
        val week = if (isInSemester) {
            remoteWeekIndex
        } else {
            weekForDate(startDate, now)
        }

        return Position(
            week = week.coerceIn(1, SCHEDULE_WEEK_COUNT),
            isInSemester = isInSemester,
            startDate = startDate
        )
    }

    /**
     * 由「教务返回的第 N 周 + 抓取日期」反推第 1 周周一。
     *
     * 例：2026-11-23（周一）抓到「第 13 周」→ 第 1 周周一 = 2026-08-31。
     */
    fun startDateFromTeachingWeek(observedOn: LocalDate, teachingWeek: Int): LocalDate {
        val week = teachingWeek.coerceIn(1, SCHEDULE_WEEK_COUNT)
        val mondayOfObservedWeek = observedOn.minusDays((observedOn.dayOfWeek.value - 1).toLong())
        return mondayOfObservedWeek.minusWeeks((week - 1).toLong())
    }

    fun weekForDate(startDate: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(startDate, date)
        return ((days / 7L).toInt() + 1).coerceIn(1, SCHEDULE_WEEK_COUNT)
    }

    fun resolveLocalSemesterState(
        startDate: LocalDate,
        date: LocalDate,
        cachedState: Boolean?,
        stateObservedOn: LocalDate?
    ): Boolean {
        if (cachedState != null && stateObservedOn == date) return cachedState
        return !date.isBefore(startDate) && date.isBefore(startDate.plusWeeks(SCHEDULE_WEEK_COUNT.toLong()))
    }
}
