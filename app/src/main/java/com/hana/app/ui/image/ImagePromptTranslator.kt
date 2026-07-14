package com.hana.app.ui.image

import java.util.Locale

data class ImagePromptForm(
    val subject: String = "",
    val scene: String = "",
    val styleLabel: String = ImageStyles.DEFAULT.labelZh,
    val mood: String = "",
    val lighting: String = "",
    val camera: String = "",
    val detailBoost: Boolean = true,
    val batchCount: Int = 4,
    val aspectRatio: String = "1:1",
    val negativePromptZh: String = "低清晰度, 手部畸形, 多余肢体, 模糊, 水印, 文字"
)

data class PromptBuildResult(
    val chineseSummary: String,
    val englishPrompt: String,
    val negativePromptEnglish: String,
    val launchUrl: String
)

data class ImageStylePreset(
    val labelZh: String,
    val englishPrompt: String
)

object ImageStyles {
    val DEFAULT = ImageStylePreset("电影感插画", "cinematic illustration, highly detailed, polished lighting")

    val presets = listOf(
        DEFAULT,
        ImageStylePreset("写实摄影", "photorealistic, realistic skin texture, natural color grading"),
        ImageStylePreset("二次元", "anime style, clean line art, expressive eyes, vibrant colors"),
        ImageStylePreset("游戏概念设定", "concept art, artstation quality, dramatic composition, production design"),
        ImageStylePreset("赛博朋克", "cyberpunk, neon glow, futuristic city lights, dense atmosphere"),
        ImageStylePreset("国风幻想", "Chinese fantasy, elegant costume design, ethereal atmosphere, ornate details"),
        ImageStylePreset("像素风", "pixel art, retro game palette, crisp edges, sprite-like rendering")
    )

    fun findByLabel(label: String): ImageStylePreset {
        return presets.firstOrNull { it.labelZh == label } ?: DEFAULT
    }
}

private val cnToEnDictionary = linkedMapOf(
    "少女" to "young woman",
    "男生" to "young man",
    "猫娘" to "catgirl",
    "机甲" to "mecha",
    "古风" to "ancient Chinese style",
    "宫殿" to "palace",
    "森林" to "forest",
    "海边" to "seaside",
    "夜景" to "night scene",
    "日落" to "sunset",
    "月光" to "moonlight",
    "雨天" to "rainy weather",
    "微笑" to "gentle smile",
    "全身" to "full body",
    "半身" to "half body portrait",
    "特写" to "close-up portrait",
    "红色" to "red",
    "蓝色" to "blue",
    "白色" to "white",
    "黑色" to "black",
    "金色" to "golden",
    "花海" to "sea of flowers",
    "星空" to "starry sky",
    "礼服" to "formal dress",
    "战甲" to "battle armor",
    "翅膀" to "wings",
    "长发" to "long hair",
    "短发" to "short hair",
    "精致" to "exquisite",
    "高清" to "high definition"
)

private val negativeDictionary = linkedMapOf(
    "低清晰度" to "low quality",
    "手部畸形" to "deformed hands",
    "多余肢体" to "extra limbs",
    "模糊" to "blurry",
    "水印" to "watermark",
    "文字" to "text",
    "重复脸" to "duplicate face",
    "脏背景" to "messy background",
    "低分辨率" to "low resolution"
)

object ImagePromptTranslator {
    private const val BASE_WEB_URL = "https://perchance.org/ai-text-to-image-generator"

    fun build(form: ImagePromptForm): PromptBuildResult {
        val style = ImageStyles.findByLabel(form.styleLabel)
        val englishSegments = buildList {
            add(translateFreeText(form.subject))
            if (form.scene.isNotBlank()) add(translateFreeText(form.scene))
            add(style.englishPrompt)
            if (form.mood.isNotBlank()) add(translateFreeText(form.mood))
            if (form.lighting.isNotBlank()) add(translateFreeText(form.lighting))
            if (form.camera.isNotBlank()) add(translateFreeText(form.camera))
            add(aspectRatioToPrompt(form.aspectRatio))
            if (form.detailBoost) add("masterpiece, best quality, intricate details, sharp focus")
        }.filter { it.isNotBlank() }

        val englishPrompt = englishSegments.joinToString(", ")
        val negativePromptEnglish = translateNegativeText(form.negativePromptZh)
        val chineseSummary = buildChineseSummary(form)
        val launchUrl = "$BASE_WEB_URL?prompt=${urlEncode(englishPrompt)}"

        return PromptBuildResult(
            chineseSummary = chineseSummary,
            englishPrompt = englishPrompt,
            negativePromptEnglish = negativePromptEnglish,
            launchUrl = launchUrl
        )
    }

    fun extractedRulesZh(): List<String> = listOf(
        "来源于 bug/生图.txt 的页面标题与配置：这是一个无限制文生图网页，核心输入仍然是 text-to-image prompt。",
        "我提炼出的稳定结构是：主体 + 场景 + 风格 + 情绪 + 光线 + 镜头 + 质量词 + 负面词。",
        "中文可直接写，页面会先翻译整理成英文提示词，避免你手写长串英文。",
        "负面词会单独生成，适合抑制模糊、畸形手、低清等常见问题。",
        "当前版本优先做稳定 prompt 工作流，不依赖脆弱的网页 DOM 注入脚本。"
    )

    private fun translateFreeText(text: String): String {
        if (text.isBlank()) return ""
        var normalized = text.trim()
        cnToEnDictionary.forEach { (cn, en) ->
            normalized = normalized.replace(cn, en)
        }
        return normalized
            .replace("，", ", ")
            .replace("。", ", ")
            .replace("；", ", ")
            .replace("：", ": ")
            .replace(Regex("\\s+"), " ")
            .trim(',', ' ')
    }

    private fun translateNegativeText(text: String): String {
        if (text.isBlank()) return ""
        return text.split(',', '，', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { negativeDictionary[it] ?: translateFreeText(it).lowercase(Locale.US) }
            .distinct()
            .joinToString(", ")
    }

    private fun aspectRatioToPrompt(value: String): String {
        return when (value) {
            "16:9" -> "wide composition, cinematic frame"
            "9:16" -> "vertical composition, mobile wallpaper framing"
            "4:3" -> "balanced composition, classic frame"
            else -> "square composition"
        }
    }

    private fun buildChineseSummary(form: ImagePromptForm): String {
        return buildString {
            append("主体：")
            append(form.subject.ifBlank { "未填写" })
            if (form.scene.isNotBlank()) append("｜场景：${form.scene}")
            append("｜风格：${form.styleLabel}")
            if (form.mood.isNotBlank()) append("｜氛围：${form.mood}")
            if (form.lighting.isNotBlank()) append("｜光线：${form.lighting}")
            if (form.camera.isNotBlank()) append("｜镜头：${form.camera}")
            append("｜画幅：${form.aspectRatio}")
            append("｜数量：${form.batchCount}")
        }
    }

    private fun urlEncode(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
