package com.hana.app.data.model

data class Message(
    val role: String,
    val content: String
) {
    enum class Role(val value: String) {
        System("system"),
        User("user"),
        Assistant("assistant")
    }
}
