package io.github.mangi.eta.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.data.db.TaskAttachment
import io.github.mangi.eta.ui.model.TaskItemUi
import io.github.mangi.eta.ui.model.TaskQueueUiState
import io.github.mangi.eta.ui.model.TaskStatusUi
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import io.github.mangi.eta.ui.app.LocalSiriStage
import io.github.mangi.eta.ui.theme.siriGlassSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 问题3：任务栏胶囊图标尺寸，与浮窗按钮图标（15dp）保持一致。 */
private val TaskCapsuleIconSize = 15.dp
/** 问题1：任务栏胶囊高度，与浮窗控制按钮/胶囊（32dp）统一。 */
private val TaskCapsuleHeight = 32.dp

/**
 * 统一任务状态胶囊徽章（本体与悬浮窗完全同源设计）
 */
@Composable
fun TaskStatusCapsuleBadge(
    state: TaskQueueUiState,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSiriStyle = LocalSiriStage.current
    val siriDark = isSystemInDarkTheme()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .then(
                if (isSiriStyle) {
                    // 问题3：任务栏胶囊与浮窗按钮同源——液态玻璃面 + 渐变描边。
                    Modifier.siriGlassSurface(
                        shape = RoundedCornerShape(percent = 50),
                        isDark = siriDark,
                        refractionAlpha = 0.8f,
                    )
                } else {
                    Modifier.background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                },
            )
            .clickable(onClick = onClick)
            // 问题1：与浮窗按钮/胶囊统一为 32dp 高度。此前五套并存——
            // 浮窗控制按钮 32dp、浮窗胶囊 34dp、屏幕上下文移除钮 30dp、
            // 本胶囊约 31dp（图标 15dp + 上下各 8dp）。改用 heightIn 锁高而非
            // padding 累加，图标与文字行高的变化不再引入偏差。
            .heightIn(min = TaskCapsuleHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.runningCount > 0) {
            InfiniteProgressIndicator(
                // 问题3：与浮窗按钮图标（15dp）量级对齐。
                modifier = Modifier.size(TaskCapsuleIconSize),
                color = MiuixTheme.colorScheme.primary,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state.completedCount > 0) StatusSuccess else StatusIdle),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (state.totalCount == 0) "📋 任务队列" else "任务 ${state.totalCount}",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        if (state.totalCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "✓${state.completedCount}",
                style = MiuixTheme.textStyles.footnote2,
                color = StatusSuccess,
            )
            if (state.runningCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "⟳${state.runningCount}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = StatusRunning,
                )
            }
            if (state.failedCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "⚠${state.failedCount}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = StatusError,
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            // 问题3：与浮窗按钮图标量级对齐。
            modifier = Modifier.size(TaskCapsuleIconSize),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

/**
 * 统一任务卡片组件（本体与浮窗弹框共用）
 */
@Composable
fun TaskCardItem(
    task: TaskItemUi,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = {
            if (onClick != null) onClick() else expanded = !expanded
        },
    ) {
        Column(
            modifier = Modifier
                .padding(if (isCompact) 12.dp else 16.dp)
                .animateContentSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskStatusDot(status = task.status)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = task.title,
                    modifier = Modifier.weight(1f),
                    style = if (isCompact) MiuixTheme.textStyles.body2 else MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                TaskStatusTag(status = task.status)
                if (onDelete != null && task.status == TaskStatusUi.Pending) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "删除任务",
                            modifier = Modifier.size(16.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                }
            }

            // 附件缩略图（图片内联解码，文件以图标占位）
            if (task.hasAttachments) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    task.attachments.forEach { attachment ->
                        TaskAttachmentThumbnail(attachment)
                    }
                }
            }

            // 失败任务的操作：原样重试 + 编辑指令
            if (task.status == TaskStatusUi.Failed && (onRetry != null || onEdit != null)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onRetry?.let { retry ->
                        TextButton(
                            text = "重试",
                            onClick = retry,
                            minHeight = 30.dp,
                        )
                    }
                    onEdit?.let { edit ->
                        TextButton(
                            text = "编辑",
                            onClick = edit,
                            minHeight = 30.dp,
                        )
                    }
                }
            }

            // 展开或非紧凑模式下的详情
            if (expanded || !isCompact) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = task.prompt,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = if (expanded) 8 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 失败原因或执行结果产物
            if (!task.failReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = StatusError,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.failReason,
                        style = MiuixTheme.textStyles.footnote2,
                        color = StatusError,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (!task.outputSummary.isNullOrBlank() && expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "执行产物：${task.outputSummary}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TaskStatusDot(status: TaskStatusUi) {
    when (status) {
        TaskStatusUi.Running -> InfiniteProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = MiuixTheme.colorScheme.primary,
        )
        TaskStatusUi.Completed -> Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = StatusSuccess,
        )
        TaskStatusUi.Failed -> Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = StatusError,
        )
        TaskStatusUi.Pending -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(StatusIdle),
        )
        TaskStatusUi.Skipped -> Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = StatusWarning,
        )
    }
}

@Composable
private fun TaskStatusTag(status: TaskStatusUi) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(status.color().copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = status.label(),
            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp),
            color = status.color(),
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 任务附件缩略图：图片内联解码，文件用图标占位并显示文件名。 */
@Composable
private fun TaskAttachmentThumbnail(attachment: TaskAttachment) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (attachment.kind == TaskAttachment.KIND_IMAGE) {
            val bitmap = rememberDataUrlBitmap(attachment.value)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "图片附件",
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = "图片附件",
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = "文件附件",
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

/**
 * 统一追加指令输入框（Steering Input Box）
 */
@Composable
fun TaskSteeringInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "追加补充指令 (不打断当前工作)...",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = MiuixTheme.textStyles.body2.copy(
                color = MiuixTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (value.isNotBlank()) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.2f)
                )
                .clickable(enabled = value.isNotBlank(), onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "发送追加指令",
                modifier = Modifier.size(16.dp),
                tint = if (value.isNotBlank()) Color.White else MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

/**
 * 统一三合一任务面板（Tri-Tab Task Dashboard）
 * 供悬浮窗抽屉和主 APP 本体完全复用，消弭割裂感
 */
@Composable
fun TaskTriTabDashboard(
    state: TaskQueueUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    steeringInput: String,
    onSteeringInputChange: (String) -> Unit,
    onSendSteering: () -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    isFloatingOverlay: Boolean = false,
    onRetryTask: (String) -> Unit = {},
    onEditTask: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFloatingOverlay) MiuixTheme.colorScheme.surface.copy(alpha = 0.92f)
                else MiuixTheme.colorScheme.surface
            )
            .padding(14.dp),
    ) {
        // Tab 导航栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(
                "📋 未来 (${state.pendingCount})",
                "✏️ 当前",
                "📜 历史 (${state.historyTasks.size})",
            ).forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab 内容呈现
        when (selectedTab) {
            0 -> {
                // 未来任务队列
                if (state.pendingTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无排队中的待执行任务",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (isFloatingOverlay) 160.dp else 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.pendingTasks.forEach { task ->
                            TaskCardItem(
                                task = task,
                                isCompact = true,
                                onDelete = { onDeleteTask(task.taskId) },
                            )
                        }
                    }
                }
            }
            1 -> {
                // 当前正在执行的任务与实时追加
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isFloatingOverlay) 180.dp else 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.activeTask != null) {
                        TaskCardItem(
                            task = state.activeTask,
                            isCompact = true,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "当前无正在执行的任务",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TaskSteeringInputBox(
                        value = steeringInput,
                        onValueChange = onSteeringInputChange,
                        onSend = onSendSteering,
                    )
                }
            }
            2 -> {
                // 历史已完成归档
                if (state.historyTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无已完成的历史任务",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (isFloatingOverlay) 160.dp else 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.historyTasks.forEach { task ->
                            TaskCardItem(
                                task = task,
                                isCompact = true,
                                onDelete = { onDeleteTask(task.taskId) },
                                onRetry = if (task.status == TaskStatusUi.Failed) {
                                    { onRetryTask(task.taskId) }
                                } else null,
                                onEdit = if (task.status == TaskStatusUi.Failed) {
                                    { onEditTask(task.taskId) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }
}