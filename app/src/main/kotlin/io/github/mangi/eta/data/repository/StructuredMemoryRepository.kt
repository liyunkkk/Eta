package io.github.mangi.eta.data.repository

import android.content.Context
import io.github.mangi.eta.data.model.memory.MemoryCard
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.roundToInt

class StructuredMemoryRepository(private val storageFile: File) {

    private val lock = ReentrantReadWriteLock()
    private val cards = mutableListOf<MemoryCard>()
    private var loaded = false

    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "structured_memories.json")
    )

    private fun ensureLoaded() {
        if (loaded) return
        lock.write {
            if (loaded) return
            if (storageFile.exists()) {
                try {
                    val rawText = storageFile.readText(Charsets.UTF_8)
                    if (rawText.isNotBlank()) {
                        val jsonArray = JSONArray(rawText)
                        cards.clear()
                        for (i in 0 until jsonArray.length()) {
                            val item = jsonArray.optJSONObject(i) ?: continue
                            cards.add(MemoryCard.fromJsonObject(item))
                        }
                    }
                } catch (_: Exception) {}
            }
            loaded = true
        }
    }

    private fun persistLocked() {
        try {
            val jsonArray = JSONArray()
            cards.forEach { card -> jsonArray.put(card.toJsonObject()) }
            val parent = storageFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            storageFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun saveCard(
        title: String,
        content: String,
        space: String = MemoryCard.DEFAULT_SPACE,
        tags: List<String> = emptyList(),
        importance: Int = MemoryCard.DEFAULT_IMPORTANCE,
        customId: String? = null,
        createdAt: Long? = null,
        updatedAt: Long? = null,
    ): MemoryCard {
        require(title.isNotBlank()) { "记忆标题不能为空" }
        require(content.isNotBlank()) { "记忆内容不能为空" }
        ensureLoaded()

        val normalizedSpace = space.trim().ifBlank { MemoryCard.DEFAULT_SPACE }
        val normalizedTitle = title.trim()
        val normalizedTags = tags.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        val validImportance = importance.coerceIn(MemoryCard.MIN_IMPORTANCE, MemoryCard.MAX_IMPORTANCE)
        val now = System.currentTimeMillis()

        return lock.write {
            val existingIndex = if (!customId.isNullOrBlank()) {
                cards.indexOfFirst { it.id == customId }
            } else {
                cards.indexOfFirst {
                    it.space.equals(normalizedSpace, ignoreCase = true) &&
                        it.title.equals(normalizedTitle, ignoreCase = true)
                }
            }

            val savedCard = if (existingIndex >= 0) {
                val existing = cards[existingIndex]
                val merged = existing.copy(
                    title = normalizedTitle,
                    content = content.trim(),
                    space = normalizedSpace,
                    tags = if (normalizedTags.isNotEmpty()) normalizedTags else existing.tags,
                    importance = validImportance,
                    updatedAt = updatedAt ?: now,
                )
                cards[existingIndex] = merged
                merged
            } else {
                val newCard = MemoryCard(
                    id = customId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                    title = normalizedTitle,
                    content = content.trim(),
                    space = normalizedSpace,
                    tags = normalizedTags,
                    importance = validImportance,
                    createdAt = createdAt ?: now,
                    updatedAt = updatedAt ?: now,
                )
                cards.add(newCard)
                newCard
            }
            persistLocked()
            savedCard
        }
    }

    fun getCard(id: String): MemoryCard? {
        ensureLoaded()
        return lock.read { cards.firstOrNull { it.id == id } }
    }

    fun deleteCard(id: String): Boolean {
        ensureLoaded()
        return lock.write {
            val removed = cards.removeAll { it.id == id }
            if (removed) persistLocked()
            removed
        }
    }

    fun clearAll(): Int {
        ensureLoaded()
        return lock.write {
            val size = cards.size
            cards.clear()
            persistLocked()
            size
        }
    }

    fun listCards(space: String? = null, keyword: String? = null): List<MemoryCard> {
        ensureLoaded()
        val targetSpace = if (space.isNullOrBlank() || space == "全部") null else space.trim()
        val targetKey = keyword?.trim()?.lowercase()?.takeIf(String::isNotEmpty)

        return lock.read {
            cards.asSequence()
                .filter { card ->
                    if (targetSpace != null && !card.space.equals(targetSpace, ignoreCase = true)) {
                        return@filter false
                    }
                    if (targetKey != null) {
                        val inTitle = card.title.lowercase().contains(targetKey)
                        val inContent = card.content.lowercase().contains(targetKey)
                        val inTags = card.tags.any { it.lowercase().contains(targetKey) }
                        if (!inTitle && !inContent && !inTags) return@filter false
                    }
                    true
                }
                .sortedWith(
                    compareByDescending<MemoryCard> { it.importance }.thenByDescending { it.updatedAt }
                )
                .toList()
        }
    }

    fun listSpaces(): List<String> {
        ensureLoaded()
        return lock.read {
            val set = linkedSetOf("全部", "通用", "全局准则", "用户信息", "工作", "生活", "开发", "偏好")
            cards.forEach { if (it.space.isNotBlank()) set.add(it.space) }
            set.toList()
        }
    }

    fun queryCards(
        space: String? = null,
        tags: List<String>? = null,
        keyword: String? = null,
        limit: Int = 10,
    ): List<MemoryCard> {
        ensureLoaded()
        val normalizedSpace = if (space.isNullOrBlank() || space == "全部") null else space.trim()
        val targetTags = tags?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }?.filter { it.isNotEmpty() }
        val targetKeyword = keyword?.trim()?.lowercase()?.takeIf(String::isNotEmpty)

        return lock.read {
            cards.asSequence()
                .filter { card ->
                    if (normalizedSpace != null && !card.space.equals(normalizedSpace, ignoreCase = true)) {
                        return@filter false
                    }
                    if (!targetTags.isNullOrEmpty()) {
                        val cardTagsLower = card.tags.map { it.lowercase() }
                        val matchedAnyTag = targetTags.any { it.lowercase() in cardTagsLower }
                        if (!matchedAnyTag) return@filter false
                    }
                    if (targetKeyword != null) {
                        val inTitle = card.title.lowercase().contains(targetKeyword)
                        val inContent = card.content.lowercase().contains(targetKeyword)
                        val inTags = card.tags.any { it.lowercase().contains(targetKeyword) }
                        if (!inTitle && !inContent && !inTags) return@filter false
                    }
                    true
                }
                .sortedWith(
                    compareByDescending<MemoryCard> { it.importance }.thenByDescending { it.updatedAt }
                )
                .take(limit.coerceAtLeast(1))
                .toList()
        }
    }

    fun exportJson(): String {
        ensureLoaded()
        return lock.read {
            val arr = JSONArray()
            cards.forEach { arr.put(it.toJsonObject()) }
            arr.toString(2)
        }
    }

    fun importFromJson(jsonString: String): Int {
        if (jsonString.isBlank()) return 0
        ensureLoaded()

        val jsonArray = try {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("memories")
                    ?: obj.optJSONArray("cards")
                    ?: obj.optJSONArray("items")
                    ?: JSONArray()
            }
        } catch (_: Exception) {
            return 0
        }

        var importedCount = 0
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            val content = item.optString("content").trim()
            if (title.isEmpty() || content.isEmpty()) continue

            val space = item.optString("folderPath").trim().ifBlank {
                item.optString("space").trim().ifBlank { MemoryCard.DEFAULT_SPACE }
            }

            val tagsList = mutableListOf<String>()
            val tagNamesArr = item.optJSONArray("tagNames") ?: item.optJSONArray("tags")
            if (tagNamesArr != null) {
                for (t in 0 until tagNamesArr.length()) {
                    val tag = tagNamesArr.optString(t).trim()
                    if (tag.isNotEmpty()) tagsList.add(tag)
                }
            }

            val importanceRaw = item.opt("importance")
            val importance = when (importanceRaw) {
                is Number -> {
                    val d = importanceRaw.toDouble()
                    if (d in 0.0..1.0) {
                        (d * 4).roundToInt() + 1
                    } else {
                        d.toInt().coerceIn(MemoryCard.MIN_IMPORTANCE, MemoryCard.MAX_IMPORTANCE)
                    }
                }
                else -> MemoryCard.DEFAULT_IMPORTANCE
            }

            val customId = item.optString("uuid").ifBlank { item.optString("id") }
            val createdAt = item.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = item.optLong("updatedAt", System.currentTimeMillis())

            saveCard(
                title = title,
                content = content,
                space = space,
                tags = tagsList,
                importance = importance,
                customId = customId,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
            importedCount++
        }

        return importedCount
    }

    fun importFromOperitJson(jsonString: String): Int = importFromJson(jsonString)

    /**
     * 将全部记忆卡片自动生成一份格式优雅的 Markdown 镜像文本，用于底层 MEMORY.md 兼容
     */
    fun generateMarkdownMirror(): String {
        ensureLoaded()
        return lock.read {
            if (cards.isEmpty()) return@read "# 核心记忆\n\n暂无持久记忆。\n"

            buildString {
                appendLine("# 核心记忆")
                appendLine()

                // 优先展示全局准则，其余分类按字母顺序
                val grouped = cards.groupBy { it.space }
                val sortedSpaces = grouped.keys.sortedWith { a, b ->
                    when {
                        a == "全局准则" -> -1
                        b == "全局准则" -> 1
                        a == "通用" -> -1
                        b == "通用" -> 1
                        else -> a.compareTo(b)
                    }
                }

                sortedSpaces.forEach { space ->
                    val spaceCards = grouped[space].orEmpty().sortedWith(
                        compareByDescending<MemoryCard> { it.importance }.thenByDescending { it.updatedAt }
                    )
                    appendLine("## $space")
                    spaceCards.forEach { card ->
                        val tagStr = if (card.tags.isNotEmpty()) "|${card.tags.joinToString(",")}" else ""
                        val singleLine = card.content.replace("\n", " ").trim()
                        appendLine("- [$space$tagStr] ${card.title}: $singleLine")
                    }
                    appendLine()
                }
            }.trimEnd() + "\n"
        }
    }

    /**
     * 同步写回底层的 MEMORY.md 文件，确保全系统与 Agent 提示词双向镜像
     */
    fun syncToMarkdownMirror(context: Context) {
        val markdown = generateMarkdownMirror()
        try {
            AgentMemoryRepository.replaceAll(markdown)
        } catch (_: Exception) {}
    }

    fun toCompactPrompt(space: String? = null, limit: Int = 5): String {
        val selectedCards = queryCards(space = space, limit = limit)
        if (selectedCards.isEmpty()) return ""

        return buildString {
            selectedCards.forEach { card ->
                val tagStr = if (card.tags.isNotEmpty()) "|" + card.tags.joinToString(",") else ""
                val singleLineContent = card.content.replace("\n", " ").trim()
                appendLine("- [${card.space}$tagStr] ${card.title}: $singleLineContent")
            }
        }.trimEnd()
    }
}
