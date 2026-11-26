package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.chaoxing.ChaoxingEvaluationQuestionnaire
import com.ahu.ahutong.data.chaoxing.ChaoxingEvaluationTask
import com.ahu.ahutong.data.chaoxing.ChaoxingEvaluations
import com.ahu.ahutong.data.chaoxing.ChaoxingScheduleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 评教（超星教务）只读查看。提交逻辑尚未接入：官方保存/提交接口未做在线验证。
 */
class ChaoxingEvaluationViewModel(
    private val fetchTasks: suspend () -> Result<ChaoxingEvaluations> =
        { ChaoxingScheduleRepository.fetchEvaluations() },
    private val fetchQuestionnaire: suspend (ChaoxingEvaluationTask) -> Result<ChaoxingEvaluationQuestionnaire> =
        { ChaoxingScheduleRepository.fetchEvaluationQuestionnaire(it) },
) : ViewModel() {

    val tasks = MutableStateFlow<List<ChaoxingEvaluationTask>>(emptyList())
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    val questionnaire = MutableStateFlow<ChaoxingEvaluationQuestionnaire?>(null)
    val isQuestionnaireLoading = MutableStateFlow(false)
    val questionnaireError = MutableStateFlow<String?>(null)

    private var loadedOnce = false
    private var questionnaireJob: Job? = null
    private var questionnaireRequest = 0

    fun loadTasks(force: Boolean = false) {
        if (isLoading.value) return
        if (!force && loadedOnce) return
        isLoading.value = true
        errorMessage.value = null
        viewModelScope.launch {
            try {
                fetchTasks()
                    .onSuccess {
                        tasks.value = it.tasks
                        loadedOnce = true
                    }
                    .onFailure { errorMessage.value = it.message ?: "加载评教任务失败" }
            } finally {
                isLoading.value = false
            }
        }
    }

    fun openQuestionnaire(task: ChaoxingEvaluationTask) {
        if (isQuestionnaireLoading.value) return
        val request = ++questionnaireRequest
        isQuestionnaireLoading.value = true
        questionnaireError.value = null
        questionnaireJob = viewModelScope.launch {
            try {
                val result = fetchQuestionnaire(task)
                if (request == questionnaireRequest) {
                    result.onSuccess { questionnaire.value = it }
                        .onFailure { questionnaireError.value = it.message ?: "加载评教问卷失败" }
                }
            } finally {
                if (request == questionnaireRequest) isQuestionnaireLoading.value = false
            }
        }
    }

    fun closeQuestionnaire() {
        questionnaireRequest++
        questionnaireJob?.cancel()
        questionnaireJob = null
        isQuestionnaireLoading.value = false
        questionnaire.value = null
        questionnaireError.value = null
    }

    fun clearQuestionnaireError() {
        questionnaireError.value = null
    }
}
