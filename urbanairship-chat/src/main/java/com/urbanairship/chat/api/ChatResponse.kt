/* Copyright Airship and Contributors */

package com.urbanairship.chat.api

import androidx.annotation.RestrictTo
import com.urbanairship.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Sealed class representing various response and message payloads we may receive from the server.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Serializable
internal sealed class ChatResponse {
    companion object {
        private val format = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Attempts to parse the raw [text] received over the WebSocket into a `ChatResponse` type.
         *
         * This uses the `type` field and `@SerialName` annotations on the data classes below to
         * determine the correct class to parse into. If [text] is blank or
         * both parsing attempts fail, `null` will be returned.
         */
        internal fun parse(text: String): ChatResponse? {
            if (text.isBlank()) return null

            return try {
                format.decodeFromString<ChatResponse>(text)
            } catch (e1: Exception) {
                Logger.error(e1, "Failed to parse chat response payload: '$text'")
                null
            }
        }
    }

    /** Payload for the response returned after a new message is sent successfully. */
    @Serializable
    @SerialName("message_received")
    internal data class MessageReceived(
        @SerialName("payload") val message: MessageReceivedPayload
    ) : ChatResponse() {
        @Serializable
        internal data class MessageReceivedPayload(
            val success: Boolean,
            val message: Message
        )
    }

    /** Payload for the response returned after the conversation is requested. */
    @Serializable
    @SerialName("conversation_loaded")
    internal data class ConversationLoaded(
        @SerialName("payload") val conversation: ConversationPayload
    ) : ChatResponse() {
        @Serializable
        internal data class ConversationPayload(
            val messages: List<Message>?
        )
    }

    /** Payload for a new message in the conversation, sent by the other party. */
    @Serializable
    @SerialName("new_message")
    internal data class NewMessage(
        @SerialName("payload") val message: NewMessagePayload
    ) : ChatResponse() {
        @Serializable
        internal data class NewMessagePayload(
            val message: Message
        )
    }

    /** A message. */
    @Serializable
    data class Message(
        /** ID of the message. */
        @SerialName("message_id") val messageId: String,
        /** When the message was created. */
        @SerialName("created_on") val createdOn: String,
        /** Whether this message was sent (0) or received (1). */
        val direction: Int,
        /** Message text. */
        val text: String? = null,
        /** An attachment URL. */
        val attachment: String? = null,
        /** Request ID. */
        @SerialName("request_id") val requestId: String? = null
    )
}
