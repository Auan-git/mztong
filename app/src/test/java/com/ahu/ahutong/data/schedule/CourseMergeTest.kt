package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 相邻节次同课程合并：1-2 与 3-4 完全一样时应显示成 1-4。
 */
class CourseMergeTest {

    private fun course(
        name: String = "高等数学",
        teacher: String = "张老师",
        location: String = "教学楼 101",
        campus: String = "大兴",
        weekday: Int = 1,
        start: Int,
        length: Int,
        weeks: List<Int> = (1..16).toList(),
    ) = Course().apply {
        setName(name)
        setTeacher(teacher)
        setLocation(location)
        setExtra(campus)
        setWeekday(weekday.toString())
        setStartTime(start.toString())
        setLength(length.toString())
        setStartWeek(weeks.minOrNull().toString())
        setEndWeek(weeks.maxOrNull().toString())
        setWeekIndexes(weeks)
        setCourseId("C001")
    }

    @Test
    fun `merges identical adjacent periods`() {
        val merged = CourseMerge.mergeAdjacent(
            listOf(
                course(start = 1, length = 2),
                course(start = 3, length = 2),
            )
        )
        assertEquals(1, merged.size)
        assertEquals(1, merged.first().startTime)
        assertEquals(4, merged.first().length)
    }

    @Test
    fun `merges a longer contiguous run`() {
        val merged = CourseMerge.mergeAdjacent(
            listOf(
                course(start = 1, length = 2),
                course(start = 3, length = 2),
                course(start = 5, length = 2),
            )
        )
        assertEquals(1, merged.size)
        assertEquals(1, merged.first().startTime)
        assertEquals(6, merged.first().length)
    }

    @Test
    fun `keeps non-contiguous periods separate`() {
        val merged = CourseMerge.mergeAdjacent(
            listOf(
                course(start = 1, length = 2),
                course(start = 5, length = 2),
            )
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `does not merge different course info`() {
        val merged = CourseMerge.mergeAdjacent(
            listOf(
                course(start = 1, length = 2),
                course(start = 3, length = 2, teacher = "李老师"),
            )
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `merges per week range when week ranges are interleaved`() {
        // 超星真实数据形态：同一门课按周次段拆成多条，1-9 周与 10-13 周各有一对 1-2/3-4 节
        val merged = CourseMerge.mergeAdjacent(
            listOf(
                course(start = 1, length = 2, weeks = (1..9).toList(), teacher = "段老师"),
                course(start = 1, length = 2, weeks = (10..13).toList(), teacher = "方老师"),
                course(start = 3, length = 2, weeks = (1..9).toList(), teacher = "段老师"),
                course(start = 3, length = 2, weeks = (10..13).toList(), teacher = "方老师"),
            )
        )
        assertEquals(2, merged.size)
        val firstRange = merged.first { it.teacher == "段老师" }
        assertEquals(1, firstRange.startTime)
        assertEquals(4, firstRange.length)
        val secondRange = merged.first { it.teacher == "方老师" }
        assertEquals(1, secondRange.startTime)
        assertEquals(4, secondRange.length)
    }

    @Test
    fun `does not merge different weeks`() {
        val merged = CourseMerge.mergeAdjacent(
            listOf(
                course(start = 1, length = 2, weeks = (1..8).toList()),
                course(start = 3, length = 2, weeks = (9..16).toList()),
            )
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `merges four single periods even when records arrive out of order`() {
        val input = listOf(4, 2, 1, 3).map { course(start = it, length = 1) }
        val merged = CourseMerge.mergeAdjacent(input)
        assertEquals(1, merged.size)
        assertEquals(1, merged.single().startTime)
        assertEquals(4, merged.single().length)
        assertEquals(listOf(4, 2, 1, 3), input.map { it.startTime })
        assertEquals(listOf(1, 1, 1, 1), input.map { it.length })
        assertEquals(merged, CourseMerge.mergeAdjacent(merged))
    }

    @Test
    fun `merges old split cache records after serialization`() {
        val gson = Gson()
        val oldCache = gson.toJson(listOf(course(start = 1, length = 2), course(start = 3, length = 2)))
        val restored = gson.fromJson(oldCache, Array<Course>::class.java).toList()
        val merged = CourseMerge.mergeAdjacent(restored)
        assertEquals(1, merged.size)
        assertEquals(4, merged.single().length)
        assertEquals("教学楼 101", merged.single().location)
        assertEquals((1..16).toList(), merged.single().weekIndexes)
    }

    @Test
    fun `keeps adjacent lessons with different identity separate`() {
        val variants = listOf(
            course(start = 3, length = 2, name = "大学英语"),
            course(start = 3, length = 2, location = "教学楼 102"),
            course(start = 3, length = 2, campus = "燕郊"),
            course(start = 3, length = 2, weekday = 2),
            course(start = 3, length = 2).apply { setCourseId("C002") },
        )
        variants.forEach { different ->
            assertEquals(2, CourseMerge.mergeAdjacent(listOf(course(start = 1, length = 2), different)).size)
        }
    }

    @Test
    fun `keeps independently editable custom lessons separate`() {
        val first = course(start = 1, length = 2).apply { setCourseId("CUSTOM-first") }
        val second = course(start = 3, length = 2).apply { setCourseId("CUSTOM-second") }
        assertEquals(2, CourseMerge.mergeAdjacent(listOf(first, second)).size)
    }
}
