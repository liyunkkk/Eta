package io.github.mangi.eta.data.model.memory

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MemoryCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val space: String = DEFAULT_SPACE,
    val tags: List<String> = emptyList(),
    val importance: Int = DEFAULT_IMPORTANCE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_SPACE = "general"
        const val DEFAULT_IMPORTANCE = 3
        const val MIN_IMPORTANCE = 1
        const val MAX_IMPORTANCE = 5

        fun fromJsonObject(json: JSONObject): MemoryCard {
            val tagsArray = json.optJSONArray("tags")
            val tags = if (tagsArray != null) {
                (0 until tagsArray.length()).mapNotNull { i ->
                    val item = tagsArray.optString(i)
                    item.takeIf { it.isNotBlank() }
                }
            } else {
                emptyList()
            }
            return MemoryCard(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = json.optString("title"),
                content = json.optString("content"),
                space = json.optString("space").ifBlank { DEFAULT_SPACE },
                tags = tags,
                importance = json.optInt("importance", DEFAULT_IMPORTANCE).coerceIn(MIN_IMPORTANCE, MAX_IMPORTANCE),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
    }

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("content", content)
        put("space", space)
        put("tags", JSONArray(tags))
        put("importance", importance)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }
}
