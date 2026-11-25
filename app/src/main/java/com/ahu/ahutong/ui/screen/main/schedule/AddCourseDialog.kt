package com.ahu.ahutong.ui.screen.main.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.schedule.TimetableProvider
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.ahu.ahutong.ui.theme.withNight

/**
 * 长按课表空白格后弹出的「添加课程」对话框。
 *
 * 位置（星期与起始节次）由长按的格子决定，其余信息由用户填写。
 */
@Composable
fun AddCourseDialog(
    weekday: Int,
    startPeriod: Int,
    weekCount: Int = 20,
    initialName: String = "",
    initialTeacher: String = "",
    initialLocation: String = "",
    initialLength: Int = 2,
    initialStartWeek: Int = 1,
    initialEndWeek: Int = weekCount,
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        teacher: String,
        location: String,
        length: Int,
        startWeek: Int,
        endWeek: Int,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var teacher by remember { mutableStateOf(initialTeacher) }
    var location by remember { mutableStateOf(initialLocation) }
    var lengthText by remember { mutableStateOf(initialLength.toString()) }
    var startWeekText by remember { mutableStateOf(initialStartWeek.toString()) }
    var endWeekText by remember { mutableStateOf(initialEndWeek.toString()) }

    val length = lengthText.toIntOrNull()
    val startWeek = startWeekText.toIntOrNull()
    val endWeek = endWeekText.toIntOrNull()
    val valid = name.isNotBlank() &&
        length != null && length in 1..6 &&
        startWeek != null && startWeek in 1..weekCount &&
        endWeek != null && endWeek in 1..weekCount && startWeek <= endWeek

    val weekdayLabel = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(weekday - 1) { "周$weekday" }
    val periodLabel = TimetableProvider.label(startPeriod)

    AlertDialog(
        containerColor = 100.n1 withNight 20.n1,
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isEdit) "编辑课程" else "添加课程",
                    fontWeight = FontWeight.Bold,
                    color = 0.n1 withNight 100.n1,
                )
                Text(
                    text = "$weekdayLabel · $periodLabel 起",
                    style = MaterialTheme.typography.bodyMedium,
                    color = 50.n1 withNight 80.n1,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("老师姓名（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("地点（可选，如 教学楼 101）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lengthText,
                        onValueChange = { lengthText = it.filter { ch -> ch.isDigit() }.take(1) },
                        label = { Text("节数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = startWeekText,
                        onValueChange = { startWeekText = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("开始周") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endWeekText,
                        onValueChange = { endWeekText = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("结束周") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!valid) {
                    Text(
                        text = "请填写课程名称，并确认节数/周次范围有效",
                        style = MaterialTheme.typography.labelSmall,
                        color = 50.n1 withNight 80.n1,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(name.trim(), teacher.trim(), location.trim(), length!!, startWeek!!, endWeek!!)
                },
            ) {
                Text(text = "保存", color = 40.a1 withNight 80.a1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = 50.n1 withNight 80.n1)
            }
        },
    )
}