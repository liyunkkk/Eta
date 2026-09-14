package io.github.mangi.eta.agent.model

internal object ProviderUrls {
    fun normalizeBaseUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/')

    fun openAiChatCompletionsUrl(baseUrl: String): String =
        appendPath(baseUrl, "chat/completions")

    fun openAiResponsesUrl(baseUrl: String): String =
        appendPath(baseUrl, "responses")

    fun openAiModelsUrl(baseUrl: String): String =
        appendPath(baseUrl, "models")

    /**
     * Anthropic Messages 端点。
     *
     * 兼容两种 baseUrl 写法：
     * - `https://api.anthropic.com`            -> `https://api.anthropic.com/v1/messages`
     * - `https://api.anthropic.com/v1`         -> `https://api.anthropic.com/v1/messages`（不产生 /v1/v1）
     * - `https://中转站.com`（如 justworker）    -> `https://中转站.com/v1/messages`
     * - `https://中转站.com/anthropic/v1`       -> `https://中转站.com/anthropic/v1/messages`（保留前缀）
     */
    fun anthropicMessagesUrl(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        return if (normalized.endsWith("/v1", ignoreCase = true)) {
            "$normalized/messages"
        } else {
            appendPath(normalized, "v1/messages")
        }
    }

    /**
     * Anthropic 模型列表端点，规则同 [anthropicMessagesUrl]。
     */
    fun anthropicModelsUrl(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        return if (normalized.endsWith("/v1", ignoreCase = true)) {
            "$normalized/models"
        } else {
            appendPath(normalized, "v1/models")
        }
    }

    private fun appendPath(baseUrl: String, path: String): String =
        "${normalizeBaseUrl(baseUrl)}/${path.trimStart('/')}"
}
