package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.ahu.ahutong.ui.theme.withNight
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AtAGlance(
    todayCourses: List<Course>,
    currentMinutes: Int,
    navController: NavHostController,
    isInSemester: Boolean = true,
    enabled: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    // 节次时间取不到（course.startTime 超出动态 timetable 范围）的课程不参与时间判断，避免越界崩溃
    fun courseRange(course: Course): IntRange? =
        runCatching { ScheduleViewModel.getCourseTimeRangeInMinutes(course) }.getOrNull()

    val currentCourse = todayCourses.find { course ->
        val range = courseRange(course) ?: return@find false
        currentMinutes in range
    }
    val currentCourseIndex = todayCourses.indexOfFirst { course ->
        val range = courseRange(course) ?: return@indexOfFirst false
        if (currentMinutes in range) {
            true
        } else {
            currentMinutes < range.first
        }
    }.takeIf { it != -1 } ?: todayCourses.lastIndex
    val hasRemainingCourses = if (todayCourses.isNotEmpty()) {
        val range = courseRange(todayCourses.last())
        range != null && currentMinutes <= range.last
    } else {
        false
    }
    val date = SimpleDateFormat("MM-dd / EE", Locale.CHINA).format(DebugClock.nowDate())
    val focusCourse = currentCourse ?: if (hasRemainingCourses) todayCourses[currentCourseIndex] else null
    val focusInfo = focusCourse?.displayInfo()
    Column(
        modifier = Modifier.padding(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp, 8.dp, 16.dp, 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = date,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            trailingContent()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(32.dp))
                .then(
                    if (enabled) {
                        Modifier.clickable { navController.navigate("schedule") }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isInSemester) {
                Text(
                    text = when {
                        currentCourse != null -> "正在上课"
                        hasRemainingCourses -> "下节课是"
                        else -> "今日课程"
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                text = when {
                    !isInSemester -> "假期中"
                    currentCourse != null -> currentCourse.name
                    hasRemainingCourses -> todayCourses[currentCourseIndex].name
                    else -> "已全部上完"
                },
                modifier = if (isInSemester && (currentCourse != null || hasRemainingCourses)) {
                    Modifier
                        .composed {
                            val color = 50.a1 withNight 90.a1
                            drawBehind {
                                drawLine(
                                    color = color,
                                    start = Offset.Zero,
                                    end = Offset(0f, size.height),
                                    strokeWidth = 4.dp.toPx()
                                )
                            }
                        }
                        .padding(start = 8.dp)
                } else {
                    Modifier
                },
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineLarge
            )
            if (isInSemester && focusInfo != null) {
                Text(
                    text = listOf(focusInfo.periodText, focusInfo.timeRangeText.orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = 50.n1 withNight 80.n1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                val meta = listOf(focusInfo.teacherText, focusInfo.locationText)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = 50.n1 withNight 80.n1,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                val extra = listOf(focusInfo.weekText, focusInfo.codeText)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (extra.isNotBlank()) {
                    Text(
                        text = extra,
                        color = 50.n1 withNight 80.n1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(
                text = when {
                    currentCourse != null -> {
                        val duration =
                            (courseRange(currentCourse)?.last ?: currentMinutes) - currentMinutes
                        "距下课还有 " + when {
                            duration % 60 == 0 -> "${duration / 60}小时整"
                            duration > 60 -> "${duration / 60}小时${duration % 60}分钟"
                            else -> "${duration}分钟"
                        }
                    }

                    hasRemainingCourses -> {
                        val duration =
                            (courseRange(todayCourses[currentCourseIndex])?.first
                                ?: currentMinutes) - currentMinutes
                        "还有 " + when {
                            duration % 60 == 0 -> "${duration / 60}小时整"
                            duration > 60 -> "${duration / 60}小时${duration % 60}分钟"
                            else -> "${duration}分钟"
                        } + "，在 ${todayCourses[currentCourseIndex].location}"
                    }

                    else -> "准备您自己的安排吧"
                },
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
