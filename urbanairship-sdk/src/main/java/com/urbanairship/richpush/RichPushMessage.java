/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAStringUtil;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;

/**
 * The primary data structure for Rich Push messages.
 */
public class RichPushMessage implements Comparable<RichPushMessage> {

    // JSON KEYS
    final static String MESSAGE_EXPIRY_KEY = "message_expiry";
    final static String MESSAGE_ID_KEY = "message_id";
    final static String MESSAGE_URL_KEY = "message_url";
    final static String MESSAGE_BODY_URL_KEY = "message_body_url";
    final static String MESSAGE_READ_URL_KEY = "message_read_url";
    final static String MESSAGE_SENT_KEY = "message_sent";
    final static String EXTRA_KEY = "extra";
    final static String TITLE_KEY = "title";
    final static String UNREAD_KEY = "unread";

    private boolean unreadOrigin;
    private Bundle extras;
    private long sentMS;
    private Long expirationMS;
    private String messageId;
    private String messageUrl;
    private String messageBodyUrl;
    private String messageReadUrl;
    private String title;
    private JsonValue rawJson;

    // Accessed directly from RichPushInbox
    boolean deleted = false;
    boolean unreadClient;

    private RichPushMessage() {
    }

    /**
     * Factory method to create a RichPushMessage.
     *
     * @param messagePayload The raw message payload.
     * @param unreadClient flag indicating the read status on the client.
     * @param deleted flag indication the delete status.
     * @return A RichPushMessage instance, or {@code null} if the message payload is invalid.
     */
    static RichPushMessage create(JsonValue messagePayload, boolean unreadClient, boolean deleted) {
        JsonMap messageMap = messagePayload.getMap();
        if (messageMap == null) {
            return null;
        }


        RichPushMessage message = new RichPushMessage();
        message.messageId = messageMap.opt(MESSAGE_ID_KEY).getString();
        message.messageUrl = messageMap.opt(MESSAGE_URL_KEY).getString();
        message.messageBodyUrl = messageMap.opt(MESSAGE_BODY_URL_KEY).getString();
        message.messageReadUrl = messageMap.opt(MESSAGE_READ_URL_KEY).getString();
        message.title = messageMap.opt(TITLE_KEY).getString();
        message.unreadOrigin = messageMap.opt(UNREAD_KEY).getBoolean(true);
        message.rawJson = messagePayload;

        String sentMS = messageMap.opt(MESSAGE_SENT_KEY).getString();
        if (UAStringUtil.isEmpty(sentMS)) {
            message.sentMS = System.currentTimeMillis();
        } else {
            message.sentMS = DateUtils.parseIso8601(sentMS, System.currentTimeMillis());
        }

        String messageExpiry = messageMap.opt(MESSAGE_EXPIRY_KEY).getString();
        if (!UAStringUtil.isEmpty(messageExpiry)) {
            message.expirationMS = DateUtils.parseIso8601(messageExpiry, Long.MAX_VALUE);
        }

        // Extras
        message.extras = new Bundle();
        JsonMap extrasMap = messageMap.opt(EXTRA_KEY).getMap();
        if (extrasMap != null) {
            for (Map.Entry<String, JsonValue> entry : extrasMap) {
                if (entry.getValue().isString()) {
                    message.extras.putString(entry.getKey(), entry.getValue().getString());
                } else {
                    message.extras.putString(entry.getKey(), entry.getValue().toString());
                }
            }
        }

        message.deleted = deleted;
        message.unreadClient = unreadClient;

        return message;
    }

    /**
     * Get the message's Urban Airship ID.
     *
     * @return The message id.
     */
    public String getMessageId() {
        return this.messageId;
    }

    /**
     * Get the message URL.
     *
     * @return The message URL.
     */
    public String getMessageUrl() {
        return this.messageUrl;
    }

    /**
     * Get the message body URL.
     *
     * @return The message body URL.
     */
    public String getMessageBodyUrl() {
        return this.messageBodyUrl;
    }

    /**
     * Get the message mark-as-read URL.
     *
     * @return The message mark-as-read URL.
     */
    public String getMessageReadUrl() {
        return this.messageReadUrl;
    }

    /**
     * Get the message's title.
     *
     * @return The message title.
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Indicates whether the message has been read.
     *
     * @return <code>true</code> if the message is read, <code>false</code> otherwise.
     */
    public boolean isRead() {
        return !this.unreadClient;
    }

    /**
     * Get the message's sent date in UTC.
     *
     * @return The message's sent date.
     */
    public Date getSentDate() {
        return new Date(sentMS);
    }

    /**
     * Get the message's sent date (unix epoch time in milliseconds).
     *
     * @return The message's sent date (unix epoch time in milliseconds).
     */
    public long getSentDateMS() {
        return this.sentMS;
    }

    /**
     * Get the message's expiration date in UTC.
     *
     * @return The message's sent expiration date or null if the message does
     * not have an expiration date.
     */
    public Date getExpirationDate() {
        if (expirationMS != null) {
            return new Date(expirationMS);
        }
        return null;
    }

    /**
     * Get the message's expiration date (unix epoch time in milliseconds).
     *
     * @return The message's expiration date (unix epoch time in milliseconds),
     * or null if the message does not have an expiration date.
     */
    public Long getExpirationDateMS() {
        return expirationMS;
    }

    /**
     * Indicates whether the message has been expired.
     *
     * @return <code>true</code> if expired, otherwise <code>false</code>.
     */
    public boolean isExpired() {
        return expirationMS != null && System.currentTimeMillis() >= expirationMS;
    }

    /**
     * Get the message's extras.
     *
     * @return The message's extras in a {@link android.os.Bundle}.
     */
    public Bundle getExtras() {
        return this.extras;
    }

    // actions

    /**
     * Mark the message as read.
     */
    public void markRead() {
        if (this.unreadClient) {
            unreadClient = false;
            HashSet<String> set = new HashSet<>();
            set.add(messageId);
            UAirship.shared().getInbox().markMessagesRead(set);
        }
    }

    /**
     * Mark the message as unread.
     */
    public void markUnread() {
        if (!this.unreadClient) {
            unreadClient = true;
            HashSet<String> set = new HashSet<>();
            set.add(messageId);
            UAirship.shared().getInbox().markMessagesUnread(set);
        }
    }

    /**
     * Delete the message.
     */
    public void delete() {
        if (!this.deleted) {
            deleted = true;
            HashSet<String> set = new HashSet<>();
            set.add(messageId);
            UAirship.shared().getInbox().deleteMessages(set);
        }
    }

    /**
     * Gets the entire raw message payload as JSON.
     *
     * @return The message's payload as JSON.
     */
    public JsonValue getRawMessageJson() {
        return rawJson;
    }

    /**
     * Indicates whether the message has been deleted.
     *
     * @return <code>true</code> if the message is deleted, <code>false</code> otherwise.
     */
    public boolean isDeleted() {
        return this.deleted;
    }


    /**
     * Gets the list icon URL if available.
     *
     * @return The list icon URL if available, otherwise {@code null}.
     */
    @Nullable
    public String getListIconUrl() {
        JsonValue icons = getRawMessageJson().getMap().get("icons");
        if (icons != null && icons.isJsonMap()) {
            return icons.getMap().opt("list_icon").getString();
        }

        return null;
    }

    @Override
    public int compareTo(@NonNull RichPushMessage another) {
        return this.getMessageId().compareTo(another.getMessageId());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof RichPushMessage)) {
            return false;
        }

        RichPushMessage that = (RichPushMessage) o;

        if (this == that) {
            return true;
        }

        return (messageId == null ? that.messageId == null : messageId.equals(that.messageId)) &&
                (messageBodyUrl == null ? that.messageBodyUrl == null : messageBodyUrl.equals(that.messageBodyUrl)) &&
                (messageReadUrl == null ? that.messageReadUrl == null : messageReadUrl.equals(that.messageReadUrl)) &&
                (messageUrl == null ? that.messageUrl == null : messageUrl.equals(that.messageUrl)) &&
                (extras == null ? that.extras == null : extras.equals(that.extras)) &&
                (unreadClient == that.unreadClient) &&
                (unreadOrigin == that.unreadOrigin) &&
                (deleted == that.deleted) &&
                (sentMS == that.sentMS);
    }

    @Override
    public int hashCode() {
        int result = 17;

        result = 37 * result + (messageId == null ? 0 : messageId.hashCode());
        result = 37 * result + (messageBodyUrl == null ? 0 : messageBodyUrl.hashCode());
        result = 37 * result + (messageReadUrl == null ? 0 : messageReadUrl.hashCode());
        result = 37 * result + (messageUrl == null ? 0 : messageUrl.hashCode());
        result = 37 * result + (extras == null ? 0 : extras.hashCode());
        result = 37 * result + (unreadClient ? 0 : 1);
        result = 37 * result + (unreadOrigin ? 0 : 1);
        result = 37 * result + (deleted ? 0 : 1);
        result = 37 * result + Long.valueOf(sentMS).hashCode();

        return result;
    }
}

