/* Copyright Airship and Contributors */

package com.urbanairship.chat.api

import androidx.annotation.RestrictTo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Data classes for building request payloads to be sent to the server. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Serializable
internal sealed class ChatRequest(val action: String, val origin: String = ORIGIN) {
    companion object {
        private const val ACTION_FETCH_CONVERSATION = "fetch_conversation"
        private const val ACTION_SEND_MESSAGE = "send_message"
        private const val ORIGIN = "mobile_android"

        private val format = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }

    abstract val uvp: String
    internal abstract fun toJsonString(): String

    /**
     * Requests the current conversation for the given [uvp] from the server.
     *
     * If successful, a new `conversation_loaded` message will be returned over the WebSocket.
     * @see ChatResponse.ConversationLoaded
     */
    @Serializable
    internal data class FetchConversation(
        override val uvp: String
    ) : ChatRequest(ACTION_FETCH_CONVERSATION) {
        override fun toJsonString(): String = format.encodeToString(serializer(), this)
    }

    /**
     * Sends a new message in the conversation.
     *
     * If successful, a new `message_received` message will be returned over the WebSocket.
     * @see ChatResponse.MessageReceived
     */
    @Serializable
    internal data class SendMessage(
        override val uvp: String,
        val payload: Message
    ) : ChatRequest(ACTION_SEND_MESSAGE) {
        internal constructor(
            uvp: String,
            text: String? = null,
            attachment: String? = null,
            requestId: String
        ) : this(uvp, Message(requestId, text, attachment))

        @Serializable
        internal data class Message(
            @SerialName("request_id") val requestId: String,
            val text: String? = null,
            val attachment: String? = null
        )

        override fun toJsonString(): String = format.encodeToString(serializer(), this)
    }
}
