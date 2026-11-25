package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import java.util.UUID

/**
 * 本地手动添加的课程（长按课表空白格添加）。
 *
 * 服务端课表刷新时只覆盖服务端缓存，自定义课程单独存放并在读取时合并，
 * 因此刷新课表不会把用户自己加的课程冲掉。
 */
object CustomCourseSupport {

    const val COURSE_ID_PREFIX = "CUSTOM-"

    /** 自定义课程在 [Course.extra] 中的标记（不会与教务校区名冲突）。 */
    const val CUSTOM_TAG = "自定义"

    fun isCustom(course: Course): Boolean =
        course.courseId?.startsWith(COURSE_ID_PREFIX) == true

    fun createCustomCourse(
        name: String,
        teacher: String,
        location: String,
        weekday: Int,
        startPeriod: Int,
        length: Int,
        startWeek: Int,
        endWeek: Int,
    ): Course {
        val weeks = (startWeek..endWeek).toList()
        return Course().apply {
            setName(name.trim())
            setTeacher(teacher.trim())
            setLocation(location.trim())
            setWeekday(weekday.toString())
            setStartTime(startPeriod.toString())
            setLength(length.toString())
            setStartWeek(startWeek.toString())
            setEndWeek(endWeek.toString())
            setWeekIndexes(weeks)
            setCourseId(COURSE_ID_PREFIX + UUID.randomUUID())
            setExtra(CUSTOM_TAG)
        }
    }

    /** 二次编辑：保留原课程 id 与位置（星期/起始节次），只更新用户可编辑的字段。 */
    fun updateCustomCourse(
        course: Course,
        name: String,
        teacher: String,
        location: String,
        length: Int,
        startWeek: Int,
        endWeek: Int,
    ): Course {
        val weekday = course.weekday.takeIf { it > 0 } ?: 1
        val startPeriod = course.startTime.takeIf { it > 0 } ?: 1
        return createCustomCourse(
            name = name,
            teacher = teacher,
            location = location,
            weekday = weekday,
            startPeriod = startPeriod,
            length = length,
            startWeek = startWeek,
            endWeek = endWeek,
        ).apply {
            setCourseId(course.courseId)
        }
    }

    /** 服务端课表 + 自定义课程（按 courseId 去重）。 */
    fun merge(serverCourses: List<Course>, customCourses: List<Course>): List<Course> {
        if (customCourses.isEmpty()) return serverCourses
        val existingIds = serverCourses.mapNotNull { it.courseId }.toSet()
        return serverCourses + customCourses.filterNot { it.courseId in existingIds }
    }
}