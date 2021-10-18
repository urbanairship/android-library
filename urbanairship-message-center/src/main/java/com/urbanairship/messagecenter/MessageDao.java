package com.urbanairship.messagecenter;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
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
    public abstract void insert(@NonNull MessageEntity message);

    @Transaction
    @Query("SELECT * FROM richpush")
    @NonNull
    public abstract List<MessageEntity> getMessages();

    @Transaction
    @Query("SELECT message_id FROM richpush")
    @NonNull
    public abstract List<String> getMessageIds();

    @Transaction
    @Query("SELECT * FROM richpush WHERE unread = 0 AND unread <> unread_orig")
    @NonNull
    public abstract List<MessageEntity> getLocallyReadMessages();

    @Transaction
    @Query("SELECT * FROM richpush WHERE deleted = 1")
    @NonNull
    public abstract List<MessageEntity> getLocallyDeletedMessages();

    @Transaction
    @Query("UPDATE richpush SET unread = 0 WHERE message_id IN (:messageIds)")
    @NonNull
    public abstract void markMessagesRead(@NonNull List<String> messageIds);

    @Transaction
    @Query("UPDATE richpush SET unread = 1 WHERE message_id IN (:messageIds)")
    @NonNull
    public abstract void markMessagesUnread(@NonNull List<String> messageIds);

    @Transaction
    @Query("UPDATE richpush SET deleted = 1 WHERE message_id IN (:messageIds)")
    @NonNull
    public abstract void markMessagesDeleted(@NonNull List<String> messageIds);

    @Transaction
    @Query("UPDATE richpush SET unread_orig = 0 WHERE message_id IN (:messageIds)")
    @NonNull
    public abstract void markMessagesReadOrigin(@NonNull List<String> messageIds);

    @Transaction
    @Query("DELETE FROM richpush WHERE message_id IN (:messageIds)")
    @NonNull
    public abstract void deleteMessages(@NonNull List<String> messageIds);

    @Transaction
    @Query("DELETE FROM richpush")
    @NonNull
    public abstract void deleteAllMessages();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @NonNull
    public abstract void insertMessages(@NonNull List<MessageEntity> messages);

    @Update
    @NonNull
    public abstract int updateMessage(@NonNull MessageEntity message);


}
