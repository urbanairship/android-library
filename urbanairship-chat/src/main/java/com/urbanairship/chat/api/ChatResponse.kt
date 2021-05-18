/* Copyright Airship and Contributors */

package com.urbanairship.chat.api

import androidx.annotation.RestrictTo
import com.urbanairship.Logger
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Sealed class representing various response and message payloads we may receive from the server.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal sealed class ChatResponse {
    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_SUCCESS = "success"
        private const val KEY_MESSAGE = "message"
        private const val KEY_MESSAGES = "messages"
        private const val KEY_MESSAGE_ID = "message_id"
        private const val KEY_CREATED_ON = "created_on"
        private const val KEY_DIRECTION = "direction"
        private const val KEY_TEXT = "text"
        private const val KEY_ATTACHMENT = "attachment"
        private const val KEY_REQUEST_ID = "request_id"

        private const val TYPE_MESSAGE_RECEIVED = "message_received"
        private const val TYPE_CONVERSATION_LOADED = "conversation_loaded"
        private const val TYPE_NEW_MESSAGE = "new_message"

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
                val jsonMap = JsonValue.parseString(text).optMap()
                when (val type = jsonMap.opt(KEY_TYPE).optString()) {
                    TYPE_MESSAGE_RECEIVED -> MessageReceived.parse(text)
                    TYPE_CONVERSATION_LOADED -> ConversationLoaded.parse(text)
                    TYPE_NEW_MESSAGE -> NewMessage.parse(text)
                    else -> throw JsonException("Unknown response type: '$type'")
                }
            } catch (e: Exception) {
                Logger.error(e, "Failed to parse chat response payload: '$text'")
                null
            }
        }
    }

    /** Payload for the response returned after a new message is sent successfully. */
    internal data class MessageReceived(val message: MessageReceivedPayload) : ChatResponse() {
        companion object {
            internal fun parse(json: String): MessageReceived {
                val jsonMap = JsonValue.parseString(json).optMap()
                val payloadJson = jsonMap.opt(KEY_PAYLOAD).optMap()
                val success = requireNotNull(payloadJson.opt(KEY_SUCCESS).boolean) { "'$KEY_SUCCESS' may not be null!" }
                val messageJson = payloadJson.opt(KEY_MESSAGE).optMap()
                val message = Message.fromJsonMap(messageJson)

                return MessageReceived(message = MessageReceivedPayload(success, message))
            }
        }

        internal data class MessageReceivedPayload(
            val success: Boolean,
            val message: Message
        )
    }

    /** Payload for the response returned after the conversation is requested. */
    internal data class ConversationLoaded(val conversation: ConversationPayload) : ChatResponse() {
        companion object {
            internal fun parse(json: String): ConversationLoaded {
                val jsonMap = JsonValue.parseString(json).optMap()
                val payloadJson = jsonMap.opt(KEY_PAYLOAD).optMap()
                val messages = payloadJson.opt(KEY_MESSAGES).list?.map {
                    Message.fromJsonMap(it.optMap())
                } ?: listOf()

                return ConversationLoaded(conversation = ConversationPayload(messages = messages))
            }
        }

        internal data class ConversationPayload(
            val messages: List<Message>?
        )
    }

    /** Payload for a new message in the conversation, sent by the other party. */
    internal data class NewMessage(val message: NewMessagePayload) : ChatResponse() {
        companion object {
            internal fun parse(json: String): NewMessage {
                val jsonMap = JsonValue.parseString(json).optMap()
                val payloadJson = jsonMap.opt(KEY_PAYLOAD).optMap()
                val message = Message.fromJsonMap(payloadJson.opt(KEY_MESSAGE).optMap())

                return NewMessage(message = NewMessagePayload(message))
            }
        }
        internal data class NewMessagePayload(
            val message: Message
        )
    }

    /** A message. */
    data class Message(
        /** ID of the message. */
        val messageId: String,
        /** When the message was created. */
        val createdOn: String,
        /** Whether this message was sent (0) or received (1). */
        val direction: Int,
        /** Message text. */
        val text: String? = null,
        /** An attachment URL. */
        val attachment: String? = null,
        /** Request ID. */
        val requestId: String? = null
    ) {
        companion object {
            internal fun fromJsonMap(jsonMap: JsonMap): Message {
                val messageIdLong = jsonMap.opt(KEY_MESSAGE_ID).getLong(0)
                if (messageIdLong == 0L) {
                    throw JsonException("'$KEY_MESSAGE_ID' may not be null!")
                }
                val messageId = messageIdLong.toString()

                val createdOn = requireNotNull(jsonMap.opt(KEY_CREATED_ON).string) { "'$KEY_CREATED_ON' may not be null!" }
                val direction = jsonMap.opt(KEY_DIRECTION).getInt(-1)
                require(direction != -1) { "'$KEY_DIRECTION' is invalid!" }

                val text = jsonMap.opt(KEY_TEXT).string
                val attachment = jsonMap.opt(KEY_ATTACHMENT).string
                val requestId = jsonMap.opt(KEY_REQUEST_ID).string

                return Message(
                        messageId = messageId,
                        createdOn = createdOn,
                        direction = direction,
                        text = text,
                        attachment = attachment,
                        requestId = requestId
                )
            }
        }
    }
}
