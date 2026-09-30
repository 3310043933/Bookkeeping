package com.example.foodledger.recognition

enum class ModelProvider(
    val displayName: String,
    val keyUrl: String,
    val modelName: String
) {
    OPENAI("OpenAI", "https://platform.openai.com/api-keys", "gpt-4.1-mini"),
    DEEPSEEK("DeepSeek", "https://platform.deepseek.com/api_keys", "deepseek-flash"),
    GEMINI("Google Gemini", "https://aistudio.google.com/app/apikey", "gemini-2.5-flash"),
    ANTHROPIC("Anthropic Claude", "https://console.anthropic.com/settings/keys", "claude-sonnet-4-5"),
    QWEN("阿里云通义千问", "https://bailian.console.aliyun.com/?apiKey=1", "qwen-vl-plus")
}
