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
        val original = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "assistant")
                put("reasoning_content", "thinking 1")
                put("content", "step 1")
            })
            put(JSONObject().apply {
                put("role", "assistant")
                put("reasoning_content", "thinking 2")
                put("content", "step 2")
            })
        }
        val originalStringBefore = original.toString()

        val optimized = DynamicContextOptimizer.optimize(original)

        assertEquals(originalStringBefore, original.toString())
        assertNotSame(original, optimized)
        assertFalse(optimized.getJSONObject(0).has("reasoning_content"))
        assertTrue(optimized.getJSONObject(1).has("reasoning_content"))
    }

    @Test
    fun scrubHistoricalThinkingKeepsOnlyLastAssistantThinking() {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "user").put("content", "start"))
            put(JSONObject().put("role", "assistant").put("reasoning_content", "thought 1").put("content", "reply 1"))
            put(JSONObject().put("role", "user").put("content", "next"))
            put(JSONObject().put("role", "assistant").put("reasoning_content", "thought 2").put("content", "reply 2"))
            put(JSONObject().put("role", "user").put("content", "final"))
            put(JSONObject().put("role", "assistant").put("reasoning_content", "thought 3").put("content", "reply 3"))
        }

        val optimized = DynamicContextOptimizer.optimize(messages)

        assertEquals(6, optimized.length())
        assertFalse(optimized.getJSONObject(1).has("reasoning_content"))
        assertFalse(optimized.getJSONObject(3).has("reasoning_content"))
        assertEquals("thought 3", optimized.getJSONObject(5).getString("reasoning_content"))
    }

    @Test
    fun singleAssistantRetainsThinking() {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "assistant").put("reasoning_content", "lone thought").put("content", "reply"))
        }

        val optimized = DynamicContextOptimizer.optimize(messages)

        assertEquals(1, optimized.length())
        assertEquals("lone thought", optimized.getJSONObject(0).getString("reasoning_content"))
    }

    @Test
    fun evictStaleUiObservationsReplacesAllExceptLast() {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "assistant")
                put("tool_calls", JSONArray().put(JSONObject().apply {
                    put("id", "call_1")
                    put("function", JSONObject().put("name", "observe_screen"))
                }))
            })
            put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", "call_1")
                put("content", "{\"observation_id\":\"111\",\"ui_nodes\":[{\"id\":\"btn\"}]}")
            })
            put(JSONObject().apply {
                put("role", "assistant")
                put("tool_calls", JSONArray().put(JSONObject().apply {
                    put("id", "call_2")
                    put("function", JSONObject().put("name", "read_image"))
                }))
            })
            put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", "call_2")
                put("content", "image observation content")
            })
            put(JSONObject().apply {
                put("role", "assistant")
                put("tool_calls", JSONArray().put(JSONObject().apply {
                    put("id", "call_3")
                    put("function", JSONObject().put("name", "custom_tool"))
                }))
            })
            put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", "call_3")
                put("content", "{\"ui_nodes\":[{\"id\":\"latest_node\"}],\"observation_id\":\"333\"}")
            })
        }

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
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "assistant")
                put("tool_calls", JSONArray().put(JSONObject().apply {
                    put("id", "call_1")
                    put("function", JSONObject().put("name", "observe_screen"))
                }))
            })
            put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", "call_1")
                put("content", "{\"observation_id\":\"111\",\"ui_nodes\":[]}")
            })
        }

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

        assertTrue(tool1Content.contains("... [已动态折叠"))
        assertTrue(tool1Content.startsWith("line 1:"))
        assertTrue(tool1Content.endsWith("line 50: some output details"))
        assertEquals(long50Lines, tool2Content)
    }
}
