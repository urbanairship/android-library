package com.urbanairship.messagecenter;

import com.urbanairship.Logger;
import com.urbanairship.analytics.data.BatchedQueryHelper;

import java.util.Collections;
import java.util.List;

import androidx.annotation.RestrictTo;
import androidx.core.util.Consumer;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

/**
 * Message Data Access Object.
 *
 * Note: In order to avoid potential crashes in customer apps, all generated DAO methods
 * should be {@code protected} and exposed via a separate {@code public} method that wraps
 * the internal DAO call with a try/catch.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
public abstract class MessageDao {
    public void insert(MessageEntity message) {
        try {
            insertInternal(message);
        } catch (Exception e) {
            Logger.error(e, "Failed to insert message!");
        }
    }

    public void insertMessages(List<MessageEntity> messages) {
        try {
            insertMessagesInternal(messages);
        } catch (Exception e) {
            Logger.error(e, "Failed to insert messages!");
        }
    }

    public List<MessageEntity> getMessages() {
        try {
            return getMessagesInternal();
        } catch (Exception e) {
            Logger.error(e, "Failed to get messages!");
            return Collections.emptyList();
        }
    }

    public List<String> getMessageIds() {
        try {
            return getMessageIdsInternal();
        } catch (Exception e) {
            Logger.error(e, "Failed to get message IDs!");
            return Collections.emptyList();
        }
    }

    public List<MessageEntity> getLocallyReadMessages() {
        try {
            return getLocallyReadMessagesInternal();
        } catch (Exception e) {
            Logger.error(e, "Failed to get locally read messages!");
            return Collections.emptyList();
        }
    }

    public List<MessageEntity> getLocallyDeletedMessages() {
        try {
            return getLocallyDeletedMessagesInternal();
        } catch (Exception e) {
            Logger.error(e, "Failed to get locally deleted messages!");
            return Collections.emptyList();
        }
    }

    public void markMessagesRead(List<String> messageIds) {
        try {
            markMessagesReadInternal(messageIds);
        } catch (Exception e) {
            Logger.error(e, "Failed to mark messages as read!");
        }
    }

    public void markMessagesUnread(List<String> messageIds) {
        try {
            markMessagesUnreadInternal(messageIds);
        } catch (Exception e) {
            Logger.error(e, "Failed to mark messages as unread!");
        }
    }

    public void markMessagesDeleted(List<String> messageIds) {
        try {
            markMessagesDeletedInternal(messageIds);
        } catch (Exception e) {
            Logger.error(e, "Failed to mark messages as deleted!");
        }
    }

    public void markMessagesReadOrigin(List<String> messageIds) {
        try {
            markMessagesReadOriginInternal(messageIds);
        } catch (Exception e) {
            Logger.error(e, "Failed to mark messages as read (origin)!");
        }
    }

    public void deleteMessages(List<String> messageIds) {
        try {
            deleteMessagesInternal(messageIds);
        } catch (Exception e) {
            Logger.error(e, "Failed to delete messages!");
        }
    }

    public void deleteAllMessages() {
        try {
            deleteAllMessagesInternal();
        } catch (Exception e) {
            Logger.error(e, "Failed to delete all messages!");
        }
    }


    public boolean messageExists(String messageId) {
        try {
            return messageExistsInternal(messageId);
        } catch (Exception e) {
            Logger.error(e, "Failed to check if message exists!");
            return false;
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertInternal(MessageEntity message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertMessagesInternal(List<MessageEntity> messages);

    @Transaction
    @Query("SELECT * FROM richpush")
    protected abstract List<MessageEntity> getMessagesInternal();

    @Transaction
    @Query("SELECT message_id FROM richpush")
    protected abstract List<String> getMessageIdsInternal();

    @Transaction
    @Query("SELECT * FROM richpush WHERE unread = 0 AND unread <> unread_orig")
    protected abstract List<MessageEntity> getLocallyReadMessagesInternal();

    @Transaction
    @Query("SELECT * FROM richpush WHERE deleted = 1")
    protected abstract List<MessageEntity> getLocallyDeletedMessagesInternal();

    @Transaction
    @Query("UPDATE richpush SET unread = 0 WHERE message_id IN (:messageIds)")
    protected abstract void markMessagesReadInternal(List<String> messageIds);

    @Transaction
    @Query("UPDATE richpush SET unread = 1 WHERE message_id IN (:messageIds)")
    protected abstract void markMessagesUnreadInternal(List<String> messageIds);

    @Transaction
    @Query("UPDATE richpush SET deleted = 1 WHERE message_id IN (:messageIds)")
    protected abstract void markMessagesDeletedInternal(List<String> messageIds);

    @Transaction
    @Query("UPDATE richpush SET unread_orig = 0 WHERE message_id IN (:messageIds)")
    protected abstract void markMessagesReadOriginInternal(List<String> messageIds);

    @Transaction
    protected void deleteMessagesInternal(List<String> messageIds) {
        //noinspection Convert2MethodRef
        Consumer<List<String>> consumer = ids -> deleteMessagesBatchInternal(ids);
        BatchedQueryHelper.runBatched(messageIds, consumer);
    }

    /**
     * This query is only for internal use, with a {@code BatchedQueryHelper},
     * which stops us from bumping into the max query params limit of 999.
     */
    @Query("DELETE FROM richpush WHERE message_id IN (:messageIds)")
    protected abstract void deleteMessagesBatchInternal(List<String> messageIds);

    @Transaction
    @Query("DELETE FROM richpush")
    protected abstract void deleteAllMessagesInternal();

    @Query("SELECT EXISTS (SELECT 1 FROM richpush WHERE message_id = :id)")
    protected abstract boolean messageExistsInternal(String id);
}
