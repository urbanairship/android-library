package com.urbanairship.messagecenter;

import com.urbanairship.analytics.data.BatchedQueryHelper;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.util.Consumer;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

/**
 * Message Data Access Object.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
public abstract class MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insert(MessageEntity message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertMessages(List<MessageEntity> messages);

    @Transaction
    @Query("SELECT * FROM richpush")
    public abstract List<MessageEntity> getMessages();

    @Transaction
    @Query("SELECT message_id FROM richpush")
    public abstract List<String> getMessageIds();

    @Transaction
    @Query("SELECT * FROM richpush WHERE unread = 0 AND unread <> unread_orig")
    public abstract List<MessageEntity> getLocallyReadMessages();

    @Transaction
    @Query("SELECT * FROM richpush WHERE deleted = 1")
    public abstract List<MessageEntity> getLocallyDeletedMessages();

    @Transaction
    @Query("UPDATE richpush SET unread = 0 WHERE message_id IN (:messageIds)")
    public abstract void markMessagesRead(List<String> messageIds);

    @Transaction
    @Query("UPDATE richpush SET unread = 1 WHERE message_id IN (:messageIds)")
    public abstract void markMessagesUnread(List<String> messageIds);

    @Transaction
    @Query("UPDATE richpush SET deleted = 1 WHERE message_id IN (:messageIds)")
    public abstract void markMessagesDeleted(List<String> messageIds);

    @Transaction
    @Query("UPDATE richpush SET unread_orig = 0 WHERE message_id IN (:messageIds)")
    public abstract void markMessagesReadOrigin(List<String> messageIds);

    @Transaction
    public void deleteMessages(List<String> messageIds) {
        //noinspection Convert2MethodRef
        Consumer<List<String>> consumer = ids -> deleteMessagesBatch(ids);
        BatchedQueryHelper.runBatched(messageIds, consumer);
    }

    @Query("DELETE FROM richpush WHERE message_id IN (:messageIds)")
    protected abstract void deleteMessagesBatch(List<String> messageIds);

    @Transaction
    @Query("DELETE FROM richpush")
    public abstract void deleteAllMessages();

    @Query("SELECT EXISTS (SELECT 1 FROM richpush WHERE message_id = :id)")
    public abstract boolean messageExists(String id);
}
