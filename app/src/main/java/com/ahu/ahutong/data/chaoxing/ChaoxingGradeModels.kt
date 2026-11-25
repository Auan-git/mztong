package com.ahu.ahutong.data.chaoxing

import com.google.gson.annotations.SerializedName

/**
 * 民职通 · 超星教务成绩查询数据模型。
 *
 * 协议来源：`chaoxing-schedule/grades.mjs` 的实测结果（2025-2026 学年共 42 条记录验证）。
 * 成绩接口与课表接口的响应结构不同：分页字段（page/rows/total/totalPages）与
 * 成绩数组（results）都在顶层，而不是包在 data 里。
 */

/** 成绩分页响应。 */
data class ChaoxingGradePage(
    val ret: Int = -1,
    val msg: String? = null,
    val page: Int = 0,
    /** 服务端每页条数（字段名是 rows）。 */
    @SerializedName("rows") val pageSize: Int = 0,
    val total: Int = 0,
    val totalPages: Int = 0,
    val results: List<ChaoxingGradeRow>? = null,
)

/** 一条成绩原始记录。字段按接口原文保留，缺失/未公开时保持为空。 */
data class ChaoxingGradeRow(
    val id: String? = null,
    @SerializedName("kcmc") val courseName: String? = null,
    @SerializedName("kcbh") val courseCode: String? = null,
    @SerializedName("xnxq") val semester: String? = null,
    @SerializedName("zhcj") val score: String? = null,
    @SerializedName("xf") val credits: String? = null,
    @SerializedName("hdxf") val earnedCredits: String? = null,
    @SerializedName("jd") val gradePoint: String? = null,
    /** 接口返回的学分绩点，不等于单科绩点或平均绩点，只做保留。 */
    @SerializedName("xfjd") val creditGradePoint: String? = null,
    @SerializedName("kcxz") val courseNatureCode: String? = null,
    @SerializedName("kclx") val courseTypeCode: String? = null,
    @SerializedName("kclb") val courseCategoryCode: String? = null,
    @SerializedName("sfbk") val makeup: String? = null,
    @SerializedName("xdxz") val studyTypeCode: String? = null,
    @SerializedName("xssfwpj") val evaluationPending: String? = null,
    @SerializedName("cjfxms") val components: String? = null,
    @SerializedName("tscjzwmc") val specialGrade: String? = null,
)

/** 归一化后的成绩记录（用于映射到 App 的 Grade 模型）。 */
data class ChaoxingGradeRecord(
    val recordId: String,
    val semester: String,
    val courseCode: String,
    val courseName: String,
    /** 成绩原文（可能是「92.5」「优秀」「合格」等）。 */
    val grade: String?,
    val credits: Double?,
    val earnedCredits: Double?,
    /** 单科绩点，仅当接口返回 jd 时存在。 */
    val gradePoint: Double?,
    val courseNature: String?,
    val isMakeupExam: Boolean?,
    /** 成绩说明/特殊成绩备注（接口有则保留）。 */
    val detail: String?,
)

/** 一次成绩查询的结果。 */
data class ChaoxingGrades(
    val availableSemesters: List<String>,
    val records: List<ChaoxingGradeRecord>,
)