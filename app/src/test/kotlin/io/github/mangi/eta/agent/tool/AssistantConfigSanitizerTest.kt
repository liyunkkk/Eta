package io.github.mangi.eta.agent.tool

import io.github.mangi.eta.data.model.CustomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantConfigSanitizerTest {

    @Test
    fun masksIpv4AddressAndStripsQueryParams() {
        val rawUrl = "http://35.212.167.57:8000/v1?token=super_secret_token#anchor"
        val masked = AssistantConfigSanitizer.maskBaseUrl(rawUrl)
        assertEquals("http://35.212.***.***:8000/v1", masked)
        assertFalse(masked.contains("super_secret_token"))
        assertFalse(masked.contains("167.57"))
    }

    @Test
    fun preservesStandardDomainAndRemovesQuery() {
        val rawUrl = "https://api.openai.com/v1/chat/completions?auth_param=xyz"
        val masked = AssistantConfigSanitizer.maskBaseUrl(rawUrl)
        assertEquals("https://api.openai.com/v1/chat/completions", masked)
        assertFalse(masked.contains("auth_param"))
    }

    @Test
    fun masksLongAndShortApiKeys() {
        val longKey = "sk-gcp1-1234567890abcdef"
        val maskedLong = AssistantConfigSanitizer.maskApiKey(longKey)
        assertEquals("sk-g****cdef", maskedLong)

        val shortKey = "123456"
        val maskedShort = AssistantConfigSanitizer.maskApiKey(shortKey)
        assertEquals("****", maskedShort)

        val emptyKey = ""
        val maskedEmpty = AssistantConfigSanitizer.maskApiKey(emptyKey)
        assertEquals("", maskedEmpty)
    }

    @Test
    fun protectsHeaderValues() {
        val headers = listOf(
            CustomHeader(name = "X-Proxy-Auth", value = "super-secret-password-123"),
            CustomHeader(name = "Authorization", value = "Bearer sk-99999999")
        )
        val maskedHeaders = AssistantConfigSanitizer.maskHeaders(headers)
        assertTrue(maskedHeaders.has("X-Proxy-Auth"))
        assertEquals("[PROTECTED]", maskedHeaders.getString("X-Proxy-Auth"))
        assertTrue(maskedHeaders.has("Authorization"))
        assertEquals("[PROTECTED]", maskedHeaders.getString("Authorization"))
    }
}