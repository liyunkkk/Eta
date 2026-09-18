package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

object DynamicContextOptimizer {

    private const val STALE_OBSERVATION_REPLACEMENT = "[已在此步完成屏幕观察，界面后续已发生状态迁移]"
    private const val MAX_TOOL_CONTENT_CHARS = 1500
    private const val MAX_TOOL_CONTENT_LINES = 30
    private const val TRUNCATE_HEAD_LINES = 12
    private const val TRUNCATE_TAIL_LINES = 12
    private val SCREEN_TOOL_NAMES = setOf("observe_screen", "read_image")

    fun optimize(messages: JSONArray, roundTools: JSONArray? = null): JSONArray {
        if (messages.length() == 0) return JSONArray()

        // 浅拷贝浅层 JSONObject，消除全量 toString() 与反序列化开销
        val result = JSONArray()
        for (i in 0 until messages.length()) {
            val original = messages.optJSONObject(i)
            if (original != null) {
                result.put(JSONObject(original.toString()))
            } else {
                result.put(messages.get(i))
            }
        }

        scrubHistoricalThinking(result)
        evictStaleUiObservations(result)
        truncateLongToolOutputs(result)

        return result
    }

    private fun scrubHistoricalThinking(messages: JSONArray) {
        val assistantIndices = mutableListOf<Int>()
        for (i in 0 until messages.length()) {
            val obj = messages.optJSONObject(i) ?: continue
            if (obj.optString("role") == "assistant") {
                assistantIndices.add(i)
            }
        }
        if (assistantIndices.size > 1) {
            for (i in assistantIndices.dropLast(1)) {
                messages.optJSONObject(i)?.remove("reasoning_content")
            }
        }
    }

    private fun evictStaleUiObservations(messages: JSONArray) {
        val toolCallNames = collectToolCallNames(messages)
        val observationIndices = mutableListOf<Int>()

        for (i in 0 until messages.length()) {
            val msg = messages.optJSONObject(i) ?: continue
            if (msg.optString("role") == "tool" && isScreenObservation(msg, toolCallNames)) {
                observationIndices.add(i)
            }
        }

        if (observationIndices.size > 1) {
            for (i in observationIndices.dropLast(1)) {
                val msg = messages.optJSONObject(i) ?: continue
                msg.put("content", STALE_OBSERVATION_REPLACEMENT)
            }
        }
    }

    private fun truncateLongToolOutputs(messages: JSONArray) {
        val latestAssistantIndex = (messages.length() - 1 downTo 0).firstOrNull {
            messages.optJSONObject(it)?.optString("role") == "assistant"
        } ?: -1

        var firstPostAssistantToolIndex = -1
        if (latestAssistantIndex >= 0) {
            for (i in latestAssistantIndex + 1 until messages.length()) {
                if (messages.optJSONObject(i)?.optString("role") == "tool") {
                    firstPostAssistantToolIndex = i
                    break
                }
            }
        }

        val exemptIndices = if (firstPostAssistantToolIndex >= 0) {
            (firstPostAssistantToolIndex until messages.length())
                .filter { messages.optJSONObject(it)?.optString("role") == "tool" }
                .toSet()
        } else {
            emptySet()
        }

        for (i in 0 until messages.length()) {
            if (i in exemptIndices) continue
            val msg = messages.optJSONObject(i) ?: continue
            if (msg.optString("role") != "tool") continue

            val rawContent = msg.opt("content")
            val contentStr = when (rawContent) {
                is String -> rawContent
                null -> ""
                else -> rawContent.toString()
            }

            if (contentStr.startsWith("[")) continue

            val lines = contentStr.lines()
            if (lines.size > MAX_TOOL_CONTENT_LINES || contentStr.length > MAX_TOOL_CONTENT_CHARS) {
                val truncated = truncateLines(lines, contentStr)
                msg.put("content", truncated)
            }
        }
    }

    private fun truncateLines(lines: List<String>, rawContent: String): String {
        if (lines.size > MAX_TOOL_CONTENT_LINES) {
            val head = lines.take(TRUNCATE_HEAD_LINES)
            val tail = lines.takeLast(TRUNCATE_TAIL_LINES)
            val foldedCount = lines.size - TRUNCATE_HEAD_LINES - TRUNCATE_TAIL_LINES
            val headText = head.joinToString("\n")
            val tailText = tail.joinToString("\n")
            return "$headText\n... [已动态折叠 $foldedCount 行中间输出] ...\n$tailText"
        }
        val headChars = (MAX_TOOL_CONTENT_CHARS / 2) - 30
        val tailChars = (MAX_TOOL_CONTENT_CHARS / 2) - 30
        val headText = rawContent.take(headChars)
        val tailText = rawContent.takeLast(tailChars)
        return "$headText\n... [已动态折叠中间长文本] ...\n$tailText"
    }

    private fun collectToolCallNames(messages: JSONArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until messages.length()) {
            val msg = messages.optJSONObject(i) ?: continue
            val tc = msg.opt("tool_calls")
            val rawCalls: JSONArray? = when (tc) {
                is JSONArray -> tc
                is String -> runCatching { JSONArray(tc) }.getOrNull()
                else -> null
            }
            if (rawCalls == null) continue

            for (j in 0 until rawCalls.length()) {
                val call = rawCalls.optJSONObject(j) ?: continue
                val id = call.optString("id").trim()
                val fn = call.optJSONObject("function")
                val name = fn?.optString("name")?.trim().orEmpty()
                    .ifBlank { call.optString("name").trim() }
                if (id.isNotBlank() && name.isNotBlank()) {
                    map[id] = name
                }
            }
        }
        return map
    }

    private fun isScreenObservation(
        message: JSONObject,
        toolCallNames: Map<String, String>,
    ): Boolean {
        val toolCallId = message.optString("tool_call_id")
        val toolName = toolCallNames[toolCallId] ?: message.optString("name")
        if (toolName in SCREEN_TOOL_NAMES) {
            return true
        }
        val content = message.opt("content")
        val contentStr = when (content) {
            is String -> content
            null -> ""
            else -> content.toString()
        }
        return contentStr.contains("observation_id") || contentStr.contains("ui_nodes")
    }
}
