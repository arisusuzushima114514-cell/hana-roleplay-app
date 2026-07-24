package com.hana.app.viewmodel

/** 检测模型是否为思考型（支持 reasoning_content） */
fun isReasoningModel(modelName: String?): Boolean {
    if (modelName.isNullOrBlank()) return false
    val lower = modelName.lowercase()
    return listOf("r1", "reasoner", "reasoning", "o1-", "o3-", "o4-", "deepseek-chat", "qwq").any { lower.contains(it) }
}

/** 检测模型是否为 flash / 快速生成型（无思考阶段） */
fun isFlashModel(modelName: String?): Boolean {
    if (modelName.isNullOrBlank()) return false
    val lower = modelName.lowercase()
    return listOf("flash", "mini", "turbo", "lite", "qwen2.5", "gpt-4o-mini", "gpt-3.5").any { lower.contains(it) }
        && !isReasoningModel(modelName)
}