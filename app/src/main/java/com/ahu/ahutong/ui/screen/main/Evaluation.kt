package com.ahu.ahutong.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.data.chaoxing.ChaoxingEvaluationQuestion
import com.ahu.ahutong.data.chaoxing.ChaoxingEvaluationQuestionnaire
import com.ahu.ahutong.data.chaoxing.ChaoxingEvaluationTask
import com.ahu.ahutong.ui.state.ChaoxingEvaluationViewModel

/**
 * 「小工具 → 教评」：读取超星教务的评教任务与问卷（只读）。
 *
 * 官方保存/提交协议仅从网页源码确认、未做在线验证，因此当前版本不提供提交按钮，
 * 避免误把不完整的评价提交到正式教务系统。
 */
@Composable
fun Evaluation(onBack: () -> Unit, viewModel: ChaoxingEvaluationViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.loadTasks() }
    val questionnaire by viewModel.questionnaire.collectAsState()
    val isQuestionnaireLoading by viewModel.isQuestionnaireLoading.collectAsState()
    BackHandler(enabled = questionnaire != null || isQuestionnaireLoading) {
        viewModel.closeQuestionnaire()
    }
    val current = questionnaire
    if (current != null) {
        EvaluationQuestionnaireScreen(
            viewModel = viewModel,
            questionnaire = current,
        )
    } else {
        EvaluationListScreen(viewModel, onBack)
    }
}

@Composable
private fun EvaluationListScreen(viewModel: ChaoxingEvaluationViewModel, onBack: () -> Unit) {
    val tasks by viewModel.tasks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isQuestionnaireLoading by viewModel.isQuestionnaireLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val questionnaireError by viewModel.questionnaireError.collectAsState()

    val notEvaluated = tasks.count { it.statusCode == "0" }
    val saved = tasks.count { it.statusCode == "1" }
    val submitted = tasks.count { it.statusCode == "2" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                viewModel.closeQuestionnaire()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "评教",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = { viewModel.loadTasks(force = true) },
                enabled = !isLoading && !isQuestionnaireLoading,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }

        if (tasks.isNotEmpty()) {
            Text(
                text = "共 ${tasks.size} 项 · 未评 $notEvaluated · 已保存 $saved · 已提交 $submitted",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isQuestionnaireLoading || (isLoading && tasks.isNotEmpty())) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (errorMessage != null && tasks.isNotEmpty()) {
            Text(
                text = "刷新失败，当前显示上次结果：${errorMessage.orEmpty()}",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (questionnaireError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = questionnaireError.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { viewModel.clearQuestionnaireError() }) { Text("知道了") }
            }
        }

        when {
            isLoading && tasks.isEmpty() -> CenterLoading()
            errorMessage != null && tasks.isEmpty() -> CenterError(
                message = errorMessage.orEmpty(),
                onRetry = { viewModel.loadTasks(force = true) },
            )
            tasks.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无评教任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tasks, key = { it.taskKey }) { task ->
                    EvaluationTaskCard(
                        task = task,
                        enabled = !isQuestionnaireLoading && !isLoading,
                        onOpen = { viewModel.openQuestionnaire(task) },
                    )
                }
                item {
                    Text(
                        text = "本页可查看评教任务与问卷。填写和提交请前往教务系统。",
                        modifier = Modifier.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EvaluationTaskCard(
    task: ChaoxingEvaluationTask,
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.courseName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                EvaluationStatusBadge(task.status, task.statusCode)
            }
            Text(
                text = task.teacherName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val detail = listOfNotNull(
                task.templateName.takeIf { it.isNotBlank() },
                task.semester.takeIf { it.isNotBlank() },
                task.courseCode.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onOpen, enabled = enabled) {
                    Text(if (task.statusCode == "2") "查看详情" else "查看问卷")
                }
            }
        }
    }
}

@Composable
private fun EvaluationStatusBadge(status: String, statusCode: String) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (statusCode) {
        "2" -> colors.primaryContainer to colors.onPrimaryContainer
        "1" -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EvaluationQuestionnaireScreen(
    viewModel: ChaoxingEvaluationViewModel,
    questionnaire: ChaoxingEvaluationQuestionnaire,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.closeQuestionnaire() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = questionnaire.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${questionnaire.teacherName} · ${questionnaire.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = if (questionnaire.readOnly) {
                    "该评教已提交，仅展示问卷内容。"
                } else {
                    "当前为只读预览；需要填写/提交请登录教务系统网页完成。"
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(questionnaire.questions, key = { _, q -> q.questionKey }) { index, question ->
                EvaluationQuestionCard(index = index, question = question)
            }
            item {
                Text(
                    text = "共 ${questionnaire.questions.size} 题，其中必答 ${questionnaire.requiredCount} 题。",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EvaluationQuestionCard(index: Int, question: ChaoxingEvaluationQuestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${index + 1}. ${question.prompt}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (question.required) {
                    Text(
                        text = "必答",
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            if (question.sectionTitle.isNotBlank()) {
                Text(
                    text = question.sectionTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (question.type) {
                "text" -> {
                    val hint = buildString {
                        append("意见")
                        val min = question.minLength
                        val max = question.maxLength
                        if (min != null || max != null) {
                            append("（")
                            if (min != null) append("最少 $min 字")
                            if (min != null && max != null) append("，")
                            if (max != null) append("最多 $max 字")
                            append("）")
                        }
                    }
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = question.currentAnswer.ifBlank { "（未填写）" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    val multiple = question.type == "multiple-choice"
                    val selectedIds = question.currentAnswer
                        .split(',', ';', '|')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                    question.options.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (multiple) {
                                Checkbox(
                                    checked = option.optionId in selectedIds,
                                    onCheckedChange = null,
                                    enabled = false,
                                )
                            } else {
                                RadioButton(
                                    selected = question.currentAnswer == option.optionId,
                                    onClick = {},
                                    enabled = false,
                                )
                            }
                            Text(
                                text = option.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenterError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}
