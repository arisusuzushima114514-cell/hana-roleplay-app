package com.hana.app.data.settings

enum class AppLanguage(
    val code: String,
    val displayName: String
) {
    SimplifiedChinese(code = "zh-CN", displayName = "\u7B80\u4F53\u4E2D\u6587"),
    TraditionalChinese(code = "zh-TW", displayName = "\u7E41\u9AD4\u4E2D\u6587"),
    Japanese(code = "ja", displayName = "\u65E5\u672C\u8A9E"),
    English(code = "en", displayName = "English");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code == code } ?: SimplifiedChinese
        }
    }
}

const val DEFAULT_LANGUAGE_CODE = "zh-CN"
