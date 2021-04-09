package com.urbanairship.chat

import android.net.Uri
import androidx.annotation.RestrictTo
import com.urbanairship.chat.ChatRequest.SendMessage.MessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/** Data classes for building request payloads to be sent to the server. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Serializable
internal sealed class ChatRequest(val action: String, val origin: String = ORIGIN) {
    companion object {
        private const val ACTION_GET_CONVERSATION = "fetch_conversation"
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
    internal data class GetConversation(
        override val uvp: String
    ) : ChatRequest(ACTION_GET_CONVERSATION) {
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
        // 'payload' is a JSON encoded string, so we need to specify a special serializer
        @Serializable(with = PayloadAsStringSerializer::class) val payload: MessagePayload
    ) : ChatRequest(ACTION_SEND_MESSAGE) {
        /** Creates a normal message with the given [text]. */
        internal constructor(
            uvp: String,
            text: String,
            requestId: String
        ) : this(uvp, MessagePayload(Message(requestId, text, null)))

        /** Creates an attachment message from the given [attachment] `Uri`. */
        internal constructor(
            uvp: String,
            attachment: Uri,
            requestId: String
        ) : this(uvp, MessagePayload(Message(requestId, null, attachment.toString())))

        @Serializable
        internal data class MessagePayload(
            val message: Message
        )

        @Serializable
        internal data class Message(
            @SerialName("request_id") val requestId: String,
            val text: String?,
            val attachment: String?
        ) {
            init {
                require(!(text == null && attachment == null)) {
                    "Missing required text OR attachment!"
                }
                require(!(text != null && attachment != null)) {
                    "Specify either text OR attachment"
                }
            }
        }

        override fun toJsonString(): String = format.encodeToString(serializer(), this)
    }

    /**
     * Serializer used for the `payload` field, which needs to be a JSON-encoded String
     * representation of the payload instead of a regular JSON Object.
     */
    private object PayloadAsStringSerializer : KSerializer<MessagePayload> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(javaClass.name, PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: MessagePayload) =
            encoder.encodeString(format.encodeToString(MessagePayload.serializer(), value))

        override fun deserialize(decoder: Decoder): MessagePayload =
            format.decodeFromString(MessagePayload.serializer(), decoder.decodeString())
    }
}
