package io.github.mangi.eta.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle

internal val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }
internal val LocalBlurEnabled = staticCompositionLocalOf { true }
internal val LocalTopBarBlurStyle = staticCompositionLocalOf { AppearanceTopBarBlurStyle.GAUSSIAN }
internal val LocalPlatformDensity = staticCompositionLocalOf<Density?> { null }

/** 当前组合是否处于「聊天舞台」（主界面/对话页）：Siri 视觉仅在舞台内生效。 */
internal val LocalSiriStage = staticCompositionLocalOf { false }

/**
 * 当前是否处于「主界面」路由：绿色渐变光晕背景仅随主界面出现。
 * 对话页虽属聊天舞台（输入栏/气泡保持玻璃样式），但背景为纯白，不再绘制渐变。
 */
internal val LocalSiriBackdrop = staticCompositionLocalOf { false }
