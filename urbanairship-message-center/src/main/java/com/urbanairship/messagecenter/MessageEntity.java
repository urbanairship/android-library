package com.urbanairship.messagecenter;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "richpush", indices = {
    @Index(value = {"message_id"}, unique = true)
})
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class MessageEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    protected int id;

    @ColumnInfo(name = "message_id")
    public String messageId;
    @ColumnInfo(name = "message_url")
    public String messageUrl;
    @ColumnInfo(name = "message_body_url")
    public String messageBodyUrl;
    @ColumnInfo(name = "message_read_url")
    public String messageReadUrl;
    public String title;
    public String extra;
    public boolean unread;
    @ColumnInfo(name = "unread_orig")
    public boolean unreadOrig;
    public boolean deleted;
    public String timestamp;
    @ColumnInfo(name = "raw_message_object")
    public String rawMessageObject;
    @ColumnInfo(name = "expiration_timestamp")
    public String expirationTimestamp;

    public MessageEntity(String messageId, String messageUrl, String messageBodyUrl,
                         String messageReadUrl, String title, String extra, boolean unread,
                         boolean unreadOrig, boolean deleted, String timestamp, String rawMessageObject,
                         String expirationTimestamp) {
        this.messageId = messageId;
        this.messageUrl = messageUrl;
        this.messageBodyUrl = messageBodyUrl;
        this.messageReadUrl = messageReadUrl;
        this.title = title;
        this.extra = extra;
        this.unread = unread;
        this.unreadOrig = unreadOrig;
        this.deleted = deleted;
        this.timestamp = timestamp;
        this.rawMessageObject = rawMessageObject;
        this.expirationTimestamp = expirationTimestamp;
    }

    @Nullable
    static protected MessageEntity createMessageFromPayload(@Nullable String messageId, @NonNull JsonValue messagePayload) {
        if (messagePayload == JsonValue.NULL || !messagePayload.isJsonMap()) {
            Logger.error("MessageEntity - Unexpected message: %s", messagePayload);
            return null;
        }

        JsonMap messageMap = messagePayload.optMap();

        if (UAStringUtil.isEmpty(messageMap.opt(Message.MESSAGE_ID_KEY).getString())) {
            Logger.error("MessageEntity - Message is missing an ID: %s", messagePayload);
            return null;
        }

        return new MessageEntity(
                messageId != null ? messageId : messageMap.opt(Message.MESSAGE_ID_KEY).getString(),
                messageMap.opt(Message.MESSAGE_URL_KEY).getString(),
                messageMap.opt(Message.MESSAGE_BODY_URL_KEY).getString(),
                messageMap.opt(Message.MESSAGE_READ_URL_KEY).getString(),
                messageMap.opt(Message.TITLE_KEY).getString(),
                messageMap.opt(Message.EXTRA_KEY).getString(),
                messageMap.opt(Message.UNREAD_KEY).getBoolean(true),
                messageMap.opt(Message.UNREAD_KEY).getBoolean(true),
                false,
                messageMap.opt(Message.MESSAGE_SENT_KEY).getString(),
                messageMap.toString(),
                messageMap.containsKey(Message.MESSAGE_EXPIRY_KEY) ? messageMap.opt(Message.MESSAGE_EXPIRY_KEY).getString() : null);
    }

    @NonNull
    static protected List<MessageEntity> createMessagesFromPayload(@NonNull List<JsonValue> messagePayloads) {
        ArrayList<MessageEntity> messageEntities = new ArrayList<>();

        for (JsonValue messagePayload : messagePayloads) {
            MessageEntity messageEntity = createMessageFromPayload(null, messagePayload);
            if (messageEntity != null) {
                messageEntities.add(messageEntity);
            }
        }

        return messageEntities;
    }

    public String getMessageId() {
        return messageId;
    }

    @Nullable
    protected JsonValue getMessageReporting() {
        try {
            JsonMap messageMap = JsonValue.parseString(this.rawMessageObject).getMap();
            if (messageMap != null) {
                return messageMap.get(Message.MESSAGE_REPORTING_KEY);
            }
        } catch (JsonException e) {
            Logger.error(e, "MessageEntity - Failed to parse Message reporting.");
        }
        return null;
    }

    public boolean isDeleted() {
        return this.deleted;
    }

    protected Message createMessageFromEntity(MessageEntity entity) {
        try {
            return Message.create(JsonValue.parseString(entity.rawMessageObject), entity.unread, entity.deleted);
        } catch (JsonException e) {
            Logger.error("Failed to create Message from JSON");
            return null;
        }
    }
}
