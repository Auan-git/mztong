package com.ahu.ahutong.ui.screen.main.schedule

import com.ahu.ahutong.data.model.Course

private val locationShortenMap = mapOf(
    "综合楼" to "综合",
    "教学楼" to "教学",
    "实训楼" to "实训",
    "图书馆" to "图书",
    "体育馆" to "体育",
    "体育场" to "体育场"
)

private val locationPattern = Regex(
    locationShortenMap.keys.joinToString("|") { Regex.escape(it) }
)

private val weekdayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private val whitespaceRegex = Regex("\\s+")
private val roomCodeRegex = Regex("""[A-Za-z]?\d{1,4}(?:-\d{1,5})?(?:\(\d+\))?$""")

internal fun String?.shortScheduleLocation(): String {
    val shortened = orEmpty()
        .replace(locationPattern) { locationShortenMap[it.value].orEmpty() }
        .replace(Regex("\\s*\\[[^\\]]*]"), "")
        .replace(Regex("\\s+"), "")
        .trim()

    return shortened.ifBlank { "未知" }
}

internal fun Course.weekRangeText(): String {
    val weeks = weekIndexes.orEmpty()
        .filter { it > 0 }
        .distinct()
        .sorted()
    if (weeks.isEmpty()) {
        return when {
            startWeek == endWeek -> "${startWeek}周"
            startWeek > 0 && endWeek > 0 -> "$startWeek-${endWeek}周"
            else -> "周次未知"
        }
    }

    val first = weeks.first()
    val last = weeks.last()
    if (first == last) return "${first}周"

    val isContinuous = weeks.zipWithNext().all { (a, b) -> b - a == 1 }
    if (isContinuous) return "$first-${last}周"

    val isBiweekly = weeks.zipWithNext().all { (a, b) -> b - a == 2 }
    if (isBiweekly) {
        val parity = if (first % 2 == 0) "双" else "单"
        return "$first-${last}${parity}周"
    }

    return weeks.joinToString("/") + "周"
}

/** 周几（1=周一）。 */
internal fun Course.weekdayLabelText(): String {
    val day = weekday.coerceIn(1, 7)
    return weekdayNames[day - 1]
}

/** "1-2 节" 之类的节次范围文本。 */
internal fun Course.periodRangeText(): String =
    com.ahu.ahutong.data.schedule.TimetableProvider.periodRangeLabel(
        start = startTime,
        length = length,
    )

/**
 * 校区简称：Course.extra 中保存的是教务返回的校区名（如「大兴校区」）。
 */
internal fun Course.campusText(): String =
    extra.orEmpty().trim().removeSuffix("校区").trim()

/** 从「躬行楼（4号楼） 公益慈善项目设计与管理实训室 4-503」中取教学楼名。 */
internal fun Course.buildingText(): String {
    val first = location.orEmpty().trim().split(whitespaceRegex).firstOrNull().orEmpty()
    return first
        .substringBefore("（")
        .substringBefore("(")
        .trim()
}

/** 从地点里取教室号，如「4-503」「2-401」「阶梯教室1层」。 */
internal fun Course.roomText(): String {
    val parts = location.orEmpty().trim().split(whitespaceRegex).filter { it.isNotBlank() }
    if (parts.isEmpty()) return ""
    val last = parts.last()
    if (parts.size >= 2 && last.any { it.isDigit() }) return last
    // 只有一段（如「阶梯教室1层」或「未知」）时原样展示，避免截断成无意义片段
    return last
}