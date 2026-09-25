package com.guruswarupa.launch.ai.llm

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
