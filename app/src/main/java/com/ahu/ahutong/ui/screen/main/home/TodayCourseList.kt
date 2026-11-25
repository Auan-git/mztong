package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.ahu.ahutong.ui.theme.withNight

/**
 * 首页「今日课程」列表。
 *
 * 相比只显示「节次 + 课程名 + 简称」，这里展示课程的完整信息：
 * 节次、开始/结束时间、课程名、教师、校区、教学楼、教室、周次、课程编号，
 * 并标注「上课中 / N 分钟后 / 已结束」。
 */
@Composable
fun TodayCourseList(
    todayCourses: List<Course>,
    currentMinutes: Int,
    navController: NavHostController?,
    enabled: Boolean = true
) {
    if (todayCourses.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(SmoothRoundedCornerShape(32.dp))
                .background(100.n1 withNight 20.n1)
                .then(
                    if (enabled) {
                        Modifier.clickable { navController?.navigate("schedule") }
                    } else {
                        Modifier
                    }
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "今天暂无课程",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "点击查看完整课表",
                color = 50.n1 withNight 80.n1,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val nextCourseIndex = todayCourses.indexOfFirst { course ->
        course.displayInfo().timingAt(currentMinutes) == CourseTiming.UPCOMING
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(SmoothRoundedCornerShape(32.dp))
            .background(100.n1 withNight 20.n1)
            .then(
                if (enabled) {
                    Modifier.clickable { navController?.navigate("schedule") }
                } else {
                    Modifier
                }
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "今日课程",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${todayCourses.size} 门",
                color = 50.n1 withNight 80.n1,
                style = MaterialTheme.typography.labelMedium
            )
        }
        todayCourses.forEachIndexed { index, course ->
            TodayCourseCard(
                course = course,
                currentMinutes = currentMinutes,
                isNext = index == nextCourseIndex,
                enabled = enabled,
                onClick = { navController?.navigate("schedule") }
            )
        }
    }
}

@Composable
private fun TodayCourseCard(
    course: Course,
    currentMinutes: Int,
    isNext: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val info = remember(course) { course.displayInfo() }
    val timing = info.timingAt(currentMinutes)
    val ongoing = timing == CourseTiming.ONGOING
    val ended = timing == CourseTiming.ENDED
    val cardColor = when {
        ongoing -> 90.a1 withNight 45.a1
        ended -> 95.n1 withNight 18.n1
        else -> 95.n1 withNight 25.n1
    }
    val primary = if (ongoing) (0.n1 withNight 100.n1) else Color.Unspecified
    val secondary = if (ongoing) (20.n1 withNight 90.n1) else (50.n1 withNight 80.n1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (ended) 0.55f else 1f)
            .clip(SmoothRoundedCornerShape(20.dp))
            .background(cardColor)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 左侧：节次 + 起止时间
        Column(
            modifier = Modifier.width(56.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = info.periodText,
                color = primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium
            )
            info.startTimeText?.let {
                Text(
                    text = it,
                    color = primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            info.endTimeText?.let {
                Text(
                    text = it,
                    color = secondary,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 右侧：课程完整信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = course.name.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                when {
                    ongoing -> CourseStatusBadge(text = "上课中", emphasized = true)
                    isNext -> CourseStatusBadge(
                        text = formatCountdownText(info.minutesUntilStart(currentMinutes)),
                        emphasized = false
                    )
                    ended -> Text(
                        text = "已结束",
                        color = secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            val meta = listOf(info.teacherText, info.locationText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val extra = listOf(info.weekText, info.codeText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (extra.isNotBlank()) {
                Text(
                    text = extra,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * 距离上课的倒计时文案。
 *
 * 之前用 `minutes / 60` 直接取整，2 小时 43 分会显示成「2 小时后」，
 * 与实际时间不符；这里保留分钟，显示「2小时43分后」。
 */
private fun formatCountdownText(minutes: Int?): String = when {
    minutes == null || minutes <= 1 -> "即将上课"
    minutes < 60 -> "${minutes}分钟后"
    minutes % 60 == 0 -> "${minutes / 60}小时后"
    else -> "${minutes / 60}小时${minutes % 60}分后"
}

@Composable
private fun CourseStatusBadge(
    text: String,
    emphasized: Boolean,
) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(SmoothRoundedCornerShape(10.dp))
            .background(
                if (emphasized) {
                    40.a1 withNight 90.a1
                } else {
                    90.a1 withNight 40.a1
                }
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = if (emphasized) (100.n1 withNight 0.n1) else (0.n1 withNight 100.n1),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall
    )
}