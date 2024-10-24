package com.urbanairship.messagecenter

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message.Companion.create
import com.urbanairship.util.UAStringUtil

@Entity(
    tableName = "richpush",
    indices = [
        Index(value = ["message_id"], unique = true),
        Index(value = ["unread"]),
        Index(value = ["deleted"]),
        Index(value = ["expiration_timestamp"])
    ]
)
internal data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Int = 0,
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "message_url")
    val messageUrl: String?,
    @ColumnInfo(name = "message_body_url")
    val messageBodyUrl: String?,
    @ColumnInfo(name = "message_read_url")
    val messageReadUrl: String?,
    val title: String?,
    val extra: String?,
    val unread: Boolean,
    @ColumnInfo(name = "unread_orig")
    val unreadOrig: Boolean,
    @ColumnInfo(name = "deleted")
    val deleted: Boolean,
    val timestamp: String?,
    @ColumnInfo(name = "raw_message_object")
    val rawMessageObject: String,
    @ColumnInfo(name = "expiration_timestamp")
    val expirationTimestamp: String?
) {

    val messageReporting: JsonValue? by lazy {
        try {
            JsonValue.parseString(this.rawMessageObject).map?.let { json ->
                json[Message.KEY_MESSAGE_REPORTING]
            }
        } catch (e: JsonException) {
            UALog.e(e) { "MessageEntity - Failed to parse Message reporting." }
            null
        }
    }

    fun toMessage(): Message? = try {
        create(JsonValue.parseString(rawMessageObject), unread, deleted)
    } catch (e: JsonException) {
        UALog.e(e) { "Failed to create Message from JSON" }
        null
    }

    companion object {
        @Throws(JsonException::class)
        fun createMessageFromPayload(messageId: String?, payload: JsonValue): MessageEntity? =
            createMessageFromPayload(messageId, payload.optMap())

        @Throws(JsonException::class)
        fun createMessageFromPayload(messageId: String?, messagePayload: JsonMap): MessageEntity? {

            if (UAStringUtil.isEmpty(messagePayload.opt(Message.KEY_ID).string)) {
                UALog.e { "MessageEntity - Message is missing an ID: $messagePayload" }
                return null
            }

            return MessageEntity(
                messageId = messageId ?: messagePayload.opt(Message.KEY_ID).requireString(),
                messageUrl = messagePayload.opt(Message.KEY_MESSAGE_URL).string,
                messageBodyUrl = messagePayload.opt(Message.KEY_BODY_URL).string,
                messageReadUrl = messagePayload.opt(Message.KEY_MESSAGE_READ_URL).string,
                title = messagePayload.opt(Message.KEY_TITLE).string,
                extra = messagePayload.opt(Message.KEY_EXTRAS).string,
                unread = messagePayload.opt(Message.KEY_IS_UNREAD).getBoolean(true),
                unreadOrig = messagePayload.opt(Message.KEY_IS_UNREAD).getBoolean(true),
                deleted = false,
                timestamp = messagePayload.opt(Message.KEY_SENT_DATE).string,
                rawMessageObject = messagePayload.toString(),
                expirationTimestamp = messagePayload.opt(Message.KEY_EXPIRATION_DATE).string
            )
        }

        @Throws(JsonException::class)
        fun createMessagesFromPayload(messagePayloads: List<JsonValue>): List<MessageEntity> {
            val messageEntities = ArrayList<MessageEntity>()

            for (messagePayload in messagePayloads) {
                val messageEntity = createMessageFromPayload(null, messagePayload)
                if (messageEntity != null) {
                    messageEntities.add(messageEntity)
                }
            }

            return messageEntities
        }
    }
}
