package com.urbanairship.messagecenter

import androidx.core.util.Consumer
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.urbanairship.UALog
import com.urbanairship.analytics.data.BatchedQueryHelper

/**
 * Message Data Access Object.
 *
 * Note: In order to avoid potential crashes in customer apps, all generated DAO methods
 * should be `protected` and exposed via a separate `public` method that wraps
 * the internal DAO call with a try/catch.
 */
@Dao
internal abstract class MessageDao {

    fun insert(message: MessageEntity) = try {
        insertInternal(message)
    } catch (e: Exception) {
        UALog.e(e) { "Failed to insert message!" }
    }

    fun insertMessages(messages: List<MessageEntity>) = try {
        insertMessagesInternal(messages)
    } catch (e: Exception) {
        UALog.e(e) { "Failed to insert messages!" }
    }

    val messages: List<MessageEntity>
        get() = try {
            getMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get messages!" }
            emptyList()
        }

    val messageIds: List<String>
        get() = try {
            getMessageIdsInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get message IDs!" }
            emptyList()
        }

    val locallyReadMessages: List<MessageEntity>
        get() = try {
            getLocallyReadMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e, "Failed to get locally read messages!")
            emptyList()
        }

    val locallyDeletedMessages: List<MessageEntity>
        get() = try {
            getLocallyDeletedMessagesInternal()
        } catch (e: Exception) {
            UALog. e(e) { "Failed to get locally deleted messages!" }
            emptyList()
        }

    fun markMessagesRead(messageIds: List<String>) {
        try {
            markMessagesReadInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as read!" }
        }
    }

    fun markMessagesUnread(messageIds: List<String>) {
        try {
            markMessagesUnreadInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as unread!" }
        }
    }

    fun markMessagesDeleted(messageIds: List<String>) {
        try {
            markMessagesDeletedInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as deleted!" }
        }
    }

    fun markMessagesReadOrigin(messageIds: List<String>) {
        try {
            markMessagesReadOriginInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as read (origin)!" }
        }
    }

    fun deleteMessages(messageIds: List<String>) {
        try {
            deleteMessagesInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to delete messages!" }
        }
    }

    fun deleteAllMessages() {
        try {
            deleteAllMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to delete all messages!" }
        }
    }

    fun messageExists(messageId: String): Boolean {
        return try {
            messageExistsInternal(messageId)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to check if message exists!" }
            false
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertInternal(message: MessageEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertMessagesInternal(messages: List<MessageEntity>)

    @Transaction
    @Query("SELECT * FROM richpush")
    protected abstract fun getMessagesInternal(): List<MessageEntity>

    @Transaction
    @Query("SELECT message_id FROM richpush")
    protected abstract fun getMessageIdsInternal(): List<String>

    @Transaction
    @Query("SELECT * FROM richpush WHERE unread = 0 AND unread <> unread_orig")
    protected abstract fun getLocallyReadMessagesInternal(): List<MessageEntity>

    @Transaction
    @Query("SELECT * FROM richpush WHERE deleted = 1")
    protected abstract fun getLocallyDeletedMessagesInternal(): List<MessageEntity>

    @Transaction
    @Query("UPDATE richpush SET unread = 0 WHERE message_id IN (:messageIds)")
    protected abstract fun markMessagesReadInternal(messageIds: List<String>)

    @Transaction
    @Query("UPDATE richpush SET unread = 1 WHERE message_id IN (:messageIds)")
    protected abstract fun markMessagesUnreadInternal(messageIds: List<String>)

    @Transaction
    @Query("UPDATE richpush SET deleted = 1 WHERE message_id IN (:messageIds)")
    protected abstract fun markMessagesDeletedInternal(messageIds: List<String>)

    @Transaction
    @Query("UPDATE richpush SET unread_orig = 0 WHERE message_id IN (:messageIds)")
    protected abstract fun markMessagesReadOriginInternal(messageIds: List<String>)

    @Transaction
    protected open fun deleteMessagesInternal(messageIds: List<String>) {
        val consumer = Consumer { ids: List<String> -> deleteMessagesBatchInternal(ids) }
        BatchedQueryHelper.runBatched(messageIds, consumer)
    }

    /**
     * This query is only for internal use, with a `BatchedQueryHelper`,
     * which stops us from bumping into the max query params limit of 999.
     */
    @Query("DELETE FROM richpush WHERE message_id IN (:messageIds)")
    protected abstract fun deleteMessagesBatchInternal(messageIds: List<String>)

    @Transaction
    @Query("DELETE FROM richpush")
    protected abstract fun deleteAllMessagesInternal()

    @Query("SELECT EXISTS (SELECT 1 FROM richpush WHERE message_id = :id)")
    protected abstract fun messageExistsInternal(id: String): Boolean
}
