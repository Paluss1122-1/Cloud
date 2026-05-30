package com.cloud.tabs.aitab

data class Model(
    val realname: String,
    val vision: Boolean = false,
    val audio: Boolean = false,
    val weight: Int = 1,
    val name: String = realname.substringAfter("/", realname)
)

val geminiModels = listOf(
    Model("gemini-3.1-flash-lite", vision = true, audio = true, weight = 2),
    Model("gemini-3-flash-preview", vision = true, audio = true, weight = 2),
    Model("gemini-2.5-flash", vision = true, audio = true, weight = 2),
    Model("gemini-2.5-flash-lite", vision = true, audio = true, weight = 1)
)

val nvidiaModels = listOf(
    Model("meta/llama-3.1-8b-instruct", weight = 1),
    Model("openai/gpt-oss-120b", weight = 2),
    Model("openai/gpt-oss-20b", weight = 1),
    Model("nvidia/nemotron-3-nano-30b-a3b", weight = 2),
    Model("nvidia/nemoretriever-ocr-v1", vision = true, weight = 1),
    Model("meta/llama-3.2-90b-vision-instruct", vision = true, weight = 2),
    Model("meta/llama-4-maverick-17b-128e-instruct", vision = true, weight = 2),
    Model("qwen/qwen3.5-397b-a17b", vision = true, weight = 4)
)

val serverModels = listOf(
    Model("qwen2.5:7b"),
    Model("qwen2.5-coder:3b"),
    Model("qwen2.5-coder:7b"),
    Model("qwen2.5-coder:14b"),
    Model("qwen3-coder-next:cloud"),
    Model("qwen3-vl:235b-cloud", true),
    Model("llava:13b", true),
    Model("llama3.2-vision:11b", true),
)