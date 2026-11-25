package com.ahu.ahutong.ui.screen.main.home

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.screen.main.schedule.buildingText
import com.ahu.ahutong.ui.screen.main.schedule.campusText
import com.ahu.ahutong.ui.screen.main.schedule.roomText
import com.ahu.ahutong.ui.screen.main.schedule.weekRangeText
import com.ahu.ahutong.ui.state.ScheduleViewModel
import java.util.Locale

/** 课程在当前时刻的状态。 */
internal enum class CourseTiming {
    ONGOING,
    UPCOMING,
    ENDED,
}

/**
 * 首页课程展示所需的完整信息。
 *
 * 首页空间比课表网格宽，可以把课程名、教师、校区、教学楼、教室、周次、
 * 节次与具体时间、课程编号都展示出来。
 */
internal data class CourseDisplayInfo(
    val minuteRange: IntRange?,
    val timeRangeText: String?,
    val startTimeText: String?,
    val endTimeText: String?,
    val periodText: String,
    val teacherText: String,
    val locationText: String,
    val weekText: String,
    val codeText: String,
) {
    fun timingAt(currentMinutes: Int): CourseTiming = when {
        minuteRange == null -> CourseTiming.UPCOMING
        currentMinutes < minuteRange.first -> CourseTiming.UPCOMING
        currentMinutes <= minuteRange.last -> CourseTiming.ONGOING
        else -> CourseTiming.ENDED
    }

    fun minutesUntilStart(currentMinutes: Int): Int? =
        minuteRange?.first?.let { start -> (start - currentMinutes).takeIf { it > 0 } }
}

internal fun Course.displayInfo(): CourseDisplayInfo {
    val range = runCatching { ScheduleViewModel.getCourseTimeRangeInMinutes(this) }.getOrNull()
    val teacher = teacher.orEmpty().trim()
    val campus = campusText()
    val building = buildingText()
    val room = roomText()
    val location = listOf(campus, building, room)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
    val period = com.ahu.ahutong.data.schedule.TimetableProvider
        .periodRangeLabel(start = startTime, length = length)
    val code = courseId.orEmpty().trim()
    return CourseDisplayInfo(
        minuteRange = range,
        timeRangeText = range?.let { "${formatClock(it.first)}-${formatClock(it.last)}" },
        startTimeText = range?.let { formatClock(it.first) },
        endTimeText = range?.let { formatClock(it.last) },
        periodText = period,
        teacherText = teacher,
        locationText = location,
        weekText = weekRangeText(),
        codeText = if (code.isBlank()) "" else "编号 $code",
    )
}

private fun formatClock(minutes: Int): String =
    String.format(Locale.CHINA, "%02d:%02d", (minutes / 60) % 24, minutes % 60)