package io.github.mangi.eta.data.repository

import android.content.Context
import io.github.mangi.eta.data.model.memory.MemoryCard
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
            val existingIndex = cards.indexOfFirst {
                it.space.equals(normalizedSpace, ignoreCase = true) &&
                    it.title.equals(normalizedTitle, ignoreCase = true)
            }

            val savedCard = if (existingIndex >= 0) {
                val existing = cards[existingIndex]
                val merged = existing.copy(
                    content = content.trim(),
                    tags = if (normalizedTags.isNotEmpty()) normalizedTags else existing.tags,
                    importance = validImportance,
                    updatedAt = updatedAt ?: now,
                )
                cards[existingIndex] = merged
                merged
            } else {
                val newCard = MemoryCard(
                    id = customId?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
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

    fun listCards(space: String? = null): List<MemoryCard> {
        ensureLoaded()
        return lock.read {
            val filtered = if (space.isNullOrBlank()) {
                cards
            } else {
                cards.filter { it.space.equals(space.trim(), ignoreCase = true) }
            }
            filtered.sortedWith(
                compareByDescending<MemoryCard> { it.importance }.thenByDescending { it.updatedAt }
            )
        }
    }

    fun queryCards(
        space: String? = null,
        tags: List<String>? = null,
        keyword: String? = null,
        limit: Int = 10,
    ): List<MemoryCard> {
        ensureLoaded()
        val normalizedSpace = space?.trim()?.takeIf(String::isNotEmpty)
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

    fun importFromOperitJson(jsonString: String): Int {
        if (jsonString.isBlank()) return 0
        ensureLoaded()

        val jsonArray = try {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("memories") ?: JSONArray()
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
}
