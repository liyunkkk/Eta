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

        val result = JSONArray(messages.toString())

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
            val obj = messages.optJSONObject(i) ?: continue
            if (obj.optString("role") == "tool" && isScreenObservation(obj, toolCallNames)) {
                observationIndices.add(i)
            }
        }

        if (observationIndices.size > 1) {
            for (i in observationIndices.dropLast(1)) {
                messages.optJSONObject(i)?.put("content", STALE_OBSERVATION_REPLACEMENT)
            }
        }
    }

    private fun truncateLongToolOutputs(messages: JSONArray) {
        val toolIndices = mutableListOf<Int>()
        for (i in 0 until messages.length()) {
            if (messages.optJSONObject(i)?.optString("role") == "tool") {
                toolIndices.add(i)
            }
        }
        if (toolIndices.isEmpty()) return

        val toolRounds = mutableListOf<MutableList<Int>>()
        for (idx in toolIndices) {
            if (toolRounds.isEmpty()) {
                toolRounds.add(mutableListOf(idx))
            } else {
                val lastRound = toolRounds.last()
                val prevIdx = lastRound.last()
                var separated = false
                for (k in prevIdx + 1 until idx) {
                    if (messages.optJSONObject(k)?.optString("role") != "tool") {
                        separated = true
                        break
                    }
                }
                if (separated) {
                    toolRounds.add(mutableListOf(idx))
                } else {
                    lastRound.add(idx)
                }
            }
        }

        if (toolRounds.size <= 1) return

        val nonLatestIndices = toolRounds.dropLast(1).flatten()
        for (idx in nonLatestIndices) {
            val toolMsg = messages.optJSONObject(idx) ?: continue
            val rawContent = toolMsg.opt("content")
            val contentStr = when (rawContent) {
                is String -> rawContent
                null -> ""
                else -> rawContent.toString()
            }
            val truncated = truncateContentIfNeeded(contentStr)
            if (truncated != contentStr) {
                toolMsg.put("content", truncated)
            }
        }
    }

    private fun truncateContentIfNeeded(content: String): String {
        val lines = content.lines()
        if (content.length <= MAX_TOOL_CONTENT_CHARS && lines.size <= MAX_TOOL_CONTENT_LINES) {
            return content
        }

        val totalKeep = TRUNCATE_HEAD_LINES + TRUNCATE_TAIL_LINES
        val (head, tail, foldedCount) = if (lines.size > totalKeep) {
            Triple(
                lines.take(TRUNCATE_HEAD_LINES),
                lines.takeLast(TRUNCATE_TAIL_LINES),
                lines.size - totalKeep,
            )
        } else {
            val expanded = lines.flatMap { line ->
                if (line.length > 80) line.chunked(80) else listOf(line)
            }.let { exp ->
                if (exp.size <= totalKeep) {
                    content.chunked(maxOf(1, content.length / 32))
                } else {
                    exp
                }
            }
            Triple(
                expanded.take(TRUNCATE_HEAD_LINES),
                expanded.takeLast(TRUNCATE_TAIL_LINES),
                expanded.size - totalKeep,
            )
        }

        val headText = head.joinToString("\n")
        val tailText = tail.joinToString("\n")
        return "$headText\n... [已动态折叠 $foldedCount 行中间输出] ...\n$tailText"
    }

    private fun collectToolCallNames(messages: JSONArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until messages.length()) {
            val msg = messages.optJSONObject(i) ?: continue
            val rawCalls = when (val tc = msg.opt("tool_calls")) {
                is JSONArray -> tc
                is String -> runCatching { JSONArray(tc) }.getOrNull()
                else -> null
            } ?: continue

            for (j in 0 until rawCalls.length()) {
                val call = rawCalls.optJSONObject(j) ?: continue
                val id = call.optString("id")
                val function = call.optJSONObject("function")
                val name = function?.optString("name")?.trim().orEmpty()
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
