package io.github.mangi.eta.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Siri 风格视觉令牌层（Design Tokens）。
 *
 * 唯一事实基准：参考源码/siri_ui_ref/设计规格.md（来自 5 张截图的实测读数）。
 * 本层只新增令牌，不改动既有 AppearancePaletteStyle 枚举取值，避免破坏已持久化配置。
 *
 * 色板说明：截图存在环境绿光污染（背景笔记本屏幕反光），G 通道被系统性抬高约 +15~25。
 * 以下色值为去污染估算，渐变方向与明暗关系可信，具体色相装机后可一眼校准。
 */

/** 圆角阶：iOS 液态玻璃的大圆角 + 悬浮观感。 */
object SiriShapes {
    /** 主卡片 / 对话气泡外框。 */
    val card: RoundedCornerShape = RoundedCornerShape(24.dp)

    /** 消息气泡。 */
    val bubble: RoundedCornerShape = RoundedCornerShape(22.dp)

    /** 建议 chip。 */
    val chip: RoundedCornerShape = RoundedCornerShape(20.dp)

    /** 小型容器 / 工具条。 */
    val container: RoundedCornerShape = RoundedCornerShape(16.dp)

    /** 输入胶囊、圆形按钮等全圆角。 */
    val full: RoundedCornerShape = RoundedCornerShape(percent = 50)
}

/** 玻璃质感叠色与描边。 */
object SiriGlass {
    /** 浅色态玻璃面：白底低透明度。 */
    val tintLight = Color(0xB3FFFFFF) // 70%
    val tintLightStrong = Color(0xD9FFFFFF) // 85%

    /** 深色态玻璃面：近黑低透明度。 */
    val tintDark = Color(0x991C1C1E) // 60%
    val tintDarkStrong = Color(0xB31C1C1E) // 70%

    /** 液态玻璃高光描边（模拟边缘折射）。 */
    val edgeLight = Color(0x2EFFFFFF) // 18%
    val edgeDark = Color(0x1AFFFFFF) // 10%
}

/** Siri 渐变底。自上而下：青白 → 淡青绿 → 青 → 青蓝 → 粉白。 */
object SiriBackdrop {
    private val lightTop = Color(0xFFEDF5FA)
    private val lightUpperMid = Color(0xFFB6C9B9)
    private val lightMid = Color(0xFF9CBEB4)
    private val lightLowerMid = Color(0xFFA6BECB)
    private val lightBottom = Color(0xFFDFD6DA)

    private val darkTop = Color(0xFF0A0A0C)
    private val darkUpperMid = Color(0xFF14181A)
    private val darkMid = Color(0xFF161C1E)
    private val darkLowerMid = Color(0xFF141A1E)
    private val darkBottom = Color(0xFF1A1418)

    fun lightBrush(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to lightTop,
            0.28f to lightUpperMid,
            0.50f to lightMid,
            0.72f to lightLowerMid,
            1.00f to lightBottom,
        ),
    )

    fun darkBrush(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to darkTop,
            0.28f to darkUpperMid,
            0.50f to darkMid,
            0.72f to darkLowerMid,
            1.00f to darkBottom,
        ),
    )

    fun brush(isDark: Boolean): Brush = if (isDark) darkBrush() else lightBrush()

    /** 底部纯背景色（无渐变时的兜底）。 */
    fun fallbackColor(isDark: Boolean): Color = if (isDark) darkTop else lightTop
}

/** 文字与图标色。 */
object SiriContent {
    val textPrimaryLight = Color(0xFF1C1C1E)
    val textSecondaryLight = Color(0xFF8A8A8E)
    val textPrimaryDark = Color(0xFFFFFFFF)
    val textSecondaryDark = Color(0xFF98989D)

    fun textPrimary(isDark: Boolean) = if (isDark) textPrimaryDark else textPrimaryLight
    fun textSecondary(isDark: Boolean) = if (isDark) textSecondaryDark else textSecondaryLight
}

/** Siri 呼吸光晕（仅交互触发，禁止常驻无限动画，避免周期性唤醒 CPU）。 */
object SiriGlow {
    val teal = Color(0xFF7FD4C4)
    val violet = Color(0xFFA48BD8)
    val pink = Color(0xFFE3A8C6)

    fun accentBrush(isDark: Boolean): Brush = Brush.radialGradient(
        colors = if (isDark) {
            listOf(teal.copy(alpha = 0.55f), violet.copy(alpha = 0.35f), Color.Transparent)
        } else {
            listOf(teal.copy(alpha = 0.75f), violet.copy(alpha = 0.5f), Color.Transparent)
        },
    )
}

/** 阴影参数（柔和悬浮）。 */
object SiriElevation {
    val cardShadowElevation: Dp = 2.dp
    val cardAmbientAlpha = 0.06f
    val cardSpotAlpha = 0.10f
}

/** 动效令牌：iOS 弹簧手感（轻快起手、缓收）。 */
object SiriMotion {
    /** 起手快。 */
    val easeOutQuint: Easing = Easing { x -> 1f - (1f - x).let { it * it * it * it * it } }

    /** 缓收。 */
    val easeOutCubic: Easing = Easing { x -> 1f - (1f - x).let { it * it * it } }

    /** 标准过渡时长。 */
    const val DURATION_STANDARD = 340
    const val DURATION_FAST = 180
    const val DURATION_SLOW = 520
}
