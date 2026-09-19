package io.github.mangi.eta.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardCommandKey
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.theme.EtaRadius
import io.github.mangi.eta.ui.theme.EtaSize
import io.github.mangi.eta.ui.theme.EtaSpacing
import io.github.mangi.eta.ui.theme.EtaStroke
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 输入栏「工作台工具箱」底部面板。
 *
 * 参考基准：NEXUS Agent `lib/presentation/widgets/input_tool_grid_sheet.dart`
 * ——底部弹层 + 4 列宫格（图标容器 / 标题 / 副标题两行）。
 *
 * 收纳原则：**只收纳 App 中已真实存在的能力入口，不新增任何功能**。
 * 面板仅改变这些入口的呈现层级，点击后走的仍是既有回调与既有路由。
 *
 * 面板内容分两组，各 4 项，正好 4 列 × 2 行：
 * - 输入组：图片 / 文件 / 文件夹 / 输入路径（复用输入栏原有的四个附件入口）
 * - 能力组：工具能力 / Skills / 终端 / MCP 服务器（跳转既有 [AppRoute]）
 *
 * 功耗约束：面板内不含任何常驻动画，图标与文字均为静态渲染。
 */

/** 宫格图标容器边长（对齐参考项目的 48dp 图标容器）。 */
private val InputToolIconContainerSize = 48.dp

/** 宫格图标字形尺寸。 */
private val InputToolIconSize = 24.dp

/** 宫格标题与副标题之间的行距。 */
private val InputToolTitleGap = 2.dp

/** 宫格列数（对齐参考项目的 4 列）。 */
private const val InputToolPanelColumns = 4

private data class InputToolPanelEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
internal fun AgentInputToolPanel(
    show: Boolean,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onPickPath: () -> Unit,
    onOpenRoute: (AppRoute) -> Unit,
) {
    val entries = listOf(
        InputToolPanelEntry(
            icon = Icons.Rounded.Image,
            title = stringResource(R.string.attachment_image),
            subtitle = stringResource(R.string.input_tool_panel_subtitle_image),
            onClick = onPickImage,
        ),
        InputToolPanelEntry(
            icon = Icons.Rounded.Description,
            title = stringResource(R.string.attachment_file),
            subtitle = stringResource(R.string.input_tool_panel_subtitle_file),
            onClick = onPickFiles,
        ),
        InputToolPanelEntry(
            icon = Icons.Rounded.FolderOpen,
            title = stringResource(R.string.attachment_folder),
            subtitle = stringResource(R.string.input_tool_panel_subtitle_folder),
            onClick = onPickFolder,
        ),
        InputToolPanelEntry(
            icon = Icons.Rounded.KeyboardCommandKey,
            title = stringResource(R.string.attachment_enter_path),
            subtitle = stringResource(R.string.input_tool_panel_subtitle_path),
            onClick = onPickPath,
        ),
        InputToolPanelEntry(
            icon = Icons.Rounded.Inventory2,
            title = stringResource(R.string.route_tools),
            subtitle = stringResource(R.string.input_tool_panel_subtitle_tools),
            onClick = { onOpenRoute(AppRoute.Tools) },
        ),
        InputToolPanelEntry(
            icon = Icons.Rounded.Extension,
            title = stringResource(R.string.route_skills),
            subtitle = stringResource(R.string.input_tool_panel_subtitle_skills),
            onClick = { onOpenRoute(AppRoute.Skills) },
        ),
        InputToolPanelEntry(
            icon = Icons.Rounded.Terminal,
            title = stringResource(R.string.route_terminal),
            subtitle = stringResource(R.string.input_tool_panel_subtitle_terminal),
            onClick = { onOpenRoute(AppRoute.Terminal) },
        ),
        InputToolPanelEntry(
            icon = Icons.Rounded.Dns,
            title = stringResource(R.string.route_mcp_servers),
            subtitle = stringResource(R.string.input_tool_panel_subtitle_mcp),
            onClick = { onOpenRoute(AppRoute.McpServers) },
        ),
    )

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.input_tool_panel_title),
        endAction = {
            IconButton(
                onClick = onDismiss,
                minWidth = EtaSize.control,
                minHeight = EtaSize.control,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    modifier = Modifier.size(ChatInputActionIconSize),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
        cornerRadius = EtaRadius.modal,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = EtaSpacing.sm, bottom = EtaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaSpacing.lg),
        ) {
            entries.chunked(InputToolPanelColumns).forEach { rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EtaSpacing.sm),
                ) {
                    rowEntries.forEach { entry ->
                        InputToolGridEntry(
                            icon = entry.icon,
                            title = entry.title,
                            subtitle = entry.subtitle,
                            onClick = {
                                onDismiss()
                                entry.onClick()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InputToolGridEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(EtaRadius.card))
            .clickable(onClickLabel = title, onClick = onClick)
            .padding(vertical = EtaSpacing.sm, horizontal = EtaSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(InputToolIconContainerSize)
                .squircleSurface(
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    cornerRadius = EtaRadius.control,
                )
                .squircleBorder(
                    width = EtaStroke.hairline,
                    color = MiuixTheme.colorScheme.outline,
                    cornerRadius = EtaRadius.control,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(InputToolIconSize),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(EtaSpacing.sm))
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(InputToolTitleGap))
        Text(
            text = subtitle,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
