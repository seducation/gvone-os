package com.example.agent.bus

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

enum class MessageType {
    REQUEST,
    RESPONSE,
    BROADCAST,
    ERROR,
    STATUS,
    CANCEL
}

data class AgentMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: MessageType,
    val from: String,
    val to: String? = null,
    val payload: Any? = null,
    val correlationId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class AgentMessageBus {
    private val _messages = MutableSharedFlow<AgentMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<AgentMessage> = _messages.asSharedFlow()

    suspend fun send(message: AgentMessage) {
        _messages.emit(message)
    }

    suspend fun broadcast(from: String, payload: Any?, type: MessageType = MessageType.BROADCAST) {
        _messages.emit(
            AgentMessage(
                type = type,
                from = from,
                to = null,
                payload = payload
            )
        )
    }

    suspend fun reply(original: AgentMessage, from: String, payload: Any?) {
        _messages.emit(
            AgentMessage(
                type = MessageType.RESPONSE,
                from = from,
                to = original.from,
                payload = payload,
                correlationId = original.id
            )
        )
    }

    companion object {
        val global = AgentMessageBus()
    }
}
