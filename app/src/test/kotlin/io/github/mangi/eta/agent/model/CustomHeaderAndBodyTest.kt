package io.github.mangi.eta.agent.model

import io.github.mangi.eta.data.model.CustomBody
import io.github.mangi.eta.data.model.CustomHeader
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomHeaderAndBodyTest {
    @Test
    fun authHeadersCanOverrideDefaultsWhileTransportHeadersStayForbidden() {
        val sanitized = CustomHeaderFilter.sanitize(
            listOf(
                CustomHeader("Authorization", "Bearer custom"),
                CustomHeader("x-api-key", "custom-key"),
                CustomHeader("host", "example.com"),
                CustomHeader("anthropic-version", "unsupported-override"),
                CustomHeader("x-extra", "ok")
            )
        )

        assertEquals(
            listOf(
                CustomHeader("Authorization", "Bearer custom"),
                CustomHeader("x-api-key", "custom-key"),
                CustomHeader("x-extra", "ok")
            ),
            sanitized
        )
        assertFalse(CustomHeaderFilter.isForbidden("authorization"))
        assertFalse(CustomHeaderFilter.isForbidden("x-api-key"))
        assertTrue(CustomHeaderFilter.isForbidden("host"))
        assertTrue(CustomHeaderFilter.isForbidden("anthropic-version"))
    }

    @Test
    fun customBodyRecursivelyMergesObjectsAndOverridesLeaves() {
        val target = JSONObject("""{"model":"x","metadata":{"a":1,"b":2},"temperature":1}""")
        val body = listOf(
            CustomBody(
                key = "metadata",
                value = Json.parseToJsonElement("""{"b":3,"c":4}""")
            ),
            CustomBody(
                key = "temperature",
                value = Json.parseToJsonElement("0.2")
            )
        )

        RequestBodyMerge.mergeCustomBody(target, body)

        assertEquals(1, target.getJSONObject("metadata").getInt("a"))
        assertEquals(3, target.getJSONObject("metadata").getInt("b"))
        assertEquals(4, target.getJSONObject("metadata").getInt("c"))
        assertEquals(0.2, target.getDouble("temperature"), 0.0001)
    }
}
