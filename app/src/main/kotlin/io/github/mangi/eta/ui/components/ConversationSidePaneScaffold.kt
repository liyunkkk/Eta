package io.github.mangi.eta.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.app.LocalSiriStage
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.formatCompactTokenCount
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.theme.EtaRadius
import io.github.mangi.eta.ui.theme.EtaSize
import io.github.mangi.eta.ui.theme.EtaSpacing
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.squircle.absoluteSquircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowListPopup

private object DrawerMetrics {
    val PaneMaxWidth = 340.dp
    val PaneWidthFraction = 0.84f
    val ForegroundCornerRadiusFallback = 24.dp
    val ForegroundShadowRadius = 12.dp
    const val ForegroundShadowAlpha = 0.12f
    const val SettleDampingRatio = 1f
    const val SettleStiffness = 146f
    const val SettleVisibilityThresholdPx = 0.5f
    const val SettlePositionThresholdFraction = 0.5f
    val PaneHorizontalPadding = EtaSpacing.lg
    val TopInset = EtaSpacing.lg
    val AfterActionBar = 18.dp
    val BottomInset = EtaSpacing.md
    val ActionIconSize = 20.dp
    val SectionTopPadding = EtaSpacing.sm
    val SectionBottomPadding = EtaSpacing.md
    val SectionIconSize = 14.dp
    val SectionIconGap = EtaSpacing.sm
    val SectionCountGap = EtaSpacing.md
    /** 行高对齐 EtaSize.listRow（52dp），与二级页列表行统一。 */
    val RowMinHeight = EtaSize.listRow
    val RowGap = EtaSpacing.xs
    val RowCornerRadius = EtaRadius.card
    val RowHorizontalPadding = EtaSpacing.md
    val RowVerticalPadding = EtaSpacing.md
    val ActiveDotSize = 6.dp
    val ActiveDotGap = 10.dp
    val EmptyVerticalPadding = 28.dp
    val DockTopGap = EtaSpacing.md
    val DockEntryCornerRadius = EtaRadius.control
    val DockEntryIconSize = 20.dp
    val DockEntryLabelGap = 3.dp
    val DockEntryVerticalPadding = EtaSpacing.sm
    /** 信息行之间的间距。 */
    val InfoRowGap = EtaSpacing.sm
    /** 上下文用量进度条高度。 */
    val ContextBarHeight = 6.dp
    /** 上下文用量进度条圆角（取高度的一半，呈胶囊形）。 */
    val ContextBarCornerRadius = 3.dp
    /** 底部 Dock 两个语义分区之间的间距。 */
    val DockSectionGap = EtaSpacing.md
    /** Dock 分区标签与图标行之间的间距。 */
    val DockLabelGap = EtaSpacing.xs
}

private enum class ConversationPaneAnchor {
    Closed,
    Open,
}

@Composable
internal fun ConversationSidePaneScaffold(
    state: ConversationPaneUiState,
    modelName: String,
    contextUsage: AgentContextUsageUi?,
    visible: Boolean,
    backHandlerEnabled: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSearchChange: (String) -> Unit,
    onConversationSelected: (String) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sceneLifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val paneWidth = minOf(maxWidth * DrawerMetrics.PaneWidthFraction, DrawerMetrics.PaneMaxWidth)
        val paneWidthPx = with(density) { paneWidth.toPx() }
        val anchors = remember(paneWidthPx) {
            DraggableAnchors {
                ConversationPaneAnchor.Closed at 0f
                ConversationPaneAnchor.Open at paneWidthPx
            }
        }
        val paneDragState = remember {
            AnchoredDraggableState(
                initialValue = if (visible) ConversationPaneAnchor.Open else ConversationPaneAnchor.Closed,
                anchors = anchors,
            )
        }
        val settleAnimation = remember {
            spring<Float>(
                dampingRatio = DrawerMetrics.SettleDampingRatio,
                stiffness = DrawerMetrics.SettleStiffness,
                visibilityThreshold = DrawerMetrics.SettleVisibilityThresholdPx,
            )
        }
        val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
            state = paneDragState,
            positionalThreshold = { distance ->
                distance * DrawerMetrics.SettlePositionThresholdFraction
            },
            animationSpec = settleAnimation,
        )
        val currentVisible by rememberUpdatedState(visible)
        val currentOnOpen by rememberUpdatedState(onOpen)
        val currentOnDismiss by rememberUpdatedState(onDismiss)
        val shouldClipForeground by remember(paneDragState) {
            derivedStateOf {
                val offset = paneDragState.offset
                !offset.isNaN() && offset > 0.5f
            }
        }
        val systemCornerRadius = rememberNavSystemCornerRadius()
        val foregroundCornerRadius = systemCornerRadius.takeIf { it > 0.dp }
            ?: DrawerMetrics.ForegroundCornerRadiusFallback

        SideEffect {
            paneDragState.updateAnchors(anchors)
        }

        LaunchedEffect(visible, paneWidthPx) {
            val target = if (visible) ConversationPaneAnchor.Open else ConversationPaneAnchor.Closed
            if (paneDragState.targetValue != target || paneDragState.settledValue != target) {
                paneDragState.animateTo(target, settleAnimation)
            }
        }

        LaunchedEffect(paneDragState) {
            snapshotFlow { paneDragState.settledValue }.collectLatest { settledValue ->
                val settledOpen = settledValue == ConversationPaneAnchor.Open
                if (settledOpen != currentVisible) {
                    if (settledOpen) currentOnOpen() else currentOnDismiss()
                }
            }
        }

        // NavDisplay 的退出条目在转场期间仍会保留组合；仅允许已稳定显示的首页
        // 处理侧栏返回，避免它抢先消费二级页面的第一次返回事件。
        NavigationBackHandler(
            state = navigationEventState,
            isBackEnabled = visible &&
                backHandlerEnabled &&
                sceneLifecycleState == Lifecycle.State.RESUMED,
            onBackCompleted = onDismiss,
        )

        ConversationPanePanel(
            state = state,
            width = paneWidth,
            modelName = modelName,
            contextUsage = contextUsage,
            onSearchChange = onSearchChange,
            onConversationSelected = onConversationSelected,
            onConversationRename = onConversationRename,
            onConversationExport = onConversationExport,
            onConversationDelete = onConversationDelete,
            onOpenSettings = onOpenSettings,
            onOpenModelProviders = onOpenModelProviders,
            onOpenTools = onOpenTools,
            onOpenSkills = onOpenSkills,
            onOpenCharacters = onOpenCharacters,
            onOpenPermissions = onOpenPermissions,
            modifier = Modifier.zIndex(0f),
        )

        Box(
            modifier = Modifier
                .width(paneWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    val offset = paneDragState.offset.takeUnless(Float::isNaN) ?: 0f
                    val progress = if (paneWidthPx > 0f) {
                        (offset / paneWidthPx).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    alpha = 1f - progress
                }
                .background(MiuixTheme.colorScheme.windowDimming)
                .zIndex(0.5f),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    val offset = paneDragState.offset.takeUnless(Float::isNaN)
                        ?: if (visible) paneWidthPx else 0f
                    IntOffset(offset.roundToInt(), 0)
                }
                .then(
                    if (shouldClipForeground) {
                        Modifier
                            .dropShadow(
                                shape = AbsoluteRoundedCornerShape(
                                    topLeft = foregroundCornerRadius,
                                    topRight = 0.dp,
                                    bottomRight = 0.dp,
                                    bottomLeft = foregroundCornerRadius,
                                ),
                                shadow = Shadow(
                                    radius = DrawerMetrics.ForegroundShadowRadius,
                                    color = Color.Black,
                                    alpha = DrawerMetrics.ForegroundShadowAlpha,
                                ),
                            )
                            .absoluteSquircleClip(
                                topLeft = foregroundCornerRadius,
                                topRight = 0.dp,
                                bottomRight = 0.dp,
                                bottomLeft = foregroundCornerRadius,
                            )
                    } else {
                        Modifier
                    },
                )
                // 保持物理左右方向，不随 RTL 镜像：会话列表始终从屏幕左侧显露。
                .anchoredDraggable(
                    state = paneDragState,
                    reverseDirection = false,
                    orientation = Orientation.Horizontal,
                    enabled = backHandlerEnabled,
                    flingBehavior = flingBehavior,
                )
                .zIndex(1f),
        ) {
            content()
            if (visible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onDismiss),
                )
            }
        }
    }
}

@Composable
private fun ConversationPanePanel(
    state: ConversationPaneUiState,
    width: androidx.compose.ui.unit.Dp,
    modelName: String,
    contextUsage: AgentContextUsageUi?,
    onSearchChange: (String) -> Unit,
    onConversationSelected: (String) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // state.conversations 已由 AgentAppState 按标题、预览与消息内容过滤。
    val query = state.searchQuery.trim()
    val groups = remember(state.conversations) { state.conversations.groupForDrawer() }
    val density = LocalDensity.current
    // 问题4：对话列表为独立功能面板，不要边界背景，整块统一纯白实底。
    val isSiriPane = LocalSiriStage.current

    Surface(
        modifier = modifier
            .width(width)
            .fillMaxHeight(),
        color = if (isSiriPane) Color.White else MiuixTheme.colorScheme.surface,
        contentColor = MiuixTheme.colorScheme.onSurface,
    ) {
        // 问题4：列表全高滚动，搜索区与 Dock 作为浮层盖在内容上。
        // 顶部/底部浮层已取消毛玻璃采样（否则会在列表顶部/底部形成一圈「边界背景」），
        // 因此不再需要 backdrop 捕获——同时省去一份无谓的 layer 捕获开销。
        var headerHeightPx by remember { mutableIntStateOf(0) }
        var dockHeightPx by remember { mutableIntStateOf(0) }
        val listState = rememberLazyListState()
        // 问题4：顶部/底部浮层已取消毛玻璃与分隔线（整块纯白统一），
        // 原先基于滚动位置的分隔线状态不再需要，已一并移除。
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = DrawerMetrics.PaneHorizontalPadding)
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    top = with(density) { headerHeightPx.toDp() },
                    bottom = with(density) { dockHeightPx.toDp() },
                ),
                verticalArrangement = Arrangement.spacedBy(DrawerMetrics.RowGap),
                overscrollEffect = null,
            ) {
                item(key = "pane-environment") {
                    PaneEnvironmentCard(
                        modelName = modelName,
                        contextUsage = contextUsage,
                        onOpenModelProviders = onOpenModelProviders,
                    )
                }
                item(key = "pane-section-recent") {
                    PaneSectionLabel(text = stringResource(R.string.drawer_section_recent))
                }
                if (state.conversations.isEmpty()) {
                    item {
                        EmptyConversations(isSearching = query.isNotBlank())
                    }
                } else {
                    groups.forEach { group ->
                        item(key = "section-${group.section}") {
                            ConversationSectionHeader(group = group)
                        }
                        items(
                            items = group.items,
                            key = { it.id },
                        ) { conversation ->
                            ConversationTextRow(
                                conversation = conversation,
                                selected = conversation.id == state.selectedConversationId,
                                onClick = { onConversationSelected(conversation.id) },
                                onRename = { onConversationRename(conversation) },
                                onExport = { onConversationExport(conversation) },
                                onDelete = { onConversationDelete(conversation) },
                            )
                        }
                    }
                }
            }
            PaneFrostRegion(
                // 问题4：顶部不再使用毛玻璃浮层 + 分隔线，消除「边界背景」割裂感。
                backdrop = null,
                showDivider = false,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { headerHeightPx = it.height },
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        )
                        .padding(horizontal = DrawerMetrics.PaneHorizontalPadding),
                ) {
                    Spacer(modifier = Modifier.height(DrawerMetrics.TopInset))
                    PaneActionBar(
                        query = state.searchQuery,
                        onSearchChange = onSearchChange,
                    )
                    Spacer(modifier = Modifier.height(DrawerMetrics.AfterActionBar))
                }
            }
            PaneFrostRegion(
                // 问题4：底部 Dock 同步取消毛玻璃与分隔线，整块面板背景纯白统一。
                backdrop = null,
                showDivider = false,
                dividerAtTop = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { dockHeightPx = it.height },
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        )
                        .padding(horizontal = DrawerMetrics.PaneHorizontalPadding),
                ) {
                    Spacer(modifier = Modifier.height(DrawerMetrics.DockTopGap))
                    PaneDock(
                        onOpenSettings = onOpenSettings,
                        onOpenModelProviders = onOpenModelProviders,
                        onOpenTools = onOpenTools,
                        onOpenSkills = onOpenSkills,
                        onOpenCharacters = onOpenCharacters,
                        onOpenPermissions = onOpenPermissions,
                    )
                    Spacer(modifier = Modifier.height(DrawerMetrics.BottomInset))
                }
            }
        }
    }
}

/**
 * 侧栏边缘的毛玻璃区域。毛玻璃不可用时（关闭模糊或设备不支持 RuntimeShader）
 * 退回不透明底色，行为与之前一致。
 */
@Composable
private fun PaneFrostRegion(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
    dividerAtTop: Boolean = false,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val frostModifier = if (backdrop == null) {
        modifier.background(surfaceColor)
    } else {
        modifier.textureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = PaneFrostBlurRadius,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(
                    BlendColorEntry(surfaceColor.copy(alpha = PaneFrostSurfaceAlpha)),
                ),
            ),
        )
    }
    Box(modifier = frostModifier) {
        content()
        AnimatedVisibility(
            visible = showDivider,
            modifier = Modifier
                .align(if (dividerAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth(),
            enter = fadeIn(animationSpec = tween(durationMillis = 140)),
            exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PaneDividerThickness)
                    .background(MiuixTheme.colorScheme.outline.copy(alpha = PaneDividerAlpha)),
            )
        }
    }
}

/** Linux 环境的工作目录，与 TerminalRuntime / ProotCommandBuilder 的默认 cwd 保持一致。 */
private const val DrawerWorkspacePath = "/workspace"

private val PaneDividerThickness = 0.5.dp
private const val PaneDividerAlpha = 0.5f
private const val PaneFrostBlurRadius = 25f
private const val PaneFrostSurfaceAlpha = 0.78f

/**
 * 运行环境卡：当前模型、工作区与上下文用量。
 *
 * 参考基准：NEXUS Agent `chat_catalog_drawer.dart` 的抽屉信息分区，
 * 承载在 EtaCard 上（实色底 + 1dp hairline 框线），保持侧栏纯白不透明。
 *
 * 上下文用量为静态数值渲染：不使用 rememberInfiniteTransition 或任何常驻动画，
 * 避免引入周期性唤醒（功耗红线）。
 */
@Composable
private fun PaneEnvironmentCard(
    modelName: String,
    contextUsage: AgentContextUsageUi?,
    onOpenModelProviders: () -> Unit,
) {
    EtaCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = DrawerMetrics.RowCornerRadius,
        insideMargin = PaddingValues(
            horizontal = DrawerMetrics.RowHorizontalPadding,
            vertical = DrawerMetrics.RowVerticalPadding,
        ),
    ) {
        PaneInfoRow(
            label = stringResource(R.string.ui_current_model_a0af8f),
            value = modelName.ifBlank { stringResource(R.string.drawer_value_unset) },
            onClick = onOpenModelProviders,
        )
        Spacer(modifier = Modifier.height(DrawerMetrics.InfoRowGap))
        PaneInfoRow(
            label = stringResource(R.string.drawer_workspace_label),
            value = DrawerWorkspacePath,
        )
        Spacer(modifier = Modifier.height(DrawerMetrics.InfoRowGap))
        PaneContextUsageRow(usage = contextUsage)
    }
}

/** 单条信息行：左侧标签、右侧取值；传入 onClick 时整行可点击。 */
@Composable
private fun PaneInfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(DrawerMetrics.SectionIconGap))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 上下文用量 HUD：占比文本 + 静态进度条 + token 明细。 */
@Composable
private fun PaneContextUsageRow(usage: AgentContextUsageUi?) {
    val progress = usage?.progress
    val barColor = when {
        progress == null -> MiuixTheme.colorScheme.onSurfaceVariantActions
        progress >= 0.95f -> StatusError
        progress >= 0.80f -> StatusWarning
        else -> MiuixTheme.colorScheme.primary
    }
    val tokens = usage?.contextTokens
    val window = usage?.contextWindow
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ui_contextual_usage_d12810),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = progress?.let { "${(it * 100f).roundToInt()}%" }
                    ?: stringResource(R.string.context_no_previous_usage),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(DrawerMetrics.InfoRowGap))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DrawerMetrics.ContextBarHeight)
                .clip(RoundedCornerShape(DrawerMetrics.ContextBarCornerRadius))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(DrawerMetrics.ContextBarCornerRadius))
                        .background(barColor),
                )
            }
        }
        if (tokens != null && window != null && window > 0) {
            Spacer(modifier = Modifier.height(DrawerMetrics.DockLabelGap))
            Text(
                text = "${formatCompactTokenCount(tokens)} / ${formatCompactTokenCount(window)}",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                maxLines = 1,
            )
        }
    }
}

/** 抽屉列表内的分区段头（无图标、无计数）。 */
@Composable
private fun PaneSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(
            top = DrawerMetrics.SectionTopPadding,
            bottom = DrawerMetrics.SectionBottomPadding,
        ),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.footnote1,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 底部 Dock 的语义分区标签。 */
@Composable
private fun PaneDockSectionLabel(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
        style = MiuixTheme.textStyles.footnote2,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

@Composable
private fun PaneActionBar(
    query: String,
    onSearchChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchBar(
            modifier = Modifier.weight(1f),
            expanded = false,
            onExpandedChange = {},
            inputField = {
                InputField(
                    query = query,
                    onQueryChange = onSearchChange,
                    onSearch = onSearchChange,
                    expanded = false,
                    onExpandedChange = {},
                    label = stringResource(R.string.conversation_search_hint),
                )
            },
            content = {},
        )
    }
}

@Composable
private fun ConversationSectionHeader(
    group: ConversationDrawerGroup,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = DrawerMetrics.SectionTopPadding,
                bottom = DrawerMetrics.SectionBottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            modifier = Modifier.size(DrawerMetrics.SectionIconSize),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Spacer(modifier = Modifier.width(DrawerMetrics.SectionIconGap))
        Text(
            text = group.localizedLabel(),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(DrawerMetrics.SectionCountGap))
        Text(
            text = group.items.size.toString(),
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationTextRow(
    conversation: ConversationSummaryUi,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DrawerMetrics.RowMinHeight)
                .clip(RoundedCornerShape(DrawerMetrics.RowCornerRadius))
                .background(
                    if (selected) {
                        MiuixTheme.colorScheme.surfaceContainerHigh
                    } else {
                        Color.Transparent
                    },
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showActionMenu = true
                    },
                )
                .padding(
                    horizontal = DrawerMetrics.RowHorizontalPadding,
                    vertical = DrawerMetrics.RowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
            val title = conversation.title.ifBlank { conversation.preview }
            Text(
                text = title,
                color = if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                style = MiuixTheme.textStyles.body1,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 标题与角色名相同（如未改名的角色会话）时不再重复第二行。
            conversation.characterName?.takeIf { it != title }?.let { name ->
                Text(name, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            }
            if (conversation.isActiveRun) {
                Box(
                    modifier = Modifier
                        .padding(start = DrawerMetrics.ActiveDotGap)
                        .size(DrawerMetrics.ActiveDotSize)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }

        // 问题2 例外（实测定案）：本弹窗所在面板 ConversationPanePanel 不在任何 Miuix
        // Scaffold 的 CompositionLocalProvider 子树内——它是 AgentAppShell 里 Scaffold 的兄弟节点。
        // 而 OverlayListPopup 依赖 Scaffold 注入的 LocalPopupStates，才能被 MiuixPopupHost 渲染
        // （MiuixPopupUtils.kt:266 取 LocalRootPopupStates ?: LocalPopupStates；:546 host 只渲染
        // LocalPopupStates）。改 Overlay 后 state 只会写进 staticCompositionLocalOf 的默认空列表，
        // 无 host 消费，会话操作菜单将完全不显示。故此处保留窗口级 WindowListPopup。
        WindowListPopup(
            show = showActionMenu,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            alignment = PopupPositionProvider.Align.BottomEnd,
            onDismissRequest = { showActionMenu = false },
        ) {
            val renameText = stringResource(R.string.action_rename)
            val exportText = stringResource(R.string.action_export)
            val deleteText = stringResource(R.string.action_delete)
            val renameItem = remember(renameText) {
                DropdownItem(
                    text = renameText,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = modifier.size(DrawerMetrics.ActionIconSize),
                        )
                    },
                )
            }
            val exportItem = remember(exportText) {
                DropdownItem(
                    text = exportText,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = modifier.size(DrawerMetrics.ActionIconSize),
                        )
                    },
                )
            }
            val deleteItem = remember(deleteText) {
                DropdownItem(
                    text = deleteText,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = modifier.size(DrawerMetrics.ActionIconSize),
                            tint = MiuixTheme.colorScheme.error,
                        )
                    },
                )
            }
            val deleteColors = DropdownDefaults.dropdownColors(
                contentColor = MiuixTheme.colorScheme.error,
                selectedContentColor = MiuixTheme.colorScheme.error,
                selectedIndicatorColor = MiuixTheme.colorScheme.error,
            )
            ListPopupColumn {
                DropdownImpl(
                    item = renameItem,
                    optionSize = 3,
                    isSelected = false,
                    index = 0,
                    onSelectedIndexChange = {
                        showActionMenu = false
                        onRename()
                    },
                )
                DropdownImpl(
                    item = exportItem,
                    optionSize = 3,
                    isSelected = false,
                    index = 1,
                    onSelectedIndexChange = {
                        showActionMenu = false
                        onExport()
                    },
                )
                DropdownImpl(
                    item = deleteItem,
                    optionSize = 3,
                    isSelected = false,
                    index = 2,
                    dropdownColors = deleteColors,
                    onSelectedIndexChange = {
                        showActionMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyConversations(isSearching: Boolean) {
    Text(
        text = stringResource(
            if (isSearching) R.string.conversation_no_results else R.string.conversation_empty,
        ),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(
            horizontal = DrawerMetrics.RowHorizontalPadding,
            vertical = DrawerMetrics.EmptyVerticalPadding,
        ),
    )
}

@Composable
private fun PaneDock(
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    // 语义分组：与抽屉顶部的「最近会话」一起构成抽屉的三段信息架构。
    // 入口数量与原来完全一致，仅按语义分到两行，不新增也不删减任何入口。
    Column(modifier = Modifier.fillMaxWidth()) {
        PaneDockSectionLabel(text = stringResource(R.string.drawer_section_workspace))
        Spacer(modifier = Modifier.height(DrawerMetrics.DockLabelGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DockEntry(
                icon = Icons.Rounded.Inventory2,
                label = "工具",
                onClick = onOpenTools,
                modifier = Modifier.weight(1f),
            )
            DockEntry(
                icon = Icons.Rounded.Extension,
                label = "Skills",
                onClick = onOpenSkills,
                modifier = Modifier.weight(1f),
            )
            DockEntry(
                icon = Icons.Rounded.TheaterComedy,
                label = "角色",
                onClick = onOpenCharacters,
                modifier = Modifier.weight(1f),
            )
            DockEntry(
                icon = Icons.Rounded.Lock,
                label = "权限",
                onClick = onOpenPermissions,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(DrawerMetrics.DockSectionGap))
        PaneDockSectionLabel(text = stringResource(R.string.drawer_section_system))
        Spacer(modifier = Modifier.height(DrawerMetrics.DockLabelGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DockEntry(
                icon = Icons.Rounded.Memory,
                label = "模型",
                onClick = onOpenModelProviders,
                modifier = Modifier.weight(1f),
            )
            DockEntry(
                icon = Icons.Rounded.Settings,
                label = "设置",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DockEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(DrawerMetrics.DockEntryCornerRadius))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(vertical = DrawerMetrics.DockEntryVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(DrawerMetrics.DockEntryIconSize),
            tint = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(DrawerMetrics.DockEntryLabelGap))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class ConversationDrawerGroup(
    val section: ConversationDrawerSection,
    val items: List<ConversationSummaryUi>,
)

private sealed interface ConversationDrawerSection {
    data object Pinned : ConversationDrawerSection
    data object Today : ConversationDrawerSection
    data class Dated(val label: String) : ConversationDrawerSection
}

@Composable
private fun ConversationDrawerGroup.localizedLabel(): String = when (val value = section) {
    ConversationDrawerSection.Pinned -> stringResource(R.string.conversation_section_pinned)
    ConversationDrawerSection.Today -> stringResource(R.string.conversation_section_today)
    is ConversationDrawerSection.Dated -> value.label
}

private fun List<ConversationSummaryUi>.groupForDrawer(): List<ConversationDrawerGroup> {
    if (isEmpty()) return emptyList()
    val groups = mutableListOf<ConversationDrawerGroup>()
    for (conversation in this) {
        val section = conversation.drawerSection()
        val last = groups.lastOrNull()
        if (last?.section == section) {
            groups[groups.lastIndex] = last.copy(items = last.items + conversation)
        } else {
            groups += ConversationDrawerGroup(section = section, items = listOf(conversation))
        }
    }
    return groups
}

private fun ConversationSummaryUi.drawerSection(): ConversationDrawerSection = when {
    isPinned -> ConversationDrawerSection.Pinned
    isActiveRun || isUpdatedToday(updatedAtMillis) -> ConversationDrawerSection.Today
    else -> ConversationDrawerSection.Dated(timeLabel)
}

private fun isUpdatedToday(timestampMillis: Long): Boolean {
    if (timestampMillis <= 0L) return true
    val now = java.util.Calendar.getInstance()
    val target = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
    return now.get(java.util.Calendar.ERA) == target.get(java.util.Calendar.ERA) &&
        now.get(java.util.Calendar.YEAR) == target.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == target.get(java.util.Calendar.DAY_OF_YEAR)
}
