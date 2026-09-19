package io.github.mangi.eta.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.model.AgentFileReference
import io.github.mangi.eta.agent.model.AgentFileReferenceKind
import io.github.mangi.eta.ui.app.LocalSiriStage
import io.github.mangi.eta.ui.model.PendingFileReferenceUi
import io.github.mangi.eta.ui.theme.siriGlassSurface
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup

internal val ChatInputPopupMargin = 8.dp
/** 输入栏操作按钮统一可见尺寸：32dp。IconButton 的 minWidth/minHeight 是点击区，
 *  SIRI 模式玻璃面画在按钮边界上，故点击区即可见圆钮直径。 */
internal val ChatInputActionSize = 32.dp
/** 图标/可见圆钮尺寸：与 ChatInputActionSize 一致，保证输入栏所有圆钮直径相同。 */
internal val ChatInputActionIconSize = 32.dp
/** 图标字形尺寸：24dp。
 *
 *  修复点：此前图标尺寸与圆钮尺寸同为 32dp，图标顶满圆钮、四周零留白。
 *  现在图标字形独立收敛到本常量，圆钮直径仍为 [ChatInputActionSize]（32dp）不变，
 *  因此输入栏所有圆钮可见尺寸依旧统一，只是图标恢复了 4dp 内边距。
 */
internal val ChatInputActionGlyphSize = 24.dp

/**
 * 附件选择器启动句柄。
 *
 * 输入栏「+」按钮的弹出菜单与工具箱面板共用同一套选择逻辑与回退路径，
 * 避免出现两份行为可能走样的实现。
 */
internal class AttachmentPickerLaunchers(
    val pickImage: () -> Unit,
    val pickFiles: () -> Unit,
    val pickFolder: () -> Unit,
)

/**
 * 创建附件选择器启动句柄。
 *
 * 行为与改造前 [AgentAttachmentPickerButton] 内部完全一致：
 * 存在 ActivityResultRegistry 时走 Compose 选择器，否则回退到 Trampoline Activity。
 */
@Composable
internal fun rememberAttachmentPickerLaunchers(
    onAttachImage: (String) -> Unit,
    onAttachFiles: (List<String>) -> Unit,
    onAttachFolder: (String) -> Unit,
): AttachmentPickerLaunchers {
    val context = LocalContext.current
    val registryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current

    val photoPicker = if (registryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(),
        ) { uris ->
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                onAttachImage(uri.toString())
            }
        }
    } else null

    val filePicker = if (registryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            if (uris.isNotEmpty()) onAttachFiles(uris.map { it.toString() })
        }
    } else null

    val folderPicker = if (registryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                onAttachFolder(uri.toString())
            }
        }
    } else null

    return AttachmentPickerLaunchers(
        pickImage = {
            if (photoPicker != null) {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            } else {
                AgentAttachmentPickerTrampolineActivity.pickImages(context) { uris ->
                    uris.forEach { onAttachImage(it) }
                }
            }
        },
        pickFiles = {
            if (filePicker != null) {
                filePicker.launch(arrayOf("*/*"))
            } else {
                AgentAttachmentPickerTrampolineActivity.pickFiles(context) { uris ->
                    onAttachFiles(uris)
                }
            }
        },
        pickFolder = {
            if (folderPicker != null) {
                folderPicker.launch(null)
            } else {
                AgentAttachmentPickerTrampolineActivity.pickFolder(context) { uri ->
                    onAttachFolder(uri)
                }
            }
        },
    )
}

/**
 * 「输入路径」对话框：输入栏「+」弹出菜单与工具箱面板共用。
 */
@Composable
internal fun AgentAttachmentPathDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pathInput by remember { mutableStateOf("") }
    LaunchedEffect(show) {
        if (show) pathInput = ""
    }
    OverlayDialog(
        show = show,
        title = stringResource(R.string.ui_input_file_path_36d474),
        summary = stringResource(R.string.ui_supports_files_and_folders_under_internal_storage_or_520786),
        onDismissRequest = onDismiss,
    ) {
        Column {
            TextField(
                value = pathInput,
                onValueChange = { pathInput = it },
                label = stringResource(R.string.ui_absolute_path_9ac6fc),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            MiuixDialogActions(
                confirmText = stringResource(R.string.attachment_add),
                confirmEnabled = pathInput.trim().startsWith('/'),
                onCancel = onDismiss,
                onConfirm = {
                    val path = pathInput.trim()
                    onDismiss()
                    onConfirm(path)
                },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
internal fun AgentAttachmentPickerButton(
    launchers: AttachmentPickerLaunchers,
    popupAnchorTopPx: Int,
    popupMaxHeight: Dp,
    onAttachFilePath: (String) -> Unit,
    onOpenToolPanel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isSiriStyle = LocalSiriStage.current
    val siriDark = isSystemInDarkTheme()
    var showPopup by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                // 主界面/对话页：打开工具箱面板；浮窗（未提供 onOpenToolPanel）：保持原有列表菜单。
                if (onOpenToolPanel != null) onOpenToolPanel() else showPopup = true
            },
            minWidth = ChatInputActionSize,
            minHeight = ChatInputActionSize,
            modifier = if (isSiriStyle) {
                // Apple Intelligence 输入栏左端：圆形液态玻璃「+」。
                Modifier.siriGlassSurface(
                    shape = CircleShape,
                    isDark = siriDark,
                    refractionAlpha = 0.8f,
                )
            } else {
                Modifier
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.ui_add_attachment_dba9e8),
                modifier = Modifier.size(ChatInputActionGlyphSize),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
        OverlayListPopup(
            show = showPopup && popupAnchorTopPx > 0,
            popupPositionProvider = remember(popupAnchorTopPx) {
                InputPopupPositionProvider(popupAnchorTopPx)
            },
            alignment = PopupPositionProvider.Align.TopStart,
            onDismissRequest = { showPopup = false },
            maxHeight = popupMaxHeight,
        ) {
            val dismiss = LocalDismissState.current
            val options = listOf(
                stringResource(R.string.attachment_image),
                stringResource(R.string.attachment_file),
                stringResource(R.string.attachment_folder),
                stringResource(R.string.attachment_enter_path),
            )
            ListPopupColumn {
                options.forEachIndexed { index, option ->
                    DropdownImpl(
                        text = option,
                        optionSize = options.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            when (index) {
                                0 -> launchers.pickImage()
                                1 -> launchers.pickFiles()
                                2 -> launchers.pickFolder()
                                3 -> showPathDialog = true
                            }
                        },
                    )
                }
            }
        }
    }

    AgentAttachmentPathDialog(
        show = showPathDialog,
        onDismiss = { showPathDialog = false },
        onConfirm = onAttachFilePath,
    )
}

@Composable
internal fun PendingFileReferenceStrip(
    references: List<PendingFileReferenceUi>,
    onRemoveReference: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        references.forEach { pending ->
            val reference = pending.reference
            Row(
                modifier = Modifier
                    .height(42.dp)
                    .widthIn(max = 250.dp)
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        cornerRadius = 14.dp,
                    )
                    .squircleBorder(
                        width = 0.5.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
                        cornerRadius = 14.dp,
                    )
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (reference.kind == AgentFileReferenceKind.Directory) {
                        Icons.Rounded.FolderOpen
                    } else {
                        Icons.Rounded.Description
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = reference.displayName +
                        if (reference.kind == AgentFileReferenceKind.Directory) "/" else "",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onRemoveReference(pending.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_remove_file_reference_04bbfc),
                        modifier = Modifier.size(15.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SentFileReferenceFlow(
    references: List<AgentFileReference>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        references.forEach { reference ->
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .widthIn(max = 280.dp)
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.surface,
                        cornerRadius = 12.dp,
                    )
                    .squircleBorder(
                        width = 0.5.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.45f),
                        cornerRadius = 12.dp,
                    )
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (reference.kind == AgentFileReferenceKind.Directory) {
                        Icons.Rounded.FolderOpen
                    } else {
                        Icons.Rounded.Description
                    },
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = reference.displayName +
                        if (reference.kind == AgentFileReferenceKind.Directory) "/" else "",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal class InputPopupPositionProvider(
    private val inputContainerTopPx: Int,
    private val windowHorizontalInsetPx: Int? = null,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: PopupPositionProvider.Align,
    ): IntOffset {
        val alignToEnd = when (alignment) {
            PopupPositionProvider.Align.End,
            PopupPositionProvider.Align.TopEnd,
            PopupPositionProvider.Align.BottomEnd,
            -> true

            else -> false
        }
        val physicalEnd = if (layoutDirection == LayoutDirection.Ltr) alignToEnd else !alignToEnd
        val requestedX = when {
            windowHorizontalInsetPx != null && physicalEnd ->
                windowBounds.right - popupContentSize.width - popupMargin.right - windowHorizontalInsetPx

            windowHorizontalInsetPx != null ->
                windowBounds.left + popupMargin.left + windowHorizontalInsetPx

            physicalEnd -> anchorBounds.right - popupContentSize.width - popupMargin.right
            else -> anchorBounds.left + popupMargin.left
        }
        val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right)
            .coerceAtLeast(windowBounds.left)
        val requestedY = inputContainerTopPx - popupContentSize.height - popupMargin.bottom
        val maxY = windowBounds.bottom - popupContentSize.height - popupMargin.bottom
        val minY = (windowBounds.top + popupMargin.top).coerceAtMost(maxY)
        return IntOffset(
            x = requestedX.coerceIn(windowBounds.left, maxX),
            y = requestedY.coerceIn(minY, maxY),
        )
    }

    override fun getMargins(): PaddingValues = PaddingValues(vertical = ChatInputPopupMargin)
}
