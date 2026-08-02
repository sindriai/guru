package com.sindriai.guru.data.chatsession

import java.util.UUID

data class ChatHistory(
    val chatHistory: List<Message>
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val sender: Sender,
    val content: String
)

enum class Sender {
    USER,
    GURU
}