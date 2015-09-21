/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.richpush;

import android.database.Cursor;
import android.os.Bundle;

import com.urbanairship.Logger;
import com.urbanairship.RichPushTable;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;

/**
 * The primary data structure for Rich Push messages.
 *
 * @author Urban Airship
 */
public class RichPushMessage implements Comparable<RichPushMessage> {

    boolean deleted = false;
    boolean unreadClient;
    boolean unreadOrigin;

    Bundle extras;
    long sentMS;
    Long expirationMS;

    final String messageId;
    String messageUrl;
    String messageBodyUrl;
    String messageReadUrl;
    String title;
    JSONObject rawMessageJSON;


    RichPushMessage(String messageId) {
        this.messageId = messageId;
    }

    /* TODO Would really like a cleaner way of doing these factory methods.
    Seems kind of difficult without reflection, though and we (maybe?) shouldn't do that.
     */
    static RichPushMessage messageFromCursor(Cursor cursor) throws JSONException {
        RichPushMessage message = new RichPushMessage(cursor.getString(cursor.getColumnIndex(
                RichPushTable.COLUMN_NAME_MESSAGE_ID)));
        message.messageUrl = cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_MESSAGE_URL));
        message.messageBodyUrl = cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_MESSAGE_BODY_URL));
        message.messageReadUrl = cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_MESSAGE_READ_URL));
        message.unreadClient = cursor.getInt(
                cursor.getColumnIndex(RichPushTable.COLUMN_NAME_UNREAD)) == 1;

        message.unreadOrigin = cursor.getInt(
                cursor.getColumnIndex(RichPushTable.COLUMN_NAME_UNREAD_ORIG)) == 1;
        message.extras = jsonToBundle(cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_EXTRA)));
        message.title = cursor.getString(
                cursor.getColumnIndex(RichPushTable.COLUMN_NAME_TITLE));

        String timeStamp = cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_TIMESTAMP));
        message.sentMS = getMillisecondsFromTimeStamp(timeStamp, System.currentTimeMillis());

        message.deleted = cursor.getInt(
                cursor.getColumnIndex(RichPushTable.COLUMN_NAME_DELETED)) == 1;

        String rawMessageJSON = cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_RAW_MESSAGE_OBJECT));
        message.rawMessageJSON = (rawMessageJSON == null) ? new JSONObject() : new JSONObject(rawMessageJSON);

        String expiryTimeStamp = cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_EXPIRATION_TIMESTAMP));
        message.expirationMS = getMillisecondsFromTimeStamp(expiryTimeStamp, null);

        return message;
    }

    // helpers

    static Long getMillisecondsFromTimeStamp(String timeStamp, Long defaultValue) {
        if (UAStringUtil.isEmpty(timeStamp)) {
            return defaultValue;
        }

        try {
            return DateUtils.parseIso8601(timeStamp);
        } catch (ParseException e) {
            Logger.error("RichPushMessage - Couldn't parse message date: " + timeStamp + ", defaulting to: " + defaultValue + ".");
            return defaultValue;
        }
    }

    private static Bundle jsonToBundle(String extrasPayload)  {
        Bundle extras = new Bundle();
        try {
            JsonMap jsonMap = JsonValue.parseString(extrasPayload).getMap();

            if (jsonMap != null) {
                for (Map.Entry<String, JsonValue> entry : jsonMap) {
                    if (entry.getValue().isString()) {
                        extras.putString(entry.getKey(), entry.getValue().getString());
                    } else {
                        extras.putString(entry.getKey(), entry.getValue().toString());
                    }
                }
            }

        } catch (JsonException e) {
            Logger.error("RichPushMessage - Invalid extras: " + extrasPayload);
        }

        return extras;
    }

    // public getters

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
            getInbox().markMessagesRead(set);
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
            getInbox().markMessagesUnread(set);
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
            getInbox().deleteMessages(set);
        }
    }

    /**
     * Gets the entire raw message payload as a JSONObject
     *
     * @return The message's payload as a JSONObject
     */
    public JSONObject getRawMessageJSON() {
        return this.rawMessageJSON;
    }

    /**
     * Indicates whether the message has been deleted.
     *
     * @return <code>true</code> if the message is deleted, <code>false</code> otherwise.
     */
    public boolean isDeleted() {
        return this.deleted;
    }

    // Equality checks

    @Override
    public int compareTo(RichPushMessage another) {
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

        return areObjectsEqual(messageId, that.messageId)
                && areObjectsEqual(messageBodyUrl, that.messageBodyUrl)
                && areObjectsEqual(messageReadUrl, that.messageReadUrl)
                && areObjectsEqual(messageUrl, that.messageUrl)
                && areObjectsEqual(extras, that.extras)
                && unreadClient == that.unreadClient
                && sentMS == that.sentMS;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 37 * result + (this.unreadClient ? 0 : 1);
        result = 37 * result + (this.deleted ? 0 : 1);
        return 37 * result + this.messageId.hashCode();
    }

    /**
     * Helper method to compare 2 possible
     * null objects
     *
     * @param a Object to compare
     * @param b Object to compare
     * @return true if both objects are null or the same, false otherwise
     */
    private boolean areObjectsEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }

        if (a == null || b == null) {
            return false;
        }

        return a.equals(b);
    }

    /**
     * Gets the rich push user's inbox
     *
     * @return Current RichPushInbox
     */
    private RichPushInbox getInbox() {
        return UAirship.shared().getRichPushManager().getRichPushInbox();
    }


}

