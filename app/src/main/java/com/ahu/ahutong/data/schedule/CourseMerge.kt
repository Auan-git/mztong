package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course

/**
 * 把同一门课在相邻节次上的多条记录合并成一条，避免课表里「1-2 节」「3-4 节」分开显示。
 *
 * 注意：超星对同一门课可能按周次段拆成多条（例如 1-9 周一条、10-13 周一条），
 * 每个周次段又会按大节拆成 1-2 节、3-4 节两条，且这些记录在列表里是交错的。
 * 因此不能只扫描相邻元素，必须先按「完全相同」分组，再在组内合并连续节次。
 *
 * 分组条件：星期、课程名、教师、地点、校区、课程编号、周次集合、起止周全部相同。
 * 仅用于展示层数据整理；自定义课程因课程编号（UUID）不同不会互相合并。
 */
object CourseMerge {

    fun mergeAdjacent(courses: List<Course>): List<Course> {
        if (courses.size <= 1) return courses

        val grouped = LinkedHashMap<MergeKey, MutableList<Course>>()
        courses
            .sortedWith(compareBy<Course>({ it.weekday }, { it.startTime }))
            .forEach { course ->
                grouped.getOrPut(keyOf(course)) { mutableListOf() }.add(course)
            }

        val merged = ArrayList<Course>(courses.size)
        grouped.values.forEach { sameCourseByWeeks ->
            var rangeStart = sameCourseByWeeks.first().startTime
            var rangeEnd = rangeStart + sameCourseByWeeks.first().length - 1
            var template = sameCourseByWeeks.first()
            sameCourseByWeeks.drop(1).forEach { course ->
                if (course.startTime == rangeEnd + 1) {
                    rangeEnd = course.startTime + course.length - 1
                } else {
                    merged.add(copyWithRange(template, rangeStart, rangeEnd - rangeStart + 1))
                    template = course
                    rangeStart = course.startTime
                    rangeEnd = course.startTime + course.length - 1
                }
            }
            merged.add(copyWithRange(template, rangeStart, rangeEnd - rangeStart + 1))
        }

        return merged.sortedWith(
            compareBy<Course>({ it.weekday }, { it.startTime }, { it.name.orEmpty() })
        )
    }

    private data class MergeKey(
        val weekday: Int,
        val startWeek: Int,
        val endWeek: Int,
        val name: String,
        val teacher: String,
        val location: String,
        val extra: String,
        val courseId: String,
        val weekIndexes: List<Int>,
    )

    private fun keyOf(course: Course): MergeKey = MergeKey(
        weekday = course.weekday,
        startWeek = course.startWeek,
        endWeek = course.endWeek,
        name = course.name.orEmpty(),
        teacher = course.teacher.orEmpty(),
        location = course.location.orEmpty(),
        extra = course.extra.orEmpty(),
        courseId = course.courseId.orEmpty(),
        weekIndexes = course.weekIndexes?.sorted().orEmpty(),
    )

    private fun copyWithRange(source: Course, startPeriod: Int, length: Int): Course =
        Course().apply {
            setName(source.name)
            setTeacher(source.teacher)
            setLocation(source.location)
            setWeekday(source.weekday.toString())
            setStartTime(startPeriod.toString())
            setLength(length.toString())
            setStartWeek(source.startWeek.toString())
            setEndWeek(source.endWeek.toString())
            setWeekIndexes(source.weekIndexes)
            setCourseId(source.courseId)
            setExtra(source.extra)
        }
}