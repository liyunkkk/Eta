package io.github.mangi.eta.ui.screens.memory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.memory.MemoryCard
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffold
import io.github.mangi.eta.ui.layout.horizontalCutoutPadding
import io.github.mangi.eta.ui.model.AgentMemoryAction
import io.github.mangi.eta.ui.model.AgentMemoryUiState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AgentMemoryScreen(
    state: AgentMemoryUiState,
    onAction: (AgentMemoryAction) -> Unit,
) {
    val context = LocalContext.current
    var showClearMdDialog by remember { mutableStateOf(false) }
    var showClearCardsDialog by remember { mutableStateOf(false) }
    var deleteTargetCardId by remember { mutableStateOf<String?>(null) }
    var editDialogCard by remember { mutableStateOf<MemoryCard?>(null) }
    var isNewCardDialog by remember { mutableStateOf(false) }

    // SAF 导入 JSON 备份
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val text = stream.bufferedReader(Charsets.UTF_8).readText()
                    onAction(AgentMemoryAction.ImportJson(text))
                }
            } catch (_: Exception) {}
        }
    }

    // SAF 导出 JSON 备份
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val jsonText = io.github.mangi.eta.data.repository.StructuredMemoryRepository(context).exportJson()
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(jsonText.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {}
        }
    }

    MiuixScaffold(
        title = stringResource(R.string.ui_memory_b55ff5),
        onBack = { onAction(AgentMemoryAction.NavigateBack) },
    ) { paddingValues, scrollBehavior, sidePadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = sidePadding,
                    end = sidePadding,
                ),
                overscrollEffect = null,
            ) {
                item(key = "status-title") { SmallTitle(stringResource(R.string.ui_memory_b55ff5)) }
                item(key = "status-card") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.ui_enable_memory_4b69b7),
                            summary = stringResource(R.string.ui_after_closing_no_memory_will_be_injected_and_the_mod_db3d23),
                            checked = state.enabled,
                            enabled = !state.isLoading,
                            onCheckedChange = { onAction(AgentMemoryAction.ToggleEnabled(it)) },
                        )
                        BasicComponent(
                            title = stringResource(R.string.ui_core_memory_injection_budget_48b5d5),
                            summary = stringResource(R.string.memory_budget_summary, formatNumber(state.coreBudgetChars)),
                        )
                    }
                }

                // ── 结构化记忆卡片管理中枢 ──────────────────────────
                item(key = "cards-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SmallTitle("结构化记忆库 (${state.cards.size})")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    isNewCardDialog = true
                                    editDialogCard = MemoryCard(title = "", content = "", space = if (state.selectedSpace != "全部") state.selectedSpace else "通用")
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "新增记忆",
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            }
                            IconButton(
                                onClick = { importLauncher.launch("application/json") },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileDownload,
                                    contentDescription = "导入备份",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = {
                                    val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    exportLauncher.launch("eta_memories_$time.json")
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileUpload,
                                    contentDescription = "导出备份",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            if (state.cards.isNotEmpty()) {
                                IconButton(
                                    onClick = { showClearCardsDialog = true },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = "清空所有卡片",
                                        tint = MiuixTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 搜索框 ──────────────────────────────────────
                item(key = "search-bar") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { onAction(AgentMemoryAction.SearchQueryChanged(it)) },
                            label = "搜索记忆（标题、正文或标签）",
                            useLabelAsPlaceholder = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "搜索",
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(18.dp),
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onAction(AgentMemoryAction.SearchQueryChanged("")) },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = "清空搜索",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ── 空间分类药丸选择器 (Horizontal Filter Chips) ──────
                item(key = "space-chips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.spaces.forEach { space ->
                            val isSelected = space == state.selectedSpace
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        if (isSelected) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.surface
                                    )
                                    .clickable { onAction(AgentMemoryAction.SelectSpace(space)) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = space,
                                    style = MiuixTheme.textStyles.footnote1.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) MiuixTheme.colorScheme.onPrimary
                                    else MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // ── 记忆卡片列表 ────────────────────────────────
                val filtered = state.filteredCards
                if (filtered.isEmpty()) {
                    item(key = "empty-cards") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = if (state.searchQuery.isNotEmpty()) "未找到匹配的记忆卡片" else "当前空间暂无记忆卡片",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    text = "+ 新增第一条记忆",
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    onClick = {
                                        isNewCardDialog = true
                                        editDialogCard = MemoryCard(title = "", content = "", space = if (state.selectedSpace != "全部") state.selectedSpace else "通用")
                                    },
                                )
                            }
                        }
                    }
                } else {
                    items(filtered.size, key = { filtered[it].id }) { idx ->
                        val card = filtered[idx]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = card.space,
                                                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                        }
                                        Text(
                                            text = card.title,
                                            style = MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        // 重要度星星
                                        repeat(card.importance) {
                                            Icon(
                                                imageVector = Icons.Rounded.Star,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = Color(0xFFFFB300),
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                isNewCardDialog = false
                                                editDialogCard = card
                                            },
                                            minWidth = 28.dp,
                                            minHeight = 28.dp,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "编辑卡片",
                                                modifier = Modifier.size(15.dp),
                                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                        IconButton(
                                            onClick = { deleteTargetCardId = card.id },
                                            minWidth = 28.dp,
                                            minHeight = 28.dp,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "删除卡片",
                                                modifier = Modifier.size(15.dp),
                                                tint = MiuixTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = card.content,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )

                                if (card.tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    ) {
                                        card.tags.forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MiuixTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = "#$tag",
                                                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── MEMORY.md 自由文本底栏 ──────────────────────────
            Column(
                modifier = Modifier
                    .padding(horizontal = sidePadding)
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                SmallTitle("全局准则 (MEMORY.md)")
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TextField(
                            value = state.draft,
                            onValueChange = { onAction(AgentMemoryAction.DraftChanged(it)) },
                            label = stringResource(R.string.ui_core_memory_user_name_long_term_preferences_aa6ff9),
                            useLabelAsPlaceholder = true,
                            enabled = !state.isLoading && !state.isSaving,
                            minLines = 4,
                            maxLines = 8,
                            textStyle = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val overLimit = state.draftBytes > state.maxBytes
                            Text(
                                text = when {
                                    overLimit -> stringResource(R.string.memory_over_limit)
                                    state.hasUnsavedChanges -> stringResource(R.string.memory_unsaved_changes)
                                    else -> ""
                                },
                                color = if (overLimit) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                            Text(
                                text = "${formatBytes(state.draftBytes)} / 1 MiB",
                                color = if (overLimit) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                text = stringResource(R.string.ui_clear_84fcd7),
                                enabled = !state.isLoading && !state.isSaving && state.draft.isNotEmpty(),
                                onClick = { showClearMdDialog = true },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = if (state.isSaving) stringResource(R.string.memory_saving) else stringResource(R.string.memory_save),
                                enabled = state.canSave,
                                onClick = { onAction(AgentMemoryAction.Save) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 编辑 / 新增卡片弹窗 ─────────────────────────────────
    editDialogCard?.let { draftCard ->
        var editTitle by remember(draftCard) { mutableStateOf(draftCard.title) }
        var editSpace by remember(draftCard) { mutableStateOf(draftCard.space) }
        var editContent by remember(draftCard) { mutableStateOf(draftCard.content) }
        var editTagsStr by remember(draftCard) { mutableStateOf(draftCard.tags.joinToString(", ")) }
        var editImportance by remember(draftCard) { mutableFloatStateOf(draftCard.importance.toFloat()) }

        WindowDialog(
            show = true,
            title = if (isNewCardDialog) "新增记忆卡片" else "编辑记忆卡片",
            onDismissRequest = { editDialogCard = null },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = "记忆标题 (例如: 工作目录规范)",
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = editSpace,
                    onValueChange = { editSpace = it },
                    label = "分类空间 (例如: 通用、工作、生活、开发、偏好)",
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    label = "核心内容",
                    useLabelAsPlaceholder = true,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = editTagsStr,
                    onValueChange = { editTagsStr = it },
                    label = "标签 (多个标签用逗号隔开)",
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "重要度: ${editImportance.toInt()} 星",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Row {
                        repeat(editImportance.toInt()) {
                            Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Slider(
                    value = editImportance,
                    onValueChange = { editImportance = it },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { editDialogCard = null },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "保存",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = editTitle.isNotBlank() && editContent.isNotBlank(),
                        onClick = {
                            val parsedTags = editTagsStr.split(",", "，", " ")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            val newCard = draftCard.copy(
                                title = editTitle.trim(),
                                space = editSpace.trim().ifBlank { "通用" },
                                content = editContent.trim(),
                                tags = parsedTags,
                                importance = editImportance.toInt().coerceIn(1, 5),
                                updatedAt = System.currentTimeMillis(),
                            )
                            onAction(AgentMemoryAction.SaveCard(newCard))
                            editDialogCard = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ── 单卡片删除确认弹窗 ─────────────────────────────────
    deleteTargetCardId?.let { cardId ->
        WindowDialog(
            show = true,
            title = "删除记忆卡片",
            summary = "确认删除该条记忆卡片？删除后 AI 将不再引用该事实。",
            onDismissRequest = { deleteTargetCardId = null },
        ) {
            MiuixDialogActions(
                confirmText = "删除",
                destructive = true,
                onCancel = { deleteTargetCardId = null },
                onConfirm = {
                    onAction(AgentMemoryAction.DeleteCard(cardId))
                    deleteTargetCardId = null
                },
            )
        }
    }

    // ── 清空所有卡片弹窗 ─────────────────────────────────
    if (showClearCardsDialog) {
        WindowDialog(
            show = true,
            title = "清空全部记忆卡片",
            summary = "将永久清除所有已记录的结构化记忆卡片，此操作不可撤销。",
            onDismissRequest = { showClearCardsDialog = false },
        ) {
            MiuixDialogActions(
                confirmText = "清空全部",
                destructive = true,
                onCancel = { showClearCardsDialog = false },
                onConfirm = {
                    showClearCardsDialog = false
                    onAction(AgentMemoryAction.ClearAllCards)
                },
            )
        }
    }

    // ── 清空 MEMORY.md 弹窗 ──────────────────────────────
    if (showClearMdDialog) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.ui_clear_all_memory_a43bd3),
            summary = stringResource(R.string.ui_the_entire_contents_of_memory_md_will_be_deleted_and_83a8ac),
            onDismissRequest = { showClearMdDialog = false },
        ) {
            MiuixDialogActions(
                confirmText = stringResource(R.string.memory_clear),
                destructive = true,
                confirmEnabled = !state.isSaving,
                onCancel = { showClearMdDialog = false },
                onConfirm = {
                    showClearMdDialog = false
                    onAction(AgentMemoryAction.Clear)
                },
            )
        }
    }

    // ── 通知弹窗 ─────────────────────────────────────────
    state.notice?.let { notice ->
        WindowDialog(
            show = true,
            title = stringResource(R.string.ui_memory_b55ff5),
            summary = notice,
            onDismissRequest = { onAction(AgentMemoryAction.DismissNotice) },
        ) {
            TextButton(
                text = stringResource(R.string.ui_knew_cb63c6),
                onClick = { onAction(AgentMemoryAction.DismissNotice) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatBytes(bytes: Int): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "%.1f KiB".format(bytes / 1_024.0)
    else -> "%.2f MiB".format(bytes / (1_024.0 * 1_024.0))
}

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance().format(value)
