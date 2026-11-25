package com.ahu.ahutong.data.chaoxing

import com.google.gson.annotations.SerializedName

/**
 * 民职通 · 超星教务（bcsa.jw.chaoxing.com）数据模型。
 *
 * 协议来源：`chaoxing-schedule` 抓取脚本的实测结果（2026-2027-1 学期验证）。
 * 接口为教务网页内部接口，字段类型不稳定，全部按可空处理；解析失败由
 * [ChaoxingParsing] 抛出 [ChaoxingException]，不把接口错误当成空课表。
 */

/** 超星教务通用响应包装：ret == 0 表示成功。 */
data class ChaoxingResponse<T>(
    val ret: Int = -1,
    val msg: String? = null,
    val data: T? = null,
)

/** 作息节次（/admin/api/getZclistByXnxq → data.jcsjszList）。 */
data class ChaoxingPeriodRow(
    @SerializedName("jc") val period: String? = null,
    @SerializedName("jcbm") val alias: String? = null,
    @SerializedName("djs") val block: String? = null,
    @SerializedName("kssj") val start: String? = null,
    @SerializedName("jssj") val end: String? = null,
)

/** 校历（/admin/api/getZclistByXnxq → data.zclist）。 */
data class ChaoxingWeekRow(
    @SerializedName("zc") val week: String? = null,
    @SerializedName("minrq") val startDate: String? = null,
    @SerializedName("maxrq") val endDate: String? = null,
)

/** 当前登录人信息（/admin/api/getZclistByXnxq → data.jsxq）。 */
data class ChaoxingUserInfo(
    @SerializedName("currentUserName") val userName: String? = null,
    @SerializedName("dataXnxq") val semester: String? = null,
    @SerializedName("xqid") val campusId: String? = null,
)

/** 作息与校历接口的 data 部分。 */
data class ChaoxingTimeConfig(
    @SerializedName("jcsjszList") val periodRows: List<ChaoxingPeriodRow>? = null,
    @SerializedName("zclist") val weekRows: List<ChaoxingWeekRow>? = null,
    @SerializedName("dqzc") val currentWeek: String? = null,
    @SerializedName("jsxq") val userInfo: ChaoxingUserInfo? = null,
)

/**
 * 排课明细（/admin/pkgl/xskb/sdpkkbList → data[]）。
 *
 * 一行 = 某门课在某天某一小节的一次排课；`kcmc` / `tmc` / `croommc` 含 HTML 链接，
 * 需去标签后使用。`djc` 是 1..N 的小节序号，不是「大节」。
 */
data class ChaoxingLessonRow(
    val id: String? = null,
    @SerializedName("xnxq") val semester: String? = null,
    @SerializedName("kcmc") val courseName: String? = null,
    @SerializedName("kcbh") val courseCode: String? = null,
    @SerializedName("tmc") val teacher: String? = null,
    @SerializedName("croommc") val classroom: String? = null,
    @SerializedName("jxlmc") val building: String? = null,
    @SerializedName("xqmc") val campus: String? = null,
    @SerializedName("jxbmc") val teachingClass: String? = null,
    @SerializedName("kcxz") val courseNature: String? = null,
    @SerializedName("xingqi") val dayOfWeek: Int = 0,
    @SerializedName("djc") val period: Int = 0,
    /** 展开后的实际周次（优先使用），如 `1,2,3,4,5,7,8,9,10,11,13,14,15,16,17,18`。 */
    @SerializedName("zcstr") val weekText: String? = null,
    /** 原始周次范围描述，如 `1-5,7-11,13-18`。 */
    @SerializedName("zc") val weekRange: String? = null,
)

/** 规范化后的一个小节时间。 */
data class ChaoxingPeriod(
    val index: Int,
    val block: Int,
    val alias: String,
    val start: String,
    val end: String,
)

/** 规范化后的一条上课安排（同一门课同一天同一大节合并后）。 */
data class ChaoxingMeeting(
    val courseCode: String,
    val courseName: String,
    val teacher: String,
    val location: String,
    val campus: String,
    val dayOfWeek: Int,
    val weeks: List<Int>,
    val startPeriod: Int,
    val endPeriod: Int,
)

/** 一次完整抓取的结果。 */
data class ChaoxingSchedule(
    val semester: String,
    val campusId: String,
    val campusName: String,
    /** 校历第 1 周周一，格式 yyyy-MM-dd。 */
    val semesterStartDate: String?,
    /** 抓取时服务器返回的当前周次。 */
    val currentWeek: Int?,
    val periods: List<ChaoxingPeriod>,
    val meetings: List<ChaoxingMeeting>,
) {
    /** 节次 → "HH:mm-HH:mm"，供课表网格与提醒使用。 */
    fun toTimetable(): Map<Int, String> =
        periods.sortedBy { it.index }.associate { it.index to "${it.start}-${it.end}" }

    /**
     * 节次 → 显示名，与教务作息 `jcbm` 对齐：
     * 一~四 → 第1~4节；中午1/2 → 中午一/中午二；五~十 → 第5~10节（下午第一节为第5节）。
     */
    fun toPeriodLabels(): Map<Int, String> =
        periods.sortedBy { it.index }.associate {
            it.index to com.ahu.ahutong.data.schedule.TimetableProvider.normalizeAlias(it.alias, it.index)
        }
}

/** 超星教务请求错误。 */
class ChaoxingException(
    val kind: ChaoxingErrorKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

enum class ChaoxingErrorKind {
    NETWORK,
    LOGIN_FAILED,
    CAPTCHA_REQUIRED,
    SESSION_EXPIRED,
    SCHEMA_CHANGED,
    API_REJECTED,
    INVALID_SEMESTER,
    SEMESTER_UNAVAILABLE,
    INVALID_WEEKS,
    UNKNOWN_PERIOD,
    INVALID_RESPONSE,
}