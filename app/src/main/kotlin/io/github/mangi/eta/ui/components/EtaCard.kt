package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.mangi.eta.ui.theme.EtaStroke
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.squircle.squircleBorder
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
 * 实现要点：
 * - 调用方 `modifier` **原样传给 Miuix `Card`**，尺寸/间距/对齐语义与改造前逐字一致，
 *   不引入任何布局行为变化。
 * - 描边由 `Box` 内一层 `matchParentSize()` 的覆盖层绘制。该 `Box` 自身不带 modifier、
 *   只包裹这一张卡片，因此其内容区恒等于卡片尺寸，描边恰好落在卡片真实边界上，
 *   不偏移、不外扩、不改变任何既有尺寸。
 * - 内部仍委托 Miuix `Card`，保留 `LocalContentColor` 注入、按压反馈与点击语义，行为与原来一致。
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
    CardOutline(cornerRadius = cornerRadius) {
        MiuixCard(
            modifier = modifier,
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = colors,
            content = content,
        )
    }
}

/**
 * 可点击的 Eta 统一卡片，对应 [MiuixCard] 的交互重载。
 *
 * 描边画在卡片之上的覆盖层，按压反馈与点击语义仍由内层 Miuix Card 承担，
 * 因此 `pressFeedbackType` / `showIndication` / `holdDownState` 的既有观感不变。
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
    CardOutline(cornerRadius = cornerRadius) {
        MiuixCard(
            modifier = modifier,
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
}

/**
 * 描边容器：调用方 modifier 原样交给卡片，描边由一层同尺寸覆盖层画在卡片之上。
 *
 * 为什么必须叠加而不是挂在卡片自身的 modifier 上：
 * Miuix 的 `squircleBorder` 内部使用 `onDrawBehind`（画在**内容之下**），而 `Card` 的底色由
 * `squircleSurface` 在同一布局节点内填充。若把描边写进卡片自己的 modifier，描边会先被绘制、
 * 随后被卡片底色完全覆盖——框线在视觉上不可见。因此这里用 `matchParentSize` 的覆盖层把
 * 描边画在最上层。
 *
 * 该覆盖层不含任何指针输入修饰符，不参与命中测试，不会拦截卡片的点击与长按。
 */
@Composable
private fun CardOutline(
    cornerRadius: Dp,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        Box(
            modifier = Modifier
                .matchParentSize()
                .squircleBorder(
                    width = EtaStroke.hairline,
                    color = MiuixTheme.colorScheme.outline,
                    cornerRadius = cornerRadius,
                ),
        )
    }
}