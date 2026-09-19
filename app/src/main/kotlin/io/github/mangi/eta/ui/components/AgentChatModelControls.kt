package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.app.LocalSiriStage
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.defaultExpandedModelProviderIds
import io.github.mangi.eta.ui.model.formatContextUsage
import io.github.mangi.eta.ui.theme.siriGlassSurface
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.RichTooltip
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AgentModelPickerButton(
    state: AgentModelPickerUiState,
    isStreaming: Boolean,
    popupAnchorTopPx: Int,
    popupMaxHeight: Dp,
    onModelSelected: (String) -> Unit,
    onOpenModelProviders: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showPopup by remember { mutableStateOf(false) }
    var expandedProviderIds by remember { mutableStateOf(emptySet<String>()) }
    val selected = state.selectedModel
    val isSiriStyle = LocalSiriStage.current
    val enabled = !isStreaming && !state.isChanging
    LaunchedEffect(enabled) {
        if (!enabled) showPopup = false
    }
    val density = LocalDensity.current
    val popupWindowInsetPx = with(density) { 14.dp.roundToPx() }
    val popupPositionProvider = remember(popupAnchorTopPx, popupWindowInsetPx) {
        InputPopupPositionProvider(
            inputContainerTopPx = popupAnchorTopPx,
            windowHorizontalInsetPx = popupWindowInsetPx,
        )
    }
    val currentModel = selected?.displayName ?: stringResource(R.string.model_not_selected)
    val switchModelDescription = stringResource(R.string.model_switch_current, currentModel)
    Box(modifier = modifier) {
        IconButton(
            onClick = {
                expandedProviderIds = defaultExpandedModelProviderIds(state.selectedModel)
                showPopup = true
            },
            enabled = enabled,
            minWidth = ChatInputActionSize,
            minHeight = ChatInputActionSize,
            modifier = Modifier.semantics {
                contentDescription = switchModelDescription
            },
        ) {
            if (isSiriStyle) {
                // 设计稿一比一：Siri 舞台模型选择器为文字胶囊（品牌 logo + 模型名 + chevron）。
                // 点击开合仍由 IconButton 承担；弹窗内容与定位零改动。
                Row(
                    modifier = Modifier
                        .widthIn(max = 168.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.surface)
                        .border(
                            width = 0.5.dp,
                            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModelBrandMark(
                        modelId = selected?.modelId,
                        sourceType = selected?.providerSourceType,
                        size = 20.dp,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = selected?.displayName ?: stringResource(R.string.model_not_selected),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            } else {
                ModelBrandMark(
                    modelId = selected?.modelId,
                    sourceType = selected?.providerSourceType,
                    size = ChatInputActionIconSize,
                )
            }
        }

        OverlayListPopup(
            show = showPopup && popupAnchorTopPx > 0,
            popupPositionProvider = popupPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { showPopup = false },
            maxHeight = popupMaxHeight,
            minWidth = 236.dp,
        ) {
            ModelPickerPopupContent(
                state = state,
                expandedProviderIds = expandedProviderIds,
                onProviderExpandedChange = { providerId, expanded ->
                    expandedProviderIds = if (expanded) {
                        expandedProviderIds + providerId
                    } else {
                        expandedProviderIds - providerId
                    }
                },
                onModelSelected = { modelId ->
                    showPopup = false
                    onModelSelected(modelId)
                },
                onOpenModelProviders = {
                    showPopup = false
                    onOpenModelProviders()
                },
            )
        }
    }
}

@Composable
private fun ModelPickerPopupContent(
    state: AgentModelPickerUiState,
    expandedProviderIds: Set<String>,
    onProviderExpandedChange: (String, Boolean) -> Unit,
    onModelSelected: (String) -> Unit,
    onOpenModelProviders: () -> Unit,
) {
    ListPopupColumn {
        state.providerGroups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
            }
            val expanded = group.providerId in expandedProviderIds
            ModelProviderGroupHeader(
                name = group.providerName,
                expanded = expanded,
                onClick = {
                    onProviderExpandedChange(group.providerId, !expanded)
                },
            )
            if (expanded) {
                group.models.forEach { model ->
                    ModelPickerRow(
                        model = model,
                        selected = model.id == state.selectedModel?.id,
                        onClick = { onModelSelected(model.id) },
                    )
                }
            }
        }
        if (state.providerGroups.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        }
        ModelProviderManagementRow(
            onClick = onOpenModelProviders,
        )
    }
}

@Composable
private fun ModelProviderManagementRow(
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .squircleSurface(
                color = Color.Transparent,
                cornerRadius = 12.dp,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.action_edit_model_providers),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

@Composable
private fun ModelProviderGroupHeader(
    name: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "model_provider_arrow",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) {
                stringResource(R.string.model_collapse_provider, name)
            } else {
                stringResource(R.string.model_expand_provider, name)
            },
            modifier = Modifier
                .size(15.dp)
                .graphicsLayer { rotationZ = arrowRotation },
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

@Composable
private fun ModelPickerRow(
    model: AgentModelOptionUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .squircleSurface(
                color = if (selected) {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                },
                cornerRadius = 12.dp,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = model.displayName,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.ui_current_model_a0af8f),
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
internal fun AgentContextUsageButton(
    usage: AgentContextUsageUi,
    onCompact: () -> Unit = {},
    canCompact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)
    val isSiriStyle = LocalSiriStage.current
    val siriDark = isSystemInDarkTheme()
    val progress = usage.progress
    val progressColor = when {
        progress == null -> MiuixTheme.colorScheme.onSurfaceVariantActions
        progress >= 0.95f -> StatusError
        progress >= 0.80f -> StatusWarning
        else -> MiuixTheme.colorScheme.primary
    }
    val locale = LocalConfiguration.current.locales[0]
    val summary = formatContextUsage(
        usage = usage,
        noUsageText = stringResource(R.string.context_no_previous_usage),
        noLimitText = stringResource(R.string.context_no_model_limit),
        locale = locale,
    )
    val detail = when {
        usage.estimated -> stringResource(R.string.context_usage_estimated, summary)
        usage.contextTokens == null -> stringResource(R.string.context_usage_after_response)
        else -> stringResource(R.string.context_usage_previous_response, summary)
    }
    val usageDescription = stringResource(
        R.string.context_usage_description,
        summary.replace('\n', ' '),
    )
    val tooltipColors = TooltipDefaults.richTooltipColors()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            RichTooltip(
                title = {
                    Text(
                        text = stringResource(R.string.ui_contextual_usage_d12810),
                        color = tooltipColors.titleContentColor,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                },
                action = if (canCompact) {
                    {
                        TextButton(
                            text = stringResource(R.string.context_compact_action),
                            onClick = {
                                onCompact()
                                tooltipState.dismiss()
                            },
                            minWidth = 0.dp,
                            minHeight = 34.dp,
                            cornerRadius = 17.dp,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    null
                },
                colors = tooltipColors,
            ) {
                Text(
                    text = detail,
                    color = tooltipColors.contentColor,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        },
        state = tooltipState,
        focusable = true,
        modifier = modifier,
    ) {
        IconButton(
            onClick = { scope.launch { tooltipState.show() } },
            minWidth = ChatInputActionSize,
            minHeight = ChatInputActionSize,
            modifier = if (isSiriStyle) {
                Modifier.siriGlassSurface(
                    shape = CircleShape,
                    isDark = siriDark,
                    refractionAlpha = 0.7f,
                )
            } else {
                Modifier
            },
        ) {
            CircularProgressIndicator(
                progress = progress ?: 0f,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = progressColor,
                    disabledForegroundColor = progressColor,
                    backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                ),
                strokeWidth = 2.5.dp,
                size = ChatInputActionIconSize,
                modifier = Modifier.semantics {
                    contentDescription = usageDescription
                },
            )
        }
    }
}

@Composable
private fun ModelBrandMark(
    modelId: String?,
    sourceType: String?,
    size: Dp,
) {
    val logo = modelOrProviderBrandLogoRes(modelId, sourceType)
    if (logo != null) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(MiuixTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Dns,
                contentDescription = null,
                modifier = Modifier.size(size * 0.56f),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}
