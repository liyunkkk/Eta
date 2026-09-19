package io.github.mangi.eta.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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

/**
 * Apple Intelligence 弥散光晕底。
 *
 * 基准：参考源码/siri_ui_ref/Apple_HIG_设计基准.md（Gemini 读原视频提炼）。
 * 上半部分柔和弥散光晕：顶部淡薄荷绿 → 中部淡天蓝 → 自然淡出至底部暖白。
 * 非生硬线性多段渐变，而是低饱和、大范围的弥散感。
 */
object SiriBackdrop {
    // 浅色态：底部暖白，顶部薄荷绿 + 天蓝弥散光晕。
    private val lightBase = Color(0xFFF8F9FA)      // 底部暖灰/米白
    private val lightMint = Color(0xFFD2F5E3)      // 顶部淡薄荷绿
    private val lightSky = Color(0xFFE3F2FD)       // 中部淡天蓝

    // 深色态：近黑底，顶部暗青 + 暗蓝弥散光晕（按浅色同结构降明度推导）。
    private val darkBase = Color(0xFF0B0C0E)       // 底部近黑
    private val darkMint = Color(0xFF14201B)       // 顶部暗薄荷
    private val darkSky = Color(0xFF141B22)        // 中部暗蓝

    /**
     * 浅色弥散光晕：三段柔和垂直渐变，光晕集中在顶部、向下淡出至暖白。
     * 通过让中间停靠更靠上 + 底部大段同色，模拟「光晕向上弥散、向下消失」。
     */
    fun lightBrush(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to lightMint,
            0.30f to lightSky,
            0.62f to lightBase,
            1.00f to lightBase,
        ),
    )

    fun darkBrush(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to darkMint,
            0.30f to darkSky,
            0.62f to darkBase,
            1.00f to darkBase,
        ),
    )

    fun brush(isDark: Boolean): Brush = if (isDark) darkBrush() else lightBrush()

    /** 底部纯背景色（无渐变时的兜底）。 */
    fun fallbackColor(isDark: Boolean): Color = if (isDark) darkBase else lightBase
}

/**
 * Apple Intelligence 弥散光晕背景 Modifier。
 *
 * 在暖白/近黑底上叠加顶部两个大范围径向光晕（薄荷绿 + 天蓝），
 * 光晕自顶部向下、向两侧柔和弥散，淡出至底色——比线性分段更接近原型的「弥散光晕」。
 * 仅 SIRI 模式由聊天舞台根容器调用；DEFAULT 不加。
 */
fun Modifier.siriBackdrop(isDark: Boolean): Modifier = this.drawBehind {
    val base = SiriBackdrop.fallbackColor(isDark)
    drawRect(base)

    val w = size.width
    val h = size.height
    val mint = if (isDark) Color(0xFF1B2C24) else Color(0xFFB7E9CC)
    val sky = if (isDark) Color(0xFF18222C) else Color(0xFFCFE6FF)
    val warm = if (isDark) Color(0xFF0B0C0E) else Color(0xFFFDF8F4)

    // 顶部薄荷绿光晕：中心接近不透明，向下、向两侧大范围柔和淡出。
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(mint, mint.copy(alpha = 0.72f), Color.Transparent),
            center = Offset(x = w * 0.34f, y = h * 0.04f),
            radius = h * 0.62f,
        ),
    )
    // 中上部偏右天蓝光晕：与薄荷绿交叠，向右下弥散。
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(sky, sky.copy(alpha = 0.6f), Color.Transparent),
            center = Offset(x = w * 0.72f, y = h * 0.34f),
            radius = h * 0.58f,
        ),
    )
    // 底部暖白回填：让下半屏自然过渡到暖白/米白。
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, warm.copy(alpha = 0.85f)),
            startY = h * 0.52f,
            endY = h * 0.92f,
        ),
    )
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
