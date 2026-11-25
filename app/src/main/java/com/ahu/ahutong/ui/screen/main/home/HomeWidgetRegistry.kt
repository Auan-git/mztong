package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.ui.graphics.Color
import com.ahu.ahutong.R

data class HomeWidgetSpec(
    val id: String,
    val title: String,
    val route: String,
    val iconId: Int,
    val tint: Color
)

object HomeWidgetRegistry {
    const val slotCount = 8

    // 说明：首页与「小工具」页都不再提供充值/缴费入口
    // （校园卡充值、浴室缴费、电控缴费、网费充值均已移除）。
    val widgets = listOf(
        HomeWidgetSpec(
            id = "grade",
            title = "成绩单",
            route = "grade",
            iconId = R.drawable.ic_grade,
            tint = Color(0xFFFFC107)
        ),
        HomeWidgetSpec(
            id = "phone_book",
            title = "电话本",
            route = "phone_book",
            iconId = R.drawable.ic_phonebook,
            tint = Color(0xFF009688)
        ),
        HomeWidgetSpec(
            id = "exam",
            title = "考场查询",
            route = "exam",
            iconId = R.drawable.ic_exam,
            tint = Color(0xFF4CAF50)
        ),
        HomeWidgetSpec(
            id = "evaluation",
            title = "教评",
            route = "evaluation",
            iconId = R.drawable.ic_evaluation,
            tint = Color(0xFF0D9488)
        ),
        HomeWidgetSpec(
            id = "school_calendar",
            title = "校历",
            route = "school_calendar",
            iconId = R.drawable.ic_schedule,
            tint = Color(0xFF9C27B0)
        ),
        HomeWidgetSpec(
            id = "free_classroom",
            title = "空闲教室",
            route = "free_classroom",
            iconId = R.drawable.ic_round_business_24,
            tint = Color(0xFF03A9F4)
        ),
        HomeWidgetSpec(
            id = "lost_found",
            title = "失物招领",
            route = "lost_found",
            iconId = R.drawable.lost_and_found,
            tint = Color(0xFF1976D2)
        ),
        HomeWidgetSpec(
            id = "weather",
            title = "天气",
            route = "weather",
            iconId = R.drawable.ic_weather,
            tint = Color(0xFFFFB300)
        ),
        HomeWidgetSpec(
            id = "campus_map",
            title = "校园地图",
            route = "campus_map",
            iconId = R.drawable.ic_campus_map,
            tint = Color(0xFF3F7BF6)
        ),
        HomeWidgetSpec(
            id = "repository",
            title = "学习资料",
            route = "repository",
            iconId = R.drawable.ic_repository,
            tint = Color(0xFF8D6E63)
        )
    )

    val widgetById = widgets.associateBy { it.id }
}
