package io.github.mangi.eta.ui.screens.memory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
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
    var showClearCardsDialog by remember { mutableStateOf(false) }
    var showMirrorPreviewDialog by remember { mutableStateOf(false) }
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
            // 全屏流式统一记忆管理中枢
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = sidePadding,
                    end = sidePadding,
                    bottom = 32.dp,
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
                            summary = "高优先级准则与核心记忆自动注入上下文（上限 ${formatNumber(state.coreBudgetChars)} 字符）",
                        )
                    }
                }

                // ── 记忆库操作顶栏 ──────────────────────────────────
                item(key = "cards-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SmallTitle("统一记忆库 (${state.cards.size})")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    isNewCardDialog = true
                                    editDialogCard = MemoryCard(
                                        title = "",
                                        content = "",
                                        space = if (state.selectedSpace != "全部") state.selectedSpace else "通用",
                                        importance = if (state.selectedSpace == "开发") 5 else 3,
                                    )
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
                            IconButton(
                                onClick = { onAction(AgentMemoryAction.SyncFromLegacyMd) },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = "从 MEMORY.md 重新合并同步",
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            }
                            IconButton(
                                onClick = { showMirrorPreviewDialog = true },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = "底层镜像预览",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            if (state.cards.isNotEmpty()) {
                                IconButton(
                                    onClick = { showClearCardsDialog = true },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = "清空全部",
                                        tint = MiuixTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 实时搜索框 ──────────────────────────────────────
                item(key = "search-bar") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { onAction(AgentMemoryAction.SearchQueryChanged(it)) },
                            label = "搜索准则、偏好或标签",
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

                // ── 空间横向分类药丸 (Filter Chips) ───────────────────
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

                // ── 统一记忆卡片列表 ────────────────────────────────
                val filtered = state.filteredCards
                if (filtered.isEmpty()) {
                    item(key = "empty-cards") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = if (state.searchQuery.isNotEmpty()) "未匹配到相关记忆" else "当前分类暂无记忆或准则",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                TextButton(
                                    text = "+ 新增一条规则或偏好",
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    onClick = {
                                        isNewCardDialog = true
                                        editDialogCard = MemoryCard(
                                            title = "",
                                            content = "",
                                            space = if (state.selectedSpace != "全部") state.selectedSpace else "通用",
                                            importance = if (state.selectedSpace == "开发") 5 else 3,
                                        )
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
                                        // 5 颗星重要度渲染
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
                                                contentDescription = "编辑",
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
                                                contentDescription = "删除",
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
            title = if (isNewCardDialog) "新增记忆条目" else "编辑记忆条目",
            onDismissRequest = { editDialogCard = null },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = "条目标题 (如: 工作目录规范、手机型号)",
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = editSpace,
                    onValueChange = { editSpace = it },
                    label = "分类空间 (如: 开发、项目信息、用户信息、偏好)",
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    label = "具体事实或行为要求",
                    useLabelAsPlaceholder = true,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = editTagsStr,
                    onValueChange = { editTagsStr = it },
                    label = "标签 (逗号分隔，如: 规范, 路径)",
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "重要度: ${editImportance.toInt()} 星 (4~5星优先自动注入每次新会话)",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Row {
                        repeat(editImportance.toInt()) {
                            Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(15.dp))
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
            title = "删除记忆条目",
            summary = "确认删除该条记忆？删除后底层 MEMORY.md 将同步抹除该记录，AI 不再遵循此要求。",
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
            title = "清空全部记忆",
            summary = "将永久清空所有已记录的卡片及底层 MEMORY.md 镜像，此操作不可撤销。",
            onDismissRequest = { showClearCardsDialog = false },
        ) {
            MiuixDialogActions(
                confirmText = "全部清空",
                destructive = true,
                onCancel = { showClearCardsDialog = false },
                onConfirm = {
                    showClearCardsDialog = false
                    onAction(AgentMemoryAction.ClearAllCards)
                },
            )
        }
    }

    // ── 底层同步镜像只读预览弹窗 ─────────────────────────
    if (showMirrorPreviewDialog) {
        val mirrorText = remember(state.cards) {
            io.github.mangi.eta.data.repository.StructuredMemoryRepository(context).generateMarkdownMirror()
        }
        WindowDialog(
            show = true,
            title = "底层 MEMORY.md 实时镜像预览",
            summary = "系统已自动将上方卡片编译为标准 Markdown 供 Agent Runtime 挂载注入：",
            onDismissRequest = { showMirrorPreviewDialog = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                ) {
                    Text(
                        text = mirrorText,
                        style = MiuixTheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(12.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    text = "关闭预览",
                    onClick = { showMirrorPreviewDialog = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance().format(value)
