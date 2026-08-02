package com.sindriai.guru.data.chatsession

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ChatHistoryManager(
    private val context: Context
) {

    /* ---------------- File Handling ---------------- */

    private val chatDir = File(context.filesDir, "chat")
    private var chatFile: File? = null
    private var currentConceptAddress: String? = null

    /* ---------------- State ---------------- */

    private val _chatHistory =
        MutableStateFlow(ChatHistory(chatHistory = emptyList()))
    val chatHistory: StateFlow<ChatHistory> = _chatHistory.asStateFlow()

    init {
        if (!chatDir.exists()) {
            chatDir.mkdirs()
        }
    }

    /* ================ PUBLIC API ============================= */

    /**
     * Loads chat history for given topic ID.
     * Example ID: P001C0010201
     */
    fun loadTopic(conceptAddress: String) {

        // Prevent unnecessary reload
        if (conceptAddress == currentConceptAddress) return

        currentConceptAddress = conceptAddress

        if (!chatDir.exists()) {
            chatDir.mkdirs()
        }

        chatFile = File(chatDir, "$conceptAddress.txt")

        if (!chatFile!!.exists()) {
            chatFile!!.createNewFile()
        }

        restoreFromFile()
    }

    fun addUserMessage(message: String) {
        appendMessage(Sender.USER, message)
    }

    fun addGuruMessage(message: String) {
        appendMessage(Sender.GURU, message)
    }

    fun getSnapshot(): List<Message> {
        return _chatHistory.value.chatHistory
    }

    fun clear() {
        val file = chatFile ?: return
        file.writeText("")
        _chatHistory.value = ChatHistory(chatHistory = emptyList())
    }

    /* ==================== INTERNAL LOGIC ========================= */

    private fun appendMessage(sender: Sender, message: String) {
        val file = chatFile ?: return
        if (message.isBlank()) return

        // Encode newlines so a single message can't corrupt the one-message-per-line file format.
        val encoded = message.replace("\n", "\\n")

        val updated = _chatHistory.value.chatHistory + Message(
            sender = sender,
            content = message  // keep the real newlines in memory/UI
        )
        _chatHistory.value = ChatHistory(chatHistory = updated)

        file.appendText("${sender.name}|$encoded\n")
    }

    private fun restoreFromFile() {
        val file = chatFile ?: return

        val messages = file.readLines()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size != 2) return@mapNotNull null

                try {
                    Message(
                        sender = Sender.valueOf(parts[0]),
                        content = parts[1].replace("\\n", "\n")  // decode back
                    )
                } catch (e: Exception) {
                    null
                }
            }

        _chatHistory.value = ChatHistory(chatHistory = messages)
    }
}
