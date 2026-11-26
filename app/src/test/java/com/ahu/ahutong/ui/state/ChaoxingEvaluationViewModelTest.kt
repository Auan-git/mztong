package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.chaoxing.ChaoxingEvaluationQuestionnaire
import com.ahu.ahutong.data.chaoxing.ChaoxingEvaluationTask
import com.ahu.ahutong.data.chaoxing.ChaoxingEvaluations
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChaoxingEvaluationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val task = ChaoxingEvaluationTask(
        "T1/C1/R1", "T1", "C1", "R1", "测试课程", "测试教师", "学生评教",
        "2026-2027-1", "TEST", "0", "未评", null,
    )
    private val questionnaire = ChaoxingEvaluationQuestionnaire(
        task.taskKey, task.courseName, task.teacherName, "0", "未评", false, emptyList(),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `refresh failure preserves previous tasks and reports error`() = runTest(dispatcher) {
        var calls = 0
        val model = ChaoxingEvaluationViewModel(fetchTasks = {
            if (++calls == 1) Result.success(ChaoxingEvaluations(listOf(task)))
            else Result.failure(IllegalStateException("连接失败"))
        })
        model.loadTasks()
        model.loadTasks()
        advanceUntilIdle()
        assertEquals(1, calls)
        model.loadTasks(force = true)
        advanceUntilIdle()
        assertEquals(listOf(task), model.tasks.value)
        assertEquals("连接失败", model.errorMessage.value)
        assertFalse(model.isLoading.value)
    }

    @Test
    fun `returning during fetch prevents late result from reopening questionnaire`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<ChaoxingEvaluationQuestionnaire>>()
        val model = ChaoxingEvaluationViewModel(fetchQuestionnaire = { response.await() })
        model.openQuestionnaire(task)
        runCurrent()
        assertTrue(model.isQuestionnaireLoading.value)
        model.closeQuestionnaire()
        response.complete(Result.success(questionnaire))
        advanceUntilIdle()
        assertNull(model.questionnaire.value)
        assertNull(model.questionnaireError.value)
        assertFalse(model.isQuestionnaireLoading.value)
    }

    @Test
    fun `cancelled request cannot clear loading state of a newer request`() = runTest(dispatcher) {
        val first = CompletableDeferred<Result<ChaoxingEvaluationQuestionnaire>>()
        val second = CompletableDeferred<Result<ChaoxingEvaluationQuestionnaire>>()
        var calls = 0
        val model = ChaoxingEvaluationViewModel(fetchQuestionnaire = {
            val response = if (++calls == 1) first else second
            // Simulate an HTTP operation that finishes after cancellation.
            withContext(NonCancellable) { response.await() }
        })
        model.openQuestionnaire(task)
        runCurrent()
        model.closeQuestionnaire()
        model.openQuestionnaire(task)
        runCurrent()
        first.complete(Result.success(questionnaire))
        runCurrent()
        assertTrue(model.isQuestionnaireLoading.value)
        assertNull(model.questionnaire.value)
        second.complete(Result.success(questionnaire))
        advanceUntilIdle()
        assertEquals(questionnaire, model.questionnaire.value)
        assertFalse(model.isQuestionnaireLoading.value)
        model.closeQuestionnaire()
        assertNull(model.questionnaire.value)
    }
}
