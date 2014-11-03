/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.urbanairship.RichPushTable;
import com.urbanairship.UrbanAirshipProvider;
import com.urbanairship.UrbanAirshipResolver;
import com.urbanairship.util.UAStringUtil;

import java.util.Collection;
import java.util.Set;

/**
 * Rich Push specific database operations.
 *
 * @author Urban Airship
 */
class RichPushResolver extends UrbanAirshipResolver {

    private static final String NEWEST_FIRST = RichPushTable.COLUMN_NAME_TIMESTAMP + " DESC";
    private static final String WHERE_CLAUSE_CHANGED = RichPushTable.COLUMN_NAME_UNREAD +
            " <> " + RichPushTable.COLUMN_NAME_UNREAD_ORIG;
    private static final String WHERE_CLAUSE_READ = RichPushTable.COLUMN_NAME_UNREAD + " = ?";
    private static final String WHERE_CLAUSE_MESSAGE_ID = RichPushTable.COLUMN_NAME_MESSAGE_ID + " = ?";
    private static final String FALSE_VALUE = "0";
    private static final String TRUE_VALUE = "1";

    public RichPushResolver(Context context) {
        super(context);
    }

    Cursor getMessage(String messageId) {
        return this.query(UrbanAirshipProvider.getRichPushContentUri(), null, WHERE_CLAUSE_MESSAGE_ID,
                new String[] { messageId }, null);
    }

    Cursor getAllMessages() {
        return this.query(UrbanAirshipProvider.getRichPushContentUri(), null, null, null, NEWEST_FIRST);
    }

    Cursor getReadMessages() {
        return this.query(UrbanAirshipProvider.getRichPushContentUri(), null, WHERE_CLAUSE_READ,
                new String[] { FALSE_VALUE }, NEWEST_FIRST);
    }

    Cursor getUnreadMessages() {
        return this.query(UrbanAirshipProvider.getRichPushContentUri(), null, WHERE_CLAUSE_READ,
                new String[] { TRUE_VALUE }, NEWEST_FIRST);
    }

    Cursor getReadUpdatedMessages() {
        return this.query(UrbanAirshipProvider.getRichPushContentUri(), null,
                WHERE_CLAUSE_READ + " AND " + WHERE_CLAUSE_CHANGED, new String[] { FALSE_VALUE }, null);
    }

    Cursor getDeletedMessages() {
        return this.query(UrbanAirshipProvider.getRichPushContentUri(), null,
                RichPushTable.COLUMN_NAME_DELETED + " = ?", new String[] { TRUE_VALUE },
                null);
    }

    int markMessageRead(String messageId) {
        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_UNREAD, false);
        return this.updateMessage(messageId, values);
    }

    int markMessagesRead(Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_UNREAD, false);
        return this.updateMessages(messageIds, values);
    }


    int markMessagesUnread(Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_UNREAD, true);
        return this.updateMessages(messageIds, values);
    }

    int markMessagesDeleted(Set<String> messageIds) {
        ContentValues values = new ContentValues();
        values.put(RichPushTable.COLUMN_NAME_DELETED, true);
        return this.updateMessages(messageIds, values);
    }

    int deleteMessage(String messageId) {
        return this.delete(this.appendMessageIdToUri(messageId), WHERE_CLAUSE_MESSAGE_ID,
                new String[] { messageId });
    }

    int deleteMessages(Set<String> messageIds) {
        int numberOfmessageIds = messageIds.size();
        return this.delete(this.appendMessageIdsToUri(messageIds),
                RichPushTable.COLUMN_NAME_MESSAGE_ID +
                        " IN ( " + UAStringUtil.repeat("?", numberOfmessageIds, ", ") + " )",
                messageIds.toArray(new String[numberOfmessageIds])
                          );
    }


    int insertMessages(ContentValues[] values) {
        return this.bulkInsert(UrbanAirshipProvider.getRichPushContentUri(), values);
    }


    int updateMessage(String messageId, ContentValues values) {
        return this.update(this.appendMessageIdToUri(messageId), values, WHERE_CLAUSE_MESSAGE_ID,
                new String[] { messageId });
    }

    int updateMessages(Set<String> messageIds, ContentValues values) {
        int numberOfmessageIds = messageIds.size();
        return this.update(this.appendMessageIdsToUri(messageIds),
                values, RichPushTable.COLUMN_NAME_MESSAGE_ID
                        + " IN ( " + UAStringUtil.repeat("?", numberOfmessageIds, ", ") + " )",
                messageIds.toArray(new String[numberOfmessageIds])
                          );
    }

    // helpers

    private Uri appendMessageIdsToUri(Collection<String> ids) {
        return Uri.withAppendedPath(UrbanAirshipProvider.getRichPushContentUri(),
                UAStringUtil.join(ids, UrbanAirshipProvider.KEYS_DELIMITER));
    }

    private Uri appendMessageIdToUri(String id) {
        return Uri.withAppendedPath(UrbanAirshipProvider.getRichPushContentUri(), id);
    }

}
