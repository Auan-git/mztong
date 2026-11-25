package com.ahu.ahutong.ui.screen.main.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.ahu.ahutong.ui.theme.withNight

/**
 * 课程详情弹层：周次、节次时间、地点、教师、校区。
 */
@Composable
fun CourseDetailDialog(
    course: Course,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(SmoothRoundedCornerShape(28.dp))
                .background(96.n1 withNight 10.n1)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = course.name.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CourseInfoRow(
                    icon = Icons.Outlined.Schedule,
                    label = "时间",
                    value = buildString {
                        append(course.weekdayLabelText())
                        append(" · ")
                        append(course.periodRangeText())
                        courseTimeText(course)?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                )
                CourseInfoRow(
                    icon = Icons.Outlined.DateRange,
                    label = "周次",
                    value = course.weekRangeText(),
                )
                if (course.location.orEmpty().isNotBlank()) {
                    CourseInfoRow(
                        icon = Icons.Outlined.Navigation,
                        label = "地点",
                        value = course.location.orEmpty(),
                    )
                }
                val campus = course.campusText()
                if (campus.isNotBlank()) {
                    CourseInfoRow(
                        icon = Icons.Outlined.LocationOn,
                        label = "校区",
                        value = campus,
                    )
                }
                if (course.teacher.orEmpty().isNotBlank()) {
                    CourseInfoRow(
                        icon = Icons.Outlined.Person,
                        label = "教师",
                        value = course.teacher.orEmpty(),
                    )
                }
            }
            if (onEdit != null || onDelete != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (onEdit != null) {
                        TextButton(onClick = onEdit) {
                            Text(
                                text = "编辑",
                                color = 40.a1 withNight 80.a1,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(
                                text = "从课表删除",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "点击空白处关闭",
                style = MaterialTheme.typography.labelSmall,
                color = 60.n1 withNight 70.n1,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun CourseInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelLarge,
            color = 60.n1 withNight 70.n1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun courseTimeText(course: Course): String? {
    val timetable = com.ahu.ahutong.ui.state.ScheduleViewModel.timetable
    val start = timetable[course.startTime] ?: return null
    val lastIndex = (course.startTime + course.length.coerceAtLeast(1) - 1)
        .coerceAtMost(timetable.keys.maxOrNull() ?: course.startTime)
    val end = timetable[lastIndex] ?: start
    return "${start.substringBefore("-")} - ${end.substringAfter("-")}"
}