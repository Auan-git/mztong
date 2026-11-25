package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 长按添加的自定义课程：字段生成、合并、去重。 */
class CustomCourseSupportTest {

    private fun course(id: String, name: String = "服务端课程") = Course().apply {
        setCourseId(id)
        setName(name)
    }

    @Test
    fun `creates custom course with expected fields`() {
        val c = CustomCourseSupport.createCustomCourse(
            name = " 自习 ",
            teacher = "自己",
            location = "图书馆",
            weekday = 3,
            startPeriod = 5,
            length = 2,
            startWeek = 2,
            endWeek = 8,
        )
        assertEquals("自习", c.name.toString())
        assertEquals("3", c.weekday.toString())
        assertEquals("5", c.startTime.toString())
        assertEquals("2", c.length.toString())
        assertEquals("2", c.startWeek.toString())
        assertEquals("8", c.endWeek.toString())
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8), c.weekIndexes?.map { it.toInt() })
        assertTrue(CustomCourseSupport.isCustom(c))
        assertEquals(CustomCourseSupport.CUSTOM_TAG, c.extra.toString())
    }

    @Test
    fun `updates custom course keeping id and slot`() {
        val original = CustomCourseSupport.createCustomCourse(
            name = "旧名",
            teacher = "老师",
            location = "地点",
            weekday = 4,
            startPeriod = 3,
            length = 2,
            startWeek = 1,
            endWeek = 16,
        )
        val updated = CustomCourseSupport.updateCustomCourse(
            course = original,
            name = "新名",
            teacher = "新老师",
            location = "新地点",
            length = 3,
            startWeek = 5,
            endWeek = 12,
        )
        assertEquals(original.courseId, updated.courseId)
        assertEquals("新名", updated.name.toString())
        assertEquals("4", updated.weekday.toString())
        assertEquals("3", updated.startTime.toString())
        assertEquals("3", updated.length.toString())
        assertEquals("5", updated.startWeek.toString())
        assertEquals("12", updated.endWeek.toString())
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12), updated.weekIndexes?.map { it.toInt() })
        assertTrue(CustomCourseSupport.isCustom(updated))
    }

    @Test
    fun `merges custom courses without duplication`() {
        val server = listOf(course("S1"), course("S2"))
        val custom = listOf(course("CUSTOM-1"), course("S1"))
        val merged = CustomCourseSupport.merge(server, custom)
        assertEquals(3, merged.size)
        assertFalse(CustomCourseSupport.isCustom(merged[0]))
        assertTrue(CustomCourseSupport.isCustom(merged[2]))
    }

    @Test
    fun `empty custom list keeps server schedule untouched`() {
        val server = listOf(course("S1"), course("S2"))
        assertEquals(server, CustomCourseSupport.merge(server, emptyList()))
    }
}