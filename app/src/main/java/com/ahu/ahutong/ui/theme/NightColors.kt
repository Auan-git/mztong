package com.ahu.ahutong.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App 实际生效的深浅色（跟随「设置 → 外观」，而不是系统深色）。
 *
 * 部分设备上 Dialog 子组合里的 `isSystemInDarkTheme()` 会读到系统配置而不是 App 覆盖后的配置，
 * 导致「系统深色 + App 浅色」时，弹层背景取到深色但文字仍是浅色主题，出现黑底黑字。
 * 这里提供的值由 [AHUTheme] 统一注入，随普通 CompositionLocal 继承到弹层里，避免该问题。
 */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

/**
 * 与 kyant.monet 的 `withNight` 同名同签名的本地实现：按 App 主题取色。
 *
 * 用法与原来完全一致：`96.n1 withNight 10.n1`。
 */
@Composable
@ReadOnlyComposable
infix fun Color.withNight(night: Color): Color =
    if (LocalAppDarkTheme.current) night else this