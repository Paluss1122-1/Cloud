package com.cloud.tabs.aitab

data class Model(
    val realname: String,
    val vision: Boolean = false,
    val name: String = realname.substringAfter("/", realname)
)

val nvidiaModels = listOf(
    Model("meta/llama-3.1-8b-instruct"),
    Model("utter-project/eurollm-9b-instruct"),
    Model("google/gemma-2-9b-it"),
    Model("openai/gpt-oss-120b"),
    Model("openai/gpt-oss-20b"),
    Model("minimaxai/minimax-m2.5"),
    Model("bigcode/starcoder2-7b"),
    Model("nvidia/nemotron-3-nano-30b-a3b"),
    Model("nvidia/nemoretriever-ocr-v1", true),
    Model("meta/llama-3.2-90b-vision-instruct", true),
    Model("meta/llama-4-maverick-17b-128e-instruct", true),
    Model("qwen/qwen3.5-397b-a17b", true),
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