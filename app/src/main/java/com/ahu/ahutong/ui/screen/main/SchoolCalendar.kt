package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.chaoxing.ChaoxingCalendar
import com.ahu.ahutong.data.chaoxing.ChaoxingScheduleRepository
import com.ahu.ahutong.data.chaoxing.ChaoxingWeekRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 校历页：直接读取超星教务的校历接口（周次 + 起止日期），本地渲染成列表。
 *
 * 旧实现依赖远程校历图片，服务不可用时整页黑屏并只弹一个 Toast；现在改为教务数据直出，
 * 失败时显示原因和「重试」按钮，不再停留在空白页。
 */
@Composable
fun SchoolCalendar(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var calendar by remember { mutableStateOf<ChaoxingCalendar?>(null) }

    val reload: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            val result = withContext(Dispatchers.IO) {
                ChaoxingScheduleRepository.fetchCalendar()
            }
            isLoading = false
            result.onSuccess { calendar = it }
                .onFailure { errorMessage = it.message ?: "获取校历失败，请稍后重试" }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = "校历",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            isLoading -> CenterHint(text = "正在获取校历…", showProgress = true)

            errorMessage != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "暂未获取到校历",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = reload) { Text("重新获取") }
            }

            else -> CalendarContent(calendar)
        }
    }
}

@Composable
private fun CalendarContent(calendar: ChaoxingCalendar?) {
    val weeks = calendar?.weeks.orEmpty()
    val currentWeek = calendar?.currentWeek
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = calendar?.semester?.let { semesterTitle(it) } ?: "本学期校历",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (currentWeek != null) {
            Text(
                text = "当前第 $currentWeek 周",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))

        weeks.forEach { row ->
            WeekRow(item = row, isCurrent = row.week?.toIntOrNull() == currentWeek)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "数据来自教务系统校历；节假日调休与放假安排以学校通知为准。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeekRow(item: ChaoxingWeekRow, isCurrent: Boolean) {
    val background = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val weekNumber = item.week?.toIntOrNull()
    val weekLabel = when {
        weekNumber == null -> "第-周"
        weekNumber <= 0 -> "预备周"
        else -> "第${weekNumber}周"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = weekLabel,
            modifier = Modifier.width(72.dp),
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
        Text(
            text = dateRangeText(item),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
        if (isCurrent) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "本周",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun CenterHint(text: String, showProgress: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showProgress) {
                CircularProgressIndicator()
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun dateRangeText(item: ChaoxingWeekRow): String {
    val start = formatDate(item.startDate)
    val end = formatDate(item.endDate)
    return when {
        start != null && end != null -> "$start - $end"
        start != null -> "$start 起"
        end != null -> "至 $end"
        else -> "日期待教务更新"
    }
}

private fun formatDate(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.length == 10 } ?: return null
    return runCatching {
        LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
            .format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
    }.getOrNull()
}

private fun semesterTitle(raw: String): String {
    // 2026-2027-1 → 2026-2027 学年 第 1 学期
    val match = Regex("""^(\d{4})-(\d{4})-([12])$""").matchEntire(raw.trim()) ?: return raw
    return "${match.groupValues[1]}-${match.groupValues[2]} 学年 第 ${match.groupValues[3]} 学期"
}
