package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import io.github.mangi.eta.ui.theme.EtaStroke
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.squircle.SquircleDefaults
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.squircle.isSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Eta 二级页统一卡片：Miuix Card + 显式 hairline 框线。
 *
 * 参考基准：NEXUS Agent `lib/presentation/widgets/section_card.dart` 的
 * 「实色 surface + 1px hairline 边框」策略。
 *
 * 为什么需要本组件：Miuix 的 `Card` 只接受 `colors`（color / contentColor），
 * **没有 border 参数**；而浅色态下卡片底 `surfaceContainer` 为纯白、页面底 `surface`
 * 为 `0xFFF7F7F7`，两者仅差 8 级灰度且无描边，卡片边界在视觉上完全消失。
 * 本组件在卡片边界补一条 `outline` 描边，恢复卡片轮廓。
 *
 * 实现要点（**不得再改动**）：
 * - 调用方 `modifier` **原样、无包裹地**传给 Miuix `Card`。绝不允许在中间插入任何
 *   布局容器（`Box` / `Column` 等）：`Modifier.weight(...)` / `fillMaxHeight()` /
 *   `align(...)` 都是 **ParentData** 修饰符，只在「直接父节点是对应作用域」时才生效。
 *   一旦被容器隔断，`weight` 会被静默丢弃，导致多列网格退化为单列、行高错乱。
 * - 描边以 `drawWithContent` 追加在 `modifier` 链上，在卡片**自身绘制完成后**再画，
 *   因此必定位于卡片底色之上（Miuix 的 `squircleBorder` 用的是 `onDrawBehind`，
 *   直接挂在卡片 modifier 上会被 `squircleSurface` 的底色完全覆盖，故不可用）。
 * - `drawWithContent` 是纯绘制修饰符，不参与测量与布局，不改变任何既有尺寸与位置。
 * - 描边路径与 `squircleSurface` 使用同一套几何参数（`addSquircleRect` +
 *   `SquircleDefaults.Extension` + `isSquircleEnabled()` 回退），因此与卡片轮廓严格对齐。
 * - 卡片底色仍为不透明的 `surfaceContainer`，不引入透明或模糊，二级页纯白底约定不变。
 *
 * 参数与 [MiuixCard] 的两个重载一一对应，各页面用 import 别名即可无缝切换。
 *
 * 重要：`cornerRadius` 默认值与 Miuix `Card` 完全一致（[CardDefaults.CornerRadius]，16dp），
 * 因此别名替换后既有页面的圆角、内边距、底色与交互观感**全部不变，只多出一条 1dp 框线**。
 */
@Composable
fun EtaCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    MiuixCard(
        modifier = modifier.etaCardOutline(cornerRadius),
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = colors,
        content = content,
    )
}

/**
 * 可点击的 Eta 统一卡片，对应 [MiuixCard] 的交互重载。
 *
 * 描边同样以 `drawWithContent` 追加在 `modifier` 链上，按压反馈与点击语义仍由
 * 内层 Miuix Card 承担，因此 `pressFeedbackType` / `showIndication` / `holdDownState`
 * 的既有观感不变，且 `weight` / `fillMaxHeight` 等 ParentData 语义完整保留。
 */
@Composable
fun EtaCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    showIndication: Boolean = false,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    MiuixCard(
        modifier = modifier.etaCardOutline(cornerRadius),
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = colors,
        pressFeedbackType = pressFeedbackType,
        showIndication = showIndication,
        holdDownState = holdDownState,
        onClick = onClick,
        onLongPress = onLongPress,
        content = content,
    )
}

/**
 * 在卡片自身绘制完成之后，把 hairline 描边画在最上层。
 *
 * 几何参数与 Miuix `squircleBorder` 保持逐字一致（内缩半个描边宽度、
 * `extension` 取 [SquircleDefaults.Extension]、`squircleEnabled` 走
 * [isSquircleEnabled] 回退），确保描边与 `squircleSurface` 的轮廓完全重合。
 */
@Composable
private fun Modifier.etaCardOutline(cornerRadius: Dp): Modifier {
    val squircleEnabled = isSquircleEnabled()
    val outlineColor = MiuixTheme.colorScheme.outline
    val strokeWidth = EtaStroke.hairline
    return this.drawWithContent {
        drawContent()
        val widthPx = strokeWidth.toPx()
        val cornerRadiusPx = cornerRadius.toPx()
        val halfStroke = widthPx / 2f
        val innerWidth = size.width - widthPx
        val innerHeight = size.height - widthPx
        if (widthPx > 0f && innerWidth > 0f && innerHeight > 0f) {
            val path = Path()
            path.addSquircleRect(
                width = innerWidth,
                height = innerHeight,
                cornerRadius = (cornerRadiusPx - halfStroke).coerceAtLeast(0f),
                extension = SquircleDefaults.Extension,
                squircleEnabled = squircleEnabled,
            )
            translate(halfStroke, halfStroke) {
                drawPath(path = path, color = outlineColor, style = Stroke(width = widthPx))
            }
        }
    }
}