package com.ahu.ahutong.ui.state

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.AHUApplication
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.data.schedule.CustomCourseSupport
import com.ahu.ahutong.data.schedule.TimetableProvider
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.notification.CourseReminderScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @Author SinkDev
 * @Date 2021/7/27-19:16
 * @Email 468766131@qq.com
 */
class ScheduleViewModel () : ViewModel() {
    val TAG = "ScheduleViewModel"
    val schedule = MutableLiveData<Result<List<Course>>>()
    val nextSchedule = MutableLiveData<Result<List<Course>>>()

    val schoolYear: String
        get() = CurrentWeekResolver.getCachedSemesterKey()?.schoolYear
            ?: AHUCache.getSchoolYear()
            ?: "2022-2023"

    val schoolTerm: String
        get() = CurrentWeekResolver.getCachedSemesterKey()?.schoolTerm ?: "1"

    val scheduleConfig = MutableLiveData<ScheduleConfigBean?>()

    // 更新周
    fun changeWeek(week: Int) {
        val configBean = scheduleConfig.value!!
        configBean.week = week
        scheduleConfig.value = configBean
    }


    /**
     * 刷新课表
     */
    fun refreshSchedule(isRefresh:Boolean = false) {
        viewModelScope.launchSafe {
            withContext(Dispatchers.Main){
                if (!AHUCache.isLogin() && !AHUCache.getMockData()) {
                    schedule.value = Result.failure(Throwable("请先登录！"))
                    return@withContext
                }

                val result = AHURepository.getSchedule(isRefresh = isRefresh)
                schedule.value = result
                if (result.isSuccess) {
                    // 拉取课表时会写入学期开学日期 / 教务当前周次，必须重新计算周次配置，
                    // 否则首登或换学期后仍显示旧的「第 1 周」和错误日期。
                    reloadScheduleConfig()
                    CourseReminderScheduler.reschedule(AHUApplication.getApp())
                }
            }

        }
    }

    fun refreshNextSchedule(isRefresh: Boolean = false) {
        viewModelScope.launchSafe {
            withContext(Dispatchers.Main) {
                if (!AHUCache.isLogin() && !AHUCache.getMockData()) {
                    nextSchedule.value = Result.failure(Throwable("请先登录"))
                    return@withContext
                }

                nextSchedule.value = AHURepository.getNextSchedule(isRefresh = isRefresh)
            }
        }
    }

    fun loadConfig() {
        viewModelScope.launchSafe {
            val initialConfig = withContext(Dispatchers.IO) {
                TimetableProvider.restoreFromCache()
                CurrentWeekResolver.resolveLocalFirst()
            }
            scheduleConfig.postValue(initialConfig.config)
            CourseReminderScheduler.reschedule(AHUApplication.getApp())

            if (!DebugClock.isMocked() && initialConfig.source != CurrentWeekResolver.Source.REMOTE) {
                val remoteConfig = withContext(Dispatchers.IO) {
                    runCatching { CurrentWeekResolver.syncRemoteConfig() }.getOrNull()
                }
                remoteConfig?.config?.let {
                    scheduleConfig.postValue(it)
                    CourseReminderScheduler.reschedule(AHUApplication.getApp())
                }
            }
        }
    }

    /**
     * 仅根据本地缓存重新计算周次/日期（不触发网络请求）。
     *
     * 课表刷新成功、App 跨天/跨周、手动设置时间后调用，避免周次与日期显示滞后。
     */
    fun reloadScheduleConfig() {
        viewModelScope.launchSafe {
            val config = withContext(Dispatchers.IO) {
                CurrentWeekResolver.resolveLocalFirst()
            }
            scheduleConfig.postValue(config.config)
            CourseReminderScheduler.reschedule(AHUApplication.getApp())
        }
    }

    /**
     * 保存时间
     * @param schoolYear String
     * @param schoolTerm String
     * @param week Int
     */
    fun saveTime(schoolYear: String, schoolTerm: String, week: Int) {
        val semesterKey = CurrentWeekResolver.buildSemesterKey(schoolYear, schoolTerm)
        AHUCache.saveSchoolYear(schoolYear)
        AHUCache.saveSchoolTerm(semesterKey)
        // 推算开学日期
        val instance = DebugClock.nowCalendar(Locale.CHINA)
        instance.add(Calendar.DATE, (week - 1) * -7)
        instance.firstDayOfWeek = Calendar.MONDAY
        instance.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        // 修改当前的开学时间和周数
        val configBean = (scheduleConfig.value ?: ScheduleConfigBean()).apply {
            isShowAll = AHUCache.isShowAllCourse()
            isInSemester = true
            startTime = instance.time
            this.week = week
            weekDay = CurrentWeekResolver.getCurrentWeekDay()
        }
        scheduleConfig.value = configBean
        AHUCache.saveSchoolTermStartTime(
            schoolYear,
            schoolTerm,
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(instance.time)
        )
        AHUCache.saveSchoolTermInSemester(
            schoolYear = schoolYear,
            schoolTerm = schoolTerm,
            isInSemester = true,
            observedOn = DebugClock.nowLocalDate().toString()
        )
        CourseReminderScheduler.reschedule(AHUApplication.getApp())
    }

    /**
     * 添加自定义课程（课表空白格长按）。
     *
     * 自定义课程单独存放，刷新服务端课表不会覆盖；保存后重新读取合并课表。
     */
    fun addCustomCourse(
        name: String,
        teacher: String,
        location: String,
        weekday: Int,
        startPeriod: Int,
        length: Int,
        startWeek: Int,
        endWeek: Int,
    ) {
        persistCustomCourse(
            CustomCourseSupport.createCustomCourse(
                name = name,
                teacher = teacher,
                location = location,
                weekday = weekday,
                startPeriod = startPeriod,
                length = length,
                startWeek = startWeek,
                endWeek = endWeek,
            )
        )
    }

    /** 二次编辑自定义课程：保留位置，只更新用户填写的字段。 */
    fun updateCustomCourse(
        course: Course,
        name: String,
        teacher: String,
        location: String,
        length: Int,
        startWeek: Int,
        endWeek: Int,
    ) {
        if (!CustomCourseSupport.isCustom(course)) return
        persistCustomCourse(
            CustomCourseSupport.updateCustomCourse(
                course = course,
                name = name,
                teacher = teacher,
                location = location,
                length = length,
                startWeek = startWeek,
                endWeek = endWeek,
            )
        )
    }

    private fun persistCustomCourse(course: Course) {
        viewModelScope.launchSafe {
            withContext(Dispatchers.IO) {
                val term = AHUCache.getSchoolTerm() ?: return@withContext
                val custom = AHUCache.getCustomCourses(term)
                    .filterNot { it.courseId == course.courseId } + course
                AHUCache.saveCustomCourses(term, custom)
            }
            schedule.value = AHURepository.getSchedule(false)
            CourseReminderScheduler.reschedule(AHUApplication.getApp())
        }
    }

    /** 删除自定义课程（服务端课程不允许在这里删除）。 */
    fun deleteCustomCourse(course: Course) {
        if (!CustomCourseSupport.isCustom(course)) return
        viewModelScope.launchSafe {
            withContext(Dispatchers.IO) {
                val term = AHUCache.getSchoolTerm() ?: return@withContext
                val custom = AHUCache.getCustomCourses(term)
                    .filterNot { it.courseId == course.courseId }
                AHUCache.saveCustomCourses(term, custom)
            }
            schedule.value = AHURepository.getSchedule(false)
            CourseReminderScheduler.reschedule(AHUApplication.getApp())
        }
    }

    companion object {
        /**
         * 节次时间表（节次 → "HH:mm-HH:mm"）。
         *
         * 默认值是民政职业大学 2026-2027-1 学期的实测作息（12 小节）；
         * 拉取课表后会由 [TimetableProvider] 用教务「作息接口」的返回值覆盖并持久化。
         */
        val timetable: Map<Int, String>
            get() = TimetableProvider.current

        /** 用远端作息表更新课表网格。 */
        fun updateTimetable(timetable: Map<Int, String>) = TimetableProvider.update(timetable)

        /** 节次显示名：第N节 / 中午一 / 中午二。 */
        fun periodLabel(index: Int): String = TimetableProvider.label(index)

        /** 课程节次范围显示名，如「第5-6节」「中午一-中午二」。 */
        fun periodRangeLabel(start: Int, length: Int): String =
            TimetableProvider.periodRangeLabel(start, length)

        /**
         * @param from "HH:mm-HH:mm"
         * @param to "HH:mm-HH:mm"
         */
        private fun getTimeRangeInMinutes(
            from: String,
            to: String = from
        ): IntRange {
            val format = SimpleDateFormat("HH:mm", Locale.CHINA)
            val start = format.parse(from.take(5)).let {
                val calendar = Calendar.getInstance(Locale.CHINA)
                calendar.time = it!!
                calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            }
            val end = format.parse(to.takeLast(5)).let {
                val calendar = Calendar.getInstance(Locale.CHINA)
                calendar.time = it!!
                calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            }
            return start..end
        }

        fun getCourseTimeRangeInMinutes(course: Course): IntRange {
            val periods = timetable
            if (periods.isEmpty()) return 0..0
            val firstKey = periods.keys.minOrNull() ?: 1
            val lastKey = periods.keys.maxOrNull() ?: firstKey
            val startText = periods[course.startTime] ?: periods[firstKey] ?: return 0..0
            val endIndex = (course.startTime + course.length - 1).coerceIn(firstKey, lastKey)
            val endText = periods[endIndex] ?: startText
            return getTimeRangeInMinutes(from = startText, to = endText)
        }
    }

    fun clear() {
        schedule.value = Result.success(emptyList())
        scheduleConfig.value = null
    }
}
