package com.guruswarupa.launch.ai.llm

/**
 * A cloud AI assistant reached by loading its real website in an embedded WebView, as an
 * alternative to the on-device models in [AssistantModel]. Unlike those, this needs network
 * access every time and (for most of these) a login on that provider's own site — the
 * trade-off is access to a far larger hosted model than anything that fits on a phone.
 */
data class WebAiProviderInfo(
    val id: String,
    val displayName: String,
    val url: String
)

object WebAiProvider {
    val CHATGPT = WebAiProviderInfo("chatgpt", "ChatGPT", "https://chat.openai.com")
    val CLAUDE = WebAiProviderInfo("claude", "Claude", "https://claude.ai")
    val GEMINI = WebAiProviderInfo("gemini", "Gemini", "https://gemini.google.com")
    val COPILOT = WebAiProviderInfo("copilot", "Copilot", "https://copilot.microsoft.com")
    val PERPLEXITY = WebAiProviderInfo("perplexity", "Perplexity", "https://www.perplexity.ai")
    val DEEPSEEK = WebAiProviderInfo("deepseek", "DeepSeek", "https://chat.deepseek.com")

    val ALL: List<WebAiProviderInfo> = listOf(CHATGPT, CLAUDE, GEMINI, COPILOT, PERPLEXITY, DEEPSEEK)

    fun byId(id: String?): WebAiProviderInfo? = ALL.firstOrNull { it.id == id }
}
