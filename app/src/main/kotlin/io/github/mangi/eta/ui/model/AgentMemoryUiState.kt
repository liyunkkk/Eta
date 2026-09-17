package io.github.mangi.eta.ui.model

import androidx.compose.runtime.Immutable
import io.github.mangi.eta.data.model.memory.MemoryCard
import io.github.mangi.eta.data.repository.AgentMemoryStore

@Immutable
data class AgentMemoryUiState(
    val enabled: Boolean = true,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val draft: String = "",
    val savedContent: String = "",
    val draftBytes: Int = 0,
    val maxBytes: Int = AgentMemoryStore.MAX_FILE_BYTES,
    val coreBudgetChars: Int = 8_000,
    val notice: String? = null,
    val cards: List<MemoryCard> = emptyList(),
    val spaces: List<String> = listOf("全部", "通用", "用户信息", "工作", "生活", "开发", "偏好"),
    val selectedSpace: String = "全部",
    val searchQuery: String = "",
    val isImporting: Boolean = false,
    val editingCard: MemoryCard? = null,
    val isCreatingNewCard: Boolean = false,
) {
    val hasUnsavedChanges: Boolean get() = draft != savedContent
    val canSave: Boolean
        get() = !isLoading && !isSaving && hasUnsavedChanges &&
            draftBytes <= maxBytes

    val filteredCards: List<MemoryCard>
        get() {
            val spaceFilter = if (selectedSpace == "全部" || selectedSpace.isBlank()) null else selectedSpace
            val query = searchQuery.trim().lowercase()
            return cards.filter { card ->
                if (spaceFilter != null && !card.space.equals(spaceFilter, ignoreCase = true)) {
                    return@filter false
                }
                if (query.isNotEmpty()) {
                    val inTitle = card.title.lowercase().contains(query)
                    val inContent = card.content.lowercase().contains(query)
                    val inTags = card.tags.any { it.lowercase().contains(query) }
                    if (!inTitle && !inContent && !inTags) return@filter false
                }
                true
            }
        }
}
