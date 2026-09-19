package io.github.mangi.eta.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    /**
     * 输入框 / 浮窗输入容器圆角。
     *
     * 基准来自实机取证（非估算）：小米 13 (fuxi) 屏幕圆角
     *   dumpsys display -> roundedCorners radius=118px，density=2.75
     *   118 / 2.75 = 42.9dp -> 取 43dp
     * 取代此前的全圆角药丸（percent = 50），使输入区圆角与系统屏幕圆角完全对齐：
     * 单行时观感接近胶囊，多行拉高后自然过渡为大圆角矩形，不再是恒定半圆。
     */
    val field: RoundedCornerShape = RoundedCornerShape(43.dp)
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
    private val lightBase = Color(0xFFFBFDF9)      // 底部暖白（微偏绿，与光晕同调）
    private val lightMint = Color(0xFFD2F5E3)      // 顶部淡薄荷绿
    private val lightSky = Color(0xFFE6F2F5)       // 中部淡青（降饱和，纠正整体偏蓝）

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
    drawSiriBackdrop(isDark)
}

/**
 * 弥散光晕的绘制体（DrawScope 版本）。
 *
 * 抽成独立函数是为了让顶栏毛玻璃的 backdrop 底也能复用同一份光晕，
 * 否则 `rememberLayerBackdrop` 内的白底会把顶栏采样结果冲成不透明白色。
 */
fun DrawScope.drawSiriBackdrop(isDark: Boolean) {
    val base = SiriBackdrop.fallbackColor(isDark)
    drawRect(base)

    val w = size.width
    val h = size.height
    // 问题5：主调必须是「绿」。薄荷绿为绝对主导（大半径 + 高不透明度），
    // 青蓝仅作右上角点缀（小半径 + 低不透明度），彻底纠正此前天蓝占比过大导致的偏蓝观感。
    val mint = if (isDark) Color(0xFF16302A) else Color(0xFFA6E8C8)
    val sky = if (isDark) Color(0xFF16222A) else Color(0xFFD9ECF2)
    val warm = if (isDark) Color(0xFF0B0C0E) else Color(0xFFFBFDF9)

    // 顶部薄荷绿光晕：中心接近不透明，向下、向两侧大范围柔和淡出。
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(mint, mint.copy(alpha = 0.86f), Color.Transparent),
            center = Offset(x = w * 0.42f, y = h * 0.02f),
            radius = h * 0.78f,
        ),
    )
    // 中上部偏右天蓝光晕：与薄荷绿交叠，向右下弥散。
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(sky, sky.copy(alpha = 0.40f), Color.Transparent),
            center = Offset(x = w * 0.82f, y = h * 0.42f),
            radius = h * 0.44f,
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

/**
 * 液态玻璃（Liquid Glass）质感层。
 *
 * 与普通毛玻璃的区别：玻璃面不是均一半透明，而是
 * ① 沿对角线做「上亮下暗」的折射渐变（模拟凸透镜厚度差）；
 * ② 左上内高光 + 右下内暗边（模拟边缘全反射）；
 * ③ 1px 渐变描边（顶部近白、底部近透明），形成「玻璃边缘」的锐利切线；
 * ④ 内层大圆角高光弧，强化「液态」的圆润饱满感。
 *
 * 全部为静态绘制（drawWithContent / drawBehind），不含任何无限动画，
 * 满足「禁止常驻 rememberInfiniteTransition」的功耗红线。
 */
object SiriLiquidGlass {
    /** 玻璃折射渐变（浅色）：左上更亮、右下更透——必须保留足够透明度让光晕透出。 */
    fun refractLight(): Brush = Brush.linearGradient(
        colors = listOf(
            Color(0x8CFFFFFF),
            Color(0x4DFFFFFF),
            Color(0x73FFFFFF),
        ),
    )

    /** 玻璃折射渐变（深色）：左上更亮、右下更暗。 */
    fun refractDark(): Brush = Brush.linearGradient(
        colors = listOf(
            Color(0x3DFFFFFF),
            Color(0x1AFFFFFF),
            Color(0x26FFFFFF),
        ),
    )

    fun refract(isDark: Boolean): Brush = if (isDark) refractDark() else refractLight()

    /** 边缘描边渐变（浅色）：顶部近白高光 → 底部极淡灰。 */
    fun edgeLight(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x99FFFFFF),
            Color(0x33FFFFFF),
            Color(0x1A000000),
        ),
    )

    /** 边缘描边渐变（深色）：顶部微白高光 → 底部近黑。 */
    fun edgeDark(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0x4DFFFFFF),
            Color(0x1AFFFFFF),
            Color(0x33000000),
        ),
    )

    fun edge(isDark: Boolean): Brush = if (isDark) edgeDark() else edgeLight()
}

/**
 * 液态玻璃修饰符：折射渐变底 + 1px 渐变描边 + 内高光。
 *
 * 调用顺序敏感——需先 [clip] 再挂本修饰符，保证描边与高光都被裁剪在形状内。
 * 使用示例：`Modifier.dropShadow(...).clip(shape).siriLiquidGlass(shape, isDark)`
 */
fun Modifier.siriLiquidGlass(
    shape: Shape,
    isDark: Boolean,
    /** 玻璃折射面的整体不透明度，按容器层级微调。 */
    refractionAlpha: Float = 1f,
): Modifier = this
    .drawBehind {
        // 玻璃主体：对角折射渐变。
        drawRect(
            brush = SiriLiquidGlass.refract(isDark),
            alpha = refractionAlpha,
        )
        // 顶部内高光：一条自左向右淡出的窄带，模拟玻璃上沿的镜面反射。
        val highlightHeight = size.height * 0.42f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.14f else 0.55f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = highlightHeight,
            ),
        )
    }
    .border(
        width = 1.dp,
        brush = SiriLiquidGlass.edge(isDark),
        shape = shape,
    )

/**
 * 液态玻璃容器便捷修饰符：裁剪 + 玻璃面 + 渐变描边。
 *
 * 用于输入栏药丸、圆形按钮、建议 chip 等所有需要液态玻璃的容器。
 */
fun Modifier.siriGlassSurface(
    shape: Shape,
    isDark: Boolean,
    refractionAlpha: Float = 1f,
): Modifier = this
    .clip(shape)
    .siriLiquidGlass(shape = shape, isDark = isDark, refractionAlpha = refractionAlpha)

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
