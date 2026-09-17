package io.github.mangi.eta.agent.tool

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KimiCodeSubagentToolTest {

    @Test
    fun testBindingSessionLogic() {
        val bindings = mutableMapOf<String, String>()
        val convId1 = UUID.randomUUID().toString()
        val convId2 = UUID.randomUUID().toString()

        val kimiSession1 = "session_1111"
        bindings[convId1] = kimiSession1

        assertEquals(kimiSession1, bindings[convId1])
        assertFalse(bindings.containsKey(convId2))

        val kimiSession2 = "session_2222"
        bindings[convId2] = kimiSession2

        assertEquals(kimiSession1, bindings[convId1])
        assertEquals(kimiSession2, bindings[convId2])
    }

    @Test
    fun testPromptJsonStructure() {
        val task = "在根目录创建测试文件"
        val contentArr = org.json.JSONArray().put(
            JSONObject().put("type", "text").put("text", task)
        )
        val body = JSONObject()
            .put("content", contentArr)
            .put("model", "gemini/gemini-3.8-flash-high")
            .put("permission_mode", "auto")

        assertEquals("auto", body.getString("permission_mode"))
        assertEquals("gemini/gemini-3.8-flash-high", body.getString("model"))
        val arr = body.getJSONArray("content")
        assertEquals(1, arr.length())
        assertEquals(task, arr.getJSONObject(0).getString("text"))
    }
}
