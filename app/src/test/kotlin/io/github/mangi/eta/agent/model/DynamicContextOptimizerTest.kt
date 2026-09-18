package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicContextOptimizerTest {

    @Test
    fun doesNotMutateOriginalMessages() {
        val original = JSONArray("""[
            {"role": "assistant", "reasoning_content": "thinking 1", "content": "step 1"},
            {"role": "assistant", "reasoning_content": "thinking 2", "content": "step 2"}
        ]""")
        val originalStringBefore = original.toString()

        val optimized = DynamicContextOptimizer.optimize(original)

        assertEquals(originalStringBefore, original.toString())
        assertNotSame(original, optimized)
        assertFalse(optimized.getJSONObject(0).has("reasoning_content"))
        assertTrue(optimized.getJSONObject(1).has("reasoning_content"))
    }

    @Test
    fun scrubHistoricalThinkingKeepsOnlyLastAssistantThinking() {
        val messages = JSONArray("""[
            {"role": "user", "content": "start"},
            {"role": "assistant", "reasoning_content": "thought 1", "content": "reply 1"},
            {"role": "user", "content": "next"},
            {"role": "assistant", "reasoning_content": "thought 2", "content": "reply 2"},
            {"role": "user", "content": "final"},
            {"role": "assistant", "reasoning_content": "thought 3", "content": "reply 3"}
        ]""")

        val optimized = DynamicContextOptimizer.optimize(messages)

        assertEquals(6, optimized.length())
        assertFalse(optimized.getJSONObject(1).has("reasoning_content"))
        assertFalse(optimized.getJSONObject(3).has("reasoning_content"))
        assertEquals("thought 3", optimized.getJSONObject(5).getString("reasoning_content"))
    }

    @Test
    fun singleAssistantRetainsThinking() {
        val messages = JSONArray("""[
            {"role": "assistant", "reasoning_content": "lone thought", "content": "reply"}
        ]""")

        val optimized = DynamicContextOptimizer.optimize(messages)

        assertEquals(1, optimized.length())
        assertEquals("lone thought", optimized.getJSONObject(0).getString("reasoning_content"))
    }

    @Test
    fun evictStaleUiObservationsReplacesAllExceptLast() {
        val messages = JSONArray("""[
            {
                "role": "assistant",
                "tool_calls": [{"id": "call_1", "function": {"name": "observe_screen"}}]
            },
            {
                "role": "tool",
                "tool_call_id": "call_1",
                "content": "{\"observation_id\":\"111\",\"ui_nodes\":[{\"id\":\"btn\"}]}"
            },
            {
                "role": "assistant",
                "tool_calls": [{"id": "call_2", "function": {"name": "read_image"}}]
            },
            {
                "role": "tool",
                "tool_call_id": "call_2",
                "content": "image observation content"
            },
            {
                "role": "assistant",
                "tool_calls": [{"id": "call_3", "function": {"name": "custom_tool"}}]
            },
            {
                "role": "tool",
                "tool_call_id": "call_3",
                "content": "{\"ui_nodes\":[{\"id\":\"latest_node\"}],\"observation_id\":\"333\"}"
            }
        ]""")

        val optimized = DynamicContextOptimizer.optimize(messages)

        val replacement = "[已在此步完成屏幕观察，界面后续已发生状态迁移]"
        assertEquals(replacement, optimized.getJSONObject(1).getString("content"))
        assertEquals(replacement, optimized.getJSONObject(3).getString("content"))
        assertEquals(
            "{\"ui_nodes\":[{\"id\":\"latest_node\"}],\"observation_id\":\"333\"}",
            optimized.getJSONObject(5).getString("content")
        )
    }

    @Test
    fun singleScreenObservationIsNotEvicted() {
        val messages = JSONArray("""[
            {
                "role": "assistant",
                "tool_calls": [{"id": "call_1", "function": {"name": "observe_screen"}}]
            },
            {
                "role": "tool",
                "tool_call_id": "call_1",
                "content": "{\"observation_id\":\"111\",\"ui_nodes\":[]}"
            }
        ]""")

        val optimized = DynamicContextOptimizer.optimize(messages)

        assertEquals("{\"observation_id\":\"111\",\"ui_nodes\":[]}", optimized.getJSONObject(1).getString("content"))
    }

    @Test
    fun truncateLongToolOutputsForNonLatestRounds() {
        val long50Lines = (1..50).joinToString("\n") { "line $it: some output details" }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "assistant")
                put("tool_calls", JSONArray().put(JSONObject().apply {
                    put("id", "call_1")
                    put("function", JSONObject().put("name", "bash"))
                }))
            })
            put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", "call_1")
                put("content", long50Lines)
            })
            put(JSONObject().apply {
                put("role", "assistant")
                put("tool_calls", JSONArray().put(JSONObject().apply {
                    put("id", "call_2")
                    put("function", JSONObject().put("name", "bash"))
                }))
            })
            put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", "call_2")
                put("content", long50Lines)
            })
        }

        val optimized = DynamicContextOptimizer.optimize(messages)

        val tool1Content = optimized.getJSONObject(1).getString("content")
        val tool2Content = optimized.getJSONObject(3).getString("content")

        assertTrue(tool1Content.contains("... [已动态折叠 26 行中间输出] ..."))
        assertTrue(tool1Content.startsWith("line 1:"))
        assertTrue(tool1Content.endsWith("line 50: some output details"))
        assertEquals(25, tool1Content.lines().size)

        assertEquals(long50Lines, tool2Content)
    }

    @Test
    fun latestRoundParallelToolsAreNotTruncated() {
        val long50Lines1 = (1..50).joinToString("\n") { "A$it" }
        val long50Lines2 = (1..50).joinToString("\n") { "B$it" }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "assistant")
                put("tool_calls", JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "call_1")
                        put("function", JSONObject().put("name", "tool_a"))
                    })
                    put(JSONObject().apply {
                        put("id", "call_2")
                        put("function", JSONObject().put("name", "tool_b"))
                    })
                })
            })
            put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", "call_1")
                put("content", long50Lines1)
            })
            put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", "call_2")
                put("content", long50Lines2)
            })
        }

        val optimized = DynamicContextOptimizer.optimize(messages)

        assertEquals(long50Lines1, optimized.getJSONObject(1).getString("content"))
        assertEquals(long50Lines2, optimized.getJSONObject(2).getString("content"))
    }

    @Test
    fun nonLatestRoundTruncatesIfCharsExceed1500() {
        val singleLongLine = "X".repeat(2000)

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "assistant")
                put("tool_calls", JSONArray().put(JSONObject().apply {
                    put("id", "call_1")
                    put("function", JSONObject().put("name", "bash"))
                }))
            })
            put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", "call_1")
                put("content", singleLongLine)
            })
            put(JSONObject().apply {
                put("role", "assistant")
                put("tool_calls", JSONArray().put(JSONObject().apply {
                    put("id", "call_2")
                    put("function", JSONObject().put("name", "bash"))
                }))
            })
            put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", "call_2")
                put("content", "recent output")
            })
        }

        val optimized = DynamicContextOptimizer.optimize(messages)

        val tool1Content = optimized.getJSONObject(1).getString("content")
        assertTrue(tool1Content.contains("... [已动态折叠"))
        assertTrue(tool1Content.length < singleLongLine.length)
        assertEquals("recent output", optimized.getJSONObject(3).getString("content"))
    }
}
