package com.ahu.ahutong.ui.screen.main.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.PaletteStyle
import com.kyant.monet.TonalPalettes.Companion.toTonalPalettes
import com.kyant.monet.n1
import com.ahu.ahutong.ui.theme.withNight

/**
 * 课表课程卡片。
 *
 * 展示信息参考学校官方课表的排布：课程名、教师、校区、教学楼、教室、周次；
 * 两节及以上的大卡片显示完整信息，单节卡片只显示课程名与周次，点击查看详情。
 */
@Composable
fun CourseCard(
    course: Course,
    color: Color,
    cellWidth: Dp,
    cellHeight: Dp,
    isCurrentWeek: Boolean = true,
    onClick: (Course) -> Unit
) {
    CompositionLocalProvider(
        LocalTonalPalettes provides color.toTonalPalettes(
            style = PaletteStyle.Vibrant,
            tonalValues = doubleArrayOf(), // 此行代码解决了卡顿问题
        )
    ) {
        Box(
            modifier = with(CourseCardSpec) {
                Modifier
                    .size(
                        cellWidth,
                        cellHeight * course.length.coerceAtLeast(1) +
                            cellSpacing * (course.length.coerceAtLeast(1) - 1)
                    )
                    .offset(
                        mainColumnWidth + (cellWidth + cellSpacing) * (course.weekday - 1) + cellSpacing,
                        mainRowHeight + (cellHeight + cellSpacing) * (course.startTime - 1) + cellSpacing
                    )
                    .clip(SmoothRoundedCornerShape(10.dp))
                    .background(if (!isCurrentWeek) Color.Gray else color)
                    .pointerInput(course.hashCode()) {
                        detectTapGestures { onClick(course) }
                    }
            }
        ) {
            val detailed = course.length.coerceAtLeast(1) >= 2
            val teacher = course.teacher.orEmpty().trim()
            val campus = course.campusText()
            val building = course.buildingText()
            val room = course.roomText()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Text(
                    text = course.name.orEmpty(),
                    color = 100.n1,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (detailed) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 11.sp, lineHeight = 13.sp)
                )
                if (detailed) {
                    Spacer(modifier = Modifier.height(2.dp))
                    if (teacher.isNotBlank()) CourseDetailLine(teacher, 9.5.sp)
                    if (campus.isNotBlank()) CourseDetailLine(campus, 9.sp)
                    if (building.isNotBlank()) CourseDetailLine(building, 9.sp)
                    if (room.isNotBlank()) CourseDetailLine(room, 9.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = course.weekRangeText(),
                    modifier = Modifier.fillMaxWidth(),
                    color = 100.n1.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun CourseDetailLine(text: String, fontSize: TextUnit) {
    Text(
        text = text,
        color = 100.n1.copy(alpha = 0.9f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = fontSize)
    )
}