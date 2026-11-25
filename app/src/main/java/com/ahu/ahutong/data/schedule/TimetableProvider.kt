package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.dao.AHUCache

/**
 * 课表节次时间表（节次 → "HH:mm-HH:mm"）。
 *
 * 默认值来自民政职业大学 2026-2027-1 学期实测作息（12 小节）；
 * 拉取课表时会用超星教务「作息接口」的返回值覆盖并持久化，避免学校调整作息后时间错误。
 */
object TimetableProvider {

    /** 兜底节次时间（民政职业大学 12 小节）。 */
    val DEFAULT: Map<Int, String> = linkedMapOf(
        1 to "08:10-08:55",
        2 to "09:05-09:50",
        3 to "10:10-10:55",
        4 to "11:05-11:50",
        5 to "12:00-12:45",
        6 to "12:55-13:40",
        7 to "14:00-14:45",
        8 to "14:55-15:40",
        9 to "16:00-16:45",
        10 to "16:55-17:40",
        11 to "18:50-19:35",
        12 to "19:45-20:30",
    )

    /**
     * 兜底节次显示名（民政职业大学 12 小节）。
     *
     * 与教务作息接口的 `jcbm` 对齐：上午第 1-4 节、中午一/中午二（不占教学节次），
     * 下午第一节为「第5节」，一直到晚上的「第10节」。
     */
    val DEFAULT_LABELS: Map<Int, String> = linkedMapOf(
        1 to "第1节",
        2 to "第2节",
        3 to "第3节",
        4 to "第4节",
        5 to "中午一",
        6 to "中午二",
        7 to "第5节",
        8 to "第6节",
        9 to "第7节",
        10 to "第8节",
        11 to "第9节",
        12 to "第10节",
    )

    private val chineseNumbers = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
        "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10,
    )

    @Volatile
    private var override: Map<Int, String>? = null

    @Volatile
    private var labelOverride: Map<Int, String>? = null

    val current: Map<Int, String>
        get() = override ?: DEFAULT

    /** 节次 → 显示名（第N节 / 中午一 / 中午二）。 */
    val labels: Map<Int, String>
        get() = labelOverride ?: DEFAULT_LABELS

    fun update(timetable: Map<Int, String>?) {
        override = timetable?.takeIf { it.isNotEmpty() }?.toSortedMap()
    }

    fun updateLabels(labels: Map<Int, String>?) {
        labelOverride = labels?.takeIf { it.isNotEmpty() }?.toSortedMap()
    }

    /** 从本地缓存恢复（App 冷启动时调用）。 */
    fun restoreFromCache() {
        val cached = runCatching { AHUCache.getTimetable() }.getOrNull()
        update(cached)
        val cachedLabels = runCatching { AHUCache.getPeriodLabels() }.getOrNull()
        updateLabels(cachedLabels)
    }

    /** 保存远端作息与节次名称，并立即生效。 */
    fun persist(timetable: Map<Int, String>, labels: Map<Int, String>? = null) {
        if (timetable.isEmpty()) return
        update(timetable)
        runCatching { AHUCache.saveTimetable(timetable) }
        if (!labels.isNullOrEmpty()) {
            updateLabels(labels)
            runCatching { AHUCache.savePeriodLabels(labels) }
        }
    }

    /** 单个节次的显示名。 */
    fun label(index: Int): String = labels[index] ?: fallbackLabel(index)

    /**
     * 课程节次范围显示名：
     * 1-2 节 → 第1-2节；5-6 节 → 中午一-中午二；7-8 节 → 第5-6节。
     */
    fun periodRangeLabel(start: Int, length: Int): String {
        val end = start + length.coerceAtLeast(1) - 1
        if (start == end) return label(start)
        val startLabel = label(start)
        val endLabel = label(end)
        if (startLabel.startsWith("第") && endLabel.startsWith("第")) {
            return "第" + startLabel.removePrefix("第").removeSuffix("节") +
                "-" + endLabel.removePrefix("第").removeSuffix("节") + "节"
        }
        return "$startLabel-$endLabel"
    }

    /**
     * 归一化教务作息里的 `jcbm`：
     * 一 → 第1节，中午1 → 中午一，五 → 第5节，九 → 第9节；无法识别时按序号兜底。
     */
    fun normalizeAlias(alias: String?, index: Int): String {
        val text = alias?.trim().orEmpty()
        if (text.isEmpty()) return fallbackLabel(index)
        if (text.startsWith("中午")) {
            return when (text.removePrefix("中午").trim()) {
                "1", "一" -> "中午一"
                "2", "二" -> "中午二"
                else -> text
            }
        }
        chineseNumbers[text]?.let { return "第${it}节" }
        text.toIntOrNull()?.let { return "第${it}节" }
        return fallbackLabel(index)
    }

    private fun fallbackLabel(index: Int): String = when {
        index in 1..4 -> "第${index}节"
        index == 5 -> "中午一"
        index == 6 -> "中午二"
        index >= 7 -> "第${index - 2}节"
        else -> "第${index}节"
    }

    /** 安全取某节次的起止时间。 */
    fun rangeOf(period: Int): String? = current[period]

    /** 安全取课程覆盖的起止时间；节次越界时返回 null。 */
    fun timeRangeOf(startPeriod: Int, length: Int): String? {
        val start = current[startPeriod] ?: return null
        val endPeriod = (startPeriod + length.coerceAtLeast(1) - 1).coerceAtMost(current.keys.maxOrNull() ?: startPeriod)
        val end = current[endPeriod] ?: start
        return "$start|$end"
    }
}