package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.model.ScheduleConfigBean
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CurrentWeekResolver {

    enum class Source {
        LOCAL,
        REMOTE,
        DEFAULT
    }

    data class SemesterKey(
        val raw: String,
        val schoolYear: String,
        val schoolTerm: String
    )

    data class ResolvedConfig(
        val config: ScheduleConfigBean,
        val source: Source
    )

    fun buildSemesterKey(schoolYear: String, schoolTerm: String): String {
        return "${schoolYear.trim()}-${schoolTerm.trim()}"
    }

    fun parseSemesterKey(raw: String?): SemesterKey? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val normalized = raw.trim()
        val parts = normalized.split("-")
        return when {
            parts.size == 3 -> SemesterKey(
                raw = normalized,
                schoolYear = "${parts[0]}-${parts[1]}",
                schoolTerm = parts[2]
            )

            parts.size == 1 && AHUCache.getSchoolYear() != null -> {
                val schoolYear = AHUCache.getSchoolYear().orEmpty()
                SemesterKey(
                    raw = buildSemesterKey(schoolYear, normalized),
                    schoolYear = schoolYear,
                    schoolTerm = normalized
                )
            }

            else -> null
        }
    }

    fun getCachedSemesterKey(): SemesterKey? {
        return parseSemesterKey(AHUCache.getSchoolTerm())
    }

    fun getCurrentWeekDay(calendar: Calendar = DebugClock.nowCalendar(Locale.CHINA)): Int {
        return (calendar[Calendar.DAY_OF_WEEK] - 1).takeIf { it != 0 } ?: 7
    }

    fun resolveLocalConfig(now: LocalDate = DebugClock.nowLocalDate()): ResolvedConfig? {
        val semesterKey = getCachedSemesterKey() ?: return null
        val cachedStartTime = AHUCache.getSchoolTermStartTime(
            semesterKey.schoolYear,
            semesterKey.schoolTerm
        )
        val cachedStartDate = cachedStartTime?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val startDate = cachedStartDate ?: deriveStartDateFromTeachingWeek(now) ?: return null
        // 由教务周次反推出来的开学日期写回缓存，后续启动不再依赖网络
        if (cachedStartDate == null) {
            AHUCache.saveSchoolTermStartTime(
                semesterKey.schoolYear,
                semesterKey.schoolTerm,
                startDate.toString()
            )
        }
        val week = TeachingWeekPolicy.weekForDate(startDate, now)
        val cachedState = AHUCache.getSchoolTermInSemester(
            semesterKey.schoolYear,
            semesterKey.schoolTerm
        )
        val stateObservedOn = AHUCache.getSchoolTermInSemesterObservedOn(
            semesterKey.schoolYear,
            semesterKey.schoolTerm
        )?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val isInSemester = TeachingWeekPolicy.resolveLocalSemesterState(
            startDate = startDate,
            date = now,
            cachedState = cachedState,
            stateObservedOn = stateObservedOn
        )
        return ResolvedConfig(
            config = buildScheduleConfig(
                week = week,
                weekDay = now.dayOfWeek.value,
                startDate = startDate,
                isInSemester = isInSemester
            ),
            source = Source.LOCAL
        )
    }

    /**
     * 由「教务返回的当前周次 + 抓取日期」反推第 1 周周一。
     *
     * 用于开学日期缓存缺失（旧版本升级、缓存被清理、教务校历缺第 1 周）时兜底，
     * 避免直接回落到「第 1 周 + 本周日期」的错误显示。
     */
    private fun deriveStartDateFromTeachingWeek(now: LocalDate): LocalDate? {
        val week = AHUCache.getTeachingWeek()?.takeIf { it in 1..60 } ?: return null
        val observedOn = AHUCache.getTeachingWeekObservedOn()
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        val startDate = TeachingWeekPolicy.startDateFromTeachingWeek(observedOn, week)
        // 极端情况下反推出的开学日期在未来，说明数据不可信，直接放弃
        return startDate.takeIf { !it.isAfter(now.plusWeeks(1)) }
    }

    suspend fun resolveLocalFirst(now: LocalDate = DebugClock.nowLocalDate()): ResolvedConfig {
        val localConfig = resolveLocalConfig(now)
        if (DebugClock.isMocked()) {
            return localConfig ?: defaultConfig(now)
        }
        val cachedSemester = getCachedSemesterKey()
        val stateObservedOn = cachedSemester?.let {
            AHUCache.getSchoolTermInSemesterObservedOn(it.schoolYear, it.schoolTerm)
        }
        if (localConfig != null && stateObservedOn == now.toString()) return localConfig

        return runCatching { syncRemoteConfig(now) }.getOrNull()
            ?: localConfig
            ?: defaultConfig(now)
    }

    /**
     * 兼容入口：原工程会请求教务「当前教学周」接口。
     *
     * 民政职业大学教务为超星系统，开学日期与当前周次在拉取课表时已写入本地缓存
     * （见 ChaoxingScheduleRepository.applyScheduleMeta），这里直接由缓存构造配置，
     * 不再请求已停用的旧教务接口，避免无谓的网络等待。
     */
    suspend fun syncRemoteConfig(now: LocalDate = DebugClock.nowLocalDate()): ResolvedConfig? {
        if (DebugClock.isMocked()) return null
        // 教务已切换为超星：周次/开学日期在拉取课表时写入本地缓存，
        // 这里直接按同一套规则重算，避免请求已停用的旧教务接口。
        return resolveLocalConfig(now)?.copy(source = Source.REMOTE)
    }
    fun defaultConfig(now: LocalDate = DebugClock.nowLocalDate()): ResolvedConfig {
        val weekDay = now.dayOfWeek.value
        val mondayOfCurrentWeek = now.minusDays((weekDay - 1).toLong())
        return ResolvedConfig(
            config = buildScheduleConfig(
                week = 1,
                weekDay = weekDay,
                startDate = mondayOfCurrentWeek,
                isInSemester = false
            ).apply { setHasSemesterData(false) },
            source = Source.DEFAULT
        )
    }

    private fun buildScheduleConfig(
        week: Int,
        weekDay: Int,
        startDate: LocalDate,
        isInSemester: Boolean
    ): ScheduleConfigBean {
        return ScheduleConfigBean().apply {
            this.week = week
            this.weekDay = weekDay
            this.startTime = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
            this.isShowAll = AHUCache.isShowAllCourse()
            this.isInSemester = isInSemester
            this.setHasSemesterData(true)
        }
    }

}
