/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.RichPushTable;
import com.urbanairship.UrbanAirshipProvider;
import com.urbanairship.UrbanAirshipResolver;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rich Push specific database operations.
 */
class RichPushResolver extends UrbanAirshipResolver {

    private static final String WHERE_CLAUSE_CHANGED = RichPushTable.COLUMN_NAME_UNREAD +
            " <> " + RichPushTable.COLUMN_NAME_UNREAD_ORIG;
    private static final String WHERE_CLAUSE_READ = RichPushTable.COLUMN_NAME_UNREAD + " = ?";
    private static final String WHERE_CLAUSE_MESSAGE_ID = RichPushTable.COLUMN_NAME_MESSAGE_ID + " = ?";
    private static final String FALSE_VALUE = "0";
    private static final String TRUE_VALUE = "1";
    private final Uri uri;

    /**
     * Default constructor.
     *
     * @param context The application context.
     */
    RichPushResolver(Context context) {
        super(context);
        this.uri = UrbanAirshipProvider.getRichPushContentUri(context);
    }

    /**
     * Gets all the {@link RichPushMessage} instances from the database.
     *
     * @return A list of {@link RichPushMessage}.
     */
    @NonNull
    List<RichPushMessage> getMessages() {
        List<RichPushMessage> messages = new ArrayList<>();

        Cursor cursor = this.query(this.uri, null, null, null, null);
        if (cursor == null) {
            return messages;
        }

        // Read all the messages from the database
        while (cursor.moveToNext()) {
            try {
                String messageJson = cursor.getString(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_RAW_MESSAGE_OBJECT));
                boolean unreadClient = cursor.getInt(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_UNREAD)) == 1;
                boolean deleted = cursor.getInt(cursor.getColumnIndex(RichPushTable.COLUMN_NAME_DELETED)) == 1;

                RichPushMessage message = RichPushMessage.create(JsonValue.parseString(messageJson), unreadClient, deleted);
                if (message != null) {
                    messages.add(message);
                }
            } catch (JsonException e) {
                Logger.error("RichPushResolver - Failed to parse message from the database.", e);
            }
        }

        cursor.close();

        return messages;
    }

    /**
     * Gets all the {@link RichPushMessage} IDs in the database.
     *
     * @return A set of message IDs.
     */
    @NonNull
    Set<String> getMessageIds() {
        Cursor cursor = this.query(this.uri, null, null, null, null);
        return getMessageIdsFromCursor(cursor);
    }

    /**
     * Gets the IDs of {@link RichPushMessage} in the database where the message is marked read on the
     * client, but not the origin.
     *
     * @return A set of message IDs.
     */
    @NonNull
    Set<String> getReadUpdatedMessageIds() {
        Cursor cursor = this.query(this.uri, null,
                WHERE_CLAUSE_READ + " AND " + WHERE_CLAUSE_CHANGED, new String[] { FALSE_VALUE }, null);
        return getMessageIdsFromCursor(cursor);
    }

    /**
     * Gets the deleted {@link RichPushMessage} IDs in the database.
     *
     * @return A set of message IDs.
     */
    @NonNull
    Set<String> getDeletedMessageIds() {
        Cursor cursor = this.query(this.uri, null,
                RichPushTable.COLUMN_NAME_DELETED + " = ?", new String[] { TRUE_VALUE },
                null);
        return getMessageIdsFromCursor(cursor);
    }

    /**
     * Marks messages read.
     *
     * @param messageIds Set of message IDs to mark as read.
     * @return Count of messages that where updated.
     */
    int markMessagesRead(@NonNull Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_UNREAD, false);
        return this.updateMessages(messageIds, values);
    }

    /**
     * Marks messages unread.
     *
     * @param messageIds Set of message IDs to mark as unread.
     * @return Count of messages that where updated.
     */
    int markMessagesUnread(@NonNull Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_UNREAD, true);
        return this.updateMessages(messageIds, values);
    }

    /**
     * Marks messages deleted.
     *
     * @param messageIds Set of message IDs to mark as deleted.
     * @return Count of messages that where updated.
     */
    int markMessagesDeleted(@NonNull Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_DELETED, true);
        return this.updateMessages(messageIds, values);
    }

    /**
     * Marks messages read on the origin.
     *
     * @param messageIds Set of message IDs to mark as read.
     * @return Count of messages that where updated.
     */
    int markMessagesReadOrigin(@NonNull Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_UNREAD_ORIG, false);
        return this.updateMessages(messageIds, values);
    }

    /**
     * Deletes messages from the database.
     *
     * @param messageIds Set of message IDs to delete.
     * @return Count of messages that were deleted.
     */
    int deleteMessages(@NonNull Set<String> messageIds) {
        String query = RichPushTable.COLUMN_NAME_MESSAGE_ID + " IN ( " + UAStringUtil.repeat("?", messageIds.size(), ", ") + " )";
        return this.delete(this.uri,query, messageIds.toArray(new String[messageIds.size()]));
    }


    /**
     * Inserts new messages into the database.
     *
     * @param messagePayloads A list of the raw message payloads.
     * @return The number of messages that were successfully inserted into the database.
     */
    int insertMessages(@NonNull List<JsonValue> messagePayloads) {
        List<ContentValues> contentValues = new ArrayList<>();
        for (JsonValue messagePayload : messagePayloads) {
            ContentValues values = parseMessageContentValues(messagePayload);

            if (values != null) {
                // Set the client unread status the same as the origin for new messages
                values.put(RichPushTable.COLUMN_NAME_UNREAD, values.getAsBoolean(RichPushTable.COLUMN_NAME_UNREAD_ORIG));
                contentValues.add(values);
            }
        }

        if (contentValues.isEmpty()) {
            return -1;
        }

        return this.bulkInsert(this.uri,
                contentValues.toArray(new ContentValues[contentValues.size()]));
    }

    /**
     * Updates a message in the database.
     * @param messageId The message ID to update.
     * @param messagePayload The raw message payload.
     * @return The row number that new message, or -1 if the message failed ot be updated.
     */
    int updateMessage(@NonNull String messageId, @NonNull JsonValue messagePayload) {
        ContentValues values = parseMessageContentValues(messagePayload);
        if (values == null) {
            return -1;
        }

        Uri uri = Uri.withAppendedPath(this.uri, messageId);

        return this.update(uri, values, WHERE_CLAUSE_MESSAGE_ID, new String[] { messageId });
    }

    /**
     * Updates message IDs with the content values.
     * @param messageIds The message IDs to update.
     * @param values The content values of the update.
     * @return Count of messages that where updated.
     */
    private int updateMessages(@NonNull Set<String> messageIds, @NonNull ContentValues values) {
        return this.update(this.uri,
                values,
                RichPushTable.COLUMN_NAME_MESSAGE_ID + " IN ( " + UAStringUtil.repeat("?", messageIds.size(), ", ") + " )",
                messageIds.toArray(new String[messageIds.size()]));
    }


    /**
     * Get the message IDs.
     *
     * @param cursor The cursor to get the message IDs from.
     * @return The message IDs as a set of strings.
     */
    @NonNull
    private Set<String> getMessageIdsFromCursor(@Nullable Cursor cursor) {
        if (cursor == null) {
            return new HashSet<>();
        }

        Set<String> ids = new HashSet<>(cursor.getCount());

        int messageIdIndex = -1;
        while (cursor.moveToNext()) {
            if (messageIdIndex == -1) {
                messageIdIndex = cursor.getColumnIndex(RichPushTable.COLUMN_NAME_MESSAGE_ID);
            }
            ids.add(cursor.getString(messageIdIndex));
        }

        cursor.close();

        return ids;
    }

    /**
     * Parses a raw message payload into content values.
     * @param messagePayload The raw message payload.
     * @return ContentValues that can be inserted into the database, or null if the message payload
     * was invalid.
     */
    @Nullable
    private ContentValues parseMessageContentValues(@Nullable JsonValue messagePayload) {
        if (messagePayload == null || !messagePayload.isJsonMap()) {
            Logger.error("RichPushResolver - Unexpected message: " + messagePayload);
            return null;
        }

        JsonMap messageMap = messagePayload.getMap();

        if (UAStringUtil.isEmpty(messageMap.opt(RichPushMessage.MESSAGE_ID_KEY).getString())) {
            Logger.error("RichPushResolver - Message is missing an ID: " + messagePayload);
            return null;
        }

        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_TIMESTAMP, messageMap.opt(RichPushMessage.MESSAGE_SENT_KEY).getString());
        values.put(RichPushTable.COLUMN_NAME_MESSAGE_ID, messageMap.opt(RichPushMessage.MESSAGE_ID_KEY).getString());
        values.put(RichPushTable.COLUMN_NAME_MESSAGE_URL, messageMap.opt(RichPushMessage.MESSAGE_URL_KEY).getString());
        values.put(RichPushTable.COLUMN_NAME_MESSAGE_BODY_URL, messageMap.opt(RichPushMessage.MESSAGE_BODY_URL_KEY).getString());
        values.put(RichPushTable.COLUMN_NAME_MESSAGE_READ_URL, messageMap.opt(RichPushMessage.MESSAGE_READ_URL_KEY).getString());
        values.put(RichPushTable.COLUMN_NAME_TITLE, messageMap.opt(RichPushMessage.TITLE_KEY).getString());
        values.put(RichPushTable.COLUMN_NAME_UNREAD_ORIG, messageMap.opt(RichPushMessage.UNREAD_KEY).getBoolean(true));

        values.put(RichPushTable.COLUMN_NAME_EXTRA, messageMap.opt(RichPushMessage.EXTRA_KEY).toString());
        values.put(RichPushTable.COLUMN_NAME_RAW_MESSAGE_OBJECT, messageMap.toString());

        if (messageMap.containsKey(RichPushMessage.MESSAGE_EXPIRY_KEY)) {
            values.put(RichPushTable.COLUMN_NAME_EXPIRATION_TIMESTAMP, messageMap.opt(RichPushMessage.MESSAGE_EXPIRY_KEY).getString());
        }

        return values;
    }
}
