package com.ahu.ahutong.data.chaoxing

import android.util.Log
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.schedule.CourseMerge
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.data.schedule.TimetableProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 民政职业大学超星教务仓库：登录、拉取课表、写入本地学期/作息缓存。
 *
 * 说明：
 * - 登录密码与学号由 `LoginViewModel` 写入 [AHUCache]（与原工程一致），
 *   本仓库只负责会话与课表数据；
 * - Cookie 由 [ChaoxingClient] 复用工程持久化 CookieJar，冷启动可直接复用；
 * - 会话过期（被重定向回登录页）时自动用本地缓存的学号/密码重登一次；
 * - 拉取成功后写入学期、开学日期、教学周状态与作息时间表，供课表网格、提醒与小组件使用。
 */
object ChaoxingScheduleRepository {

    private const val TAG = "ChaoxingScheduleRepository"

    @Volatile
    private var cachedSession: ChaoxingSession? = null

    private fun client(): ChaoxingClient = ChaoxingClient()

    /** 是否已有可用会话（仅判断本地 Cookie 是否存在）。 */
    fun hasSession(): Boolean = client().hasSessionCookies()

    /**
     * 教务系统登录。
     * @return 成功时返回学号构成的 [User]（超星学生课表页不暴露姓名）
     */
    suspend fun login(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        runCatching {
            val session = client().login(username.trim(), password)
            cachedSession = session
            User(username.trim(), username.trim())
        }.onFailure { Log.w(TAG, "Chaoxing login failed: ${it.message}") }
    }

    /**
     * 拉取当前（或指定）学期课表，并写入本地学期/作息缓存。
     */
    suspend fun fetchCourses(semester: String? = null): Result<List<Course>> =
        withContext(Dispatchers.IO) {
            fetchSchedule(semester).map { schedule ->
                applyScheduleMeta(schedule)
                toCourseList(schedule)
            }
        }

    /** 拉取课表原始结果（不写缓存）。 */
    suspend fun fetchSchedule(semester: String? = null): Result<ChaoxingSchedule> =
        withContext(Dispatchers.IO) {
            try {
                val client = client()
                // 冷启动时 CookieJar 里可能已有有效会话，先直接尝试，避免无谓重登。
                if (cachedSession == null && !client.hasSessionCookies()) {
                    loginWithCachedCredential(client)
                }
                try {
                    Result.success(client.fetchSchedule(semester))
                } catch (e: ChaoxingException) {
                    if (e.kind != ChaoxingErrorKind.SESSION_EXPIRED) throw e
                    Log.i(TAG, "Session expired, re-login with cached credential")
                    loginWithCachedCredential(client)
                    Result.success(client.fetchSchedule(semester))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fetchSchedule failed: ${t.message}")
                Result.failure(t)
            }
        }

    /** 拉取教务校历（周次与起止日期），失败时返回原因供 UI 提示。 */
    suspend fun fetchCalendar(semester: String? = null): Result<ChaoxingCalendar> =
        withContext(Dispatchers.IO) {
            try {
                val client = client()
                if (cachedSession == null && !client.hasSessionCookies()) {
                    loginWithCachedCredential(client)
                }
                try {
                    Result.success(client.fetchCalendar(semester))
                } catch (e: ChaoxingException) {
                    if (e.kind != ChaoxingErrorKind.SESSION_EXPIRED) throw e
                    loginWithCachedCredential(client)
                    Result.success(client.fetchCalendar(semester))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "fetchCalendar failed: ${t.message}")
                Result.failure(t)
            }
        }

    /** 读取评教任务列表（只读）。 */
    suspend fun fetchEvaluations(): Result<ChaoxingEvaluations> = withContext(Dispatchers.IO) {
        runEvaluationCall { it.fetchEvaluations() }
    }

    /** 读取一份评教问卷（只读）。 */
    suspend fun fetchEvaluationQuestionnaire(
        task: ChaoxingEvaluationTask,
    ): Result<ChaoxingEvaluationQuestionnaire> = withContext(Dispatchers.IO) {
        runEvaluationCall { it.fetchEvaluationQuestionnaire(task) }
    }

    private suspend fun <T> runEvaluationCall(
        block: suspend (ChaoxingClient) -> T,
    ): Result<T> {
        return try {
            val client = client()
            if (cachedSession == null && !client.hasSessionCookies()) {
                loginWithCachedCredential(client)
            }
            val result = try {
                block(client)
            } catch (e: ChaoxingException) {
                if (e.kind != ChaoxingErrorKind.SESSION_EXPIRED) throw e
                Log.i(TAG, "Evaluation session expired, re-login with cached credential")
                loginWithCachedCredential(client)
                block(client)
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "evaluation request failed: ${t.message}")
            Result.failure(t)
        }
    }

    /** 拉取下学期课表（用于课表页「预览下学期」）。 */
    suspend fun fetchNextCourses(): Result<List<Course>> {
        val current = currentSemester()
            ?: return Result.failure(ChaoxingException(ChaoxingErrorKind.INVALID_SEMESTER, "尚未获取当前学期"))
        val next = nextSemester(current)
            ?: return Result.failure(ChaoxingException(ChaoxingErrorKind.INVALID_SEMESTER, "无法推算下学期"))
        return fetchCourses(next)
    }

    /** 课表原始数据 → App 内部 [Course] 列表。 */
    fun toCourseList(schedule: ChaoxingSchedule): List<Course> {
        val maxPeriod = schedule.periods.maxOfOrNull { it.index } ?: 0
        return CourseMerge.mergeAdjacent(
            schedule.meetings
                .filter { it.startPeriod in 1..maxPeriod && it.endPeriod <= maxPeriod }
                .map { meeting ->
                    // 说明：Course 是 Java 模型，getWeekday()/setWeekday(String) 类型不一致，
                    // Kotlin 不会生成合成属性，这里统一用显式 setter。
                    Course().apply {
                        setName(meeting.courseName)
                        setTeacher(meeting.teacher)
                        setLocation(meeting.location)
                        setWeekday(meeting.dayOfWeek.toString())
                        setStartTime(meeting.startPeriod.toString())
                        setLength((meeting.endPeriod - meeting.startPeriod + 1).coerceAtLeast(1).toString())
                        setStartWeek((meeting.weeks.minOrNull() ?: 1).toString())
                        setEndWeek((meeting.weeks.maxOrNull() ?: 1).toString())
                        setWeekIndexes(meeting.weeks)
                        setCourseId(meeting.courseCode)
                        setExtra(meeting.campus)
                    }
                }
        )
    }

    /** 把学期、开学日期、教学周状态与作息表写进本地缓存。 */
    fun applyScheduleMeta(schedule: ChaoxingSchedule) {
        val semesterKey = CurrentWeekResolver.parseSemesterKey(schedule.semester) ?: run {
            Log.w(TAG, "Unrecognized semester: ${schedule.semester}")
            return
        }
        AHUCache.saveSchoolYear(semesterKey.schoolYear)
        AHUCache.saveSchoolTerm(semesterKey.raw)

        val startDate = schedule.semesterStartDate
        if (!startDate.isNullOrBlank()) {
            AHUCache.saveSchoolTermStartTime(
                semesterKey.schoolYear,
                semesterKey.schoolTerm,
                startDate,
            )
        }
        AHUCache.saveSchoolTermInSemester(
            schoolYear = semesterKey.schoolYear,
            schoolTerm = semesterKey.schoolTerm,
            isInSemester = true,
            observedOn = DebugClock.nowLocalDate().toString(),
        )
        schedule.currentWeek?.let {
            AHUCache.saveTeachingWeek(it, DebugClock.nowLocalDate().toString())
        }

        val timetable = schedule.toTimetable()
        val periodLabels = schedule.toPeriodLabels()
        if (timetable.isNotEmpty()) {
            TimetableProvider.persist(timetable, periodLabels)
        }
    }

    /** 当前学期（如 2026-2027-1）。 */
    fun currentSemester(): String? =
        CurrentWeekResolver.getCachedSemesterKey()?.raw ?: AHUCache.getSchoolTerm()

    /** 由当前学期推算下一个学期。 */
    fun nextSemester(current: String): String? {
        val match = Regex("""^(\d{4})-(\d{4})-([12])$""").matchEntire(current.trim()) ?: return null
        val startYear = match.groupValues[1].toIntOrNull() ?: return null
        val endYear = match.groupValues[2].toIntOrNull() ?: return null
        val term = match.groupValues[3]
        return if (term == "1") {
            "$startYear-$endYear-2"
        } else {
            "${startYear + 1}-${endYear + 1}-1"
        }
    }

    private suspend fun loginWithCachedCredential(client: ChaoxingClient): ChaoxingSession {
        val user = AHUCache.getCurrentUser()
            ?: throw ChaoxingException(ChaoxingErrorKind.LOGIN_FAILED, "请先登录教务系统")
        val username = user.xh?.takeIf { it.isNotBlank() }
            ?: throw ChaoxingException(ChaoxingErrorKind.LOGIN_FAILED, "本地账号信息缺失，请重新登录")
        val password = AHUCache.getWisdomPassword()?.takeIf { it.isNotBlank() }
            ?: throw ChaoxingException(ChaoxingErrorKind.LOGIN_FAILED, "本地密码已失效，请重新登录")
        val session = client.login(username, password)
        cachedSession = session
        return session
    }
}
