package io.github.mangi.eta.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Eta 布局令牌层（Layout Tokens）。
 *
 * 参考基准：NEXUS Agent（github.com/2014569061-png/ai-agent）
 * lib/presentation/theme/app_tokens.dart。
 *
 * 本层只新增常量，不改动既有令牌（SiriShapes / SiriGlass / SiriElevation / SiriMotion）
 * 的任何取值，也不接入任何既有页面；各页面在自己的改造阶段按需引用。
 *
 * 三条约定：
 * 1. 间距统一走 4px 基栅，收敛为 4/8/12/16/24/32。
 * 2. 圆角收敛为 3 档控件级常量；输入框圆角仍由 [SiriShapes.field] 的 25% 提供，
 *    此处不重复定义，避免出现第二个「输入框圆角」事实源。
 * 3. 结构高度对齐参考项目的 topBar / listRow / control / chip / searchBox。
 */

/** 间距栅格：4px 基栅。 */
object EtaSpacing {
    /** 4dp：图标与文字之间的最小间隙。 */
    val xs: Dp = 4.dp

    /** 8dp：同一组内元素间距。 */
    val sm: Dp = 8.dp

    /** 12dp：列表行水平内边距。 */
    val md: Dp = 12.dp

    /** 16dp：页面与卡片内边距。 */
    val lg: Dp = 16.dp

    /** 24dp：区块之间的间距。 */
    val xl: Dp = 24.dp

    /** 32dp：大区块分隔。 */
    val xxl: Dp = 32.dp
}

/** 圆角阶：收敛为 3 档（对齐参考项目的 radiusControl / radiusCard / radiusModal）。 */
object EtaRadius {
    /** 8dp：按钮、chip、行内小控件。 */
    val control: Dp = 8.dp

    /** 12dp：卡片、列表行。 */
    val card: Dp = 12.dp

    /** 16dp：弹层、对话框。 */
    val modal: Dp = 16.dp
}

/** 结构高度：对齐参考项目的结构高度常量。 */
object EtaSize {
    /** 56dp：顶栏高度。 */
    val topBar: Dp = 56.dp

    /** 52dp：二级页列表行高。 */
    val listRow: Dp = 52.dp

    /** 44dp：标准控件点击区。 */
    val control: Dp = 44.dp

    /** 32dp：chip 高度。 */
    val chip: Dp = 32.dp

    /** 36dp：搜索框高度。 */
    val searchBox: Dp = 36.dp

    /** 48dp：最小触摸目标。 */
    val minTouchTarget: Dp = 48.dp
}

/**
 * 描边宽度令牌。
 *
 * 颜色不在此处固化：一律取 [top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme].outline，
 * 以保证浅色/深色两套色板下框线都可见。此处只定义宽度。
 */
object EtaStroke {
    /** 1dp：卡片与列表行的 hairline 框线。 */
    val hairline: Dp = 1.dp
}
