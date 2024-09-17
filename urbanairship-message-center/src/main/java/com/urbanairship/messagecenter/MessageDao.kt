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
 * should be call through to a separate "internal" method that wraps the internal DAO call with a try/catch.
 */
@Dao
internal interface MessageDao {

    suspend fun insert(message: MessageEntity) = try {
        insertInternal(message)
    } catch (e: Exception) {
        UALog.e(e) { "Failed to insert message!" }
    }

    suspend fun insertMessages(messages: List<MessageEntity>) = try {
        insertMessagesInternal(messages)
    } catch (e: Exception) {
        UALog.e(e) { "Failed to insert messages!" }
    }

    suspend fun getMessage(id: String): MessageEntity? {
        return try {
            getMessageInternal(id)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get message!" }
            null
        }
    }

    suspend fun getMessageByUrl(url: String): MessageEntity? {
        return try {
            getMessageByUrlInternal(url)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get message by url!" }
            null
        }
    }

    suspend fun getMessages(): List<MessageEntity> {
        return try {
            getMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get messages!" }
            emptyList()
        }
    }

    suspend fun getMessageCount(): Int {
        return try {
            getMessageCountInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get message count!" }
            0
        }
    }

    suspend fun getReadMessages(): List<MessageEntity> {
        return try {
            getReadMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get read messages!" }
            emptyList()
        }
    }

    suspend fun getReadMessageCount(): Int {
        return try {
            getReadMessageCountInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get read message count!" }
            0
        }
    }

    suspend fun getUnreadMessages(): List<MessageEntity> {
        return try {
            getUnreadMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get unread messages!" }
            emptyList()
        }
    }

    suspend fun getUnreadMessageCount(): Int {
        return try {
            getUnreadMessageCountInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get unread message count!" }
            0
        }
    }

    suspend fun getMessageIds(): List<String> {
        return try {
            getMessageIdsInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get message IDs!" }
            emptyList()
        }
    }

    suspend fun getLocallyReadMessages(): List<MessageEntity> {
        return try {
            getLocallyReadMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get locally read messages!" }
            emptyList()
        }
    }

    suspend fun getLocallyDeletedMessages(): List<MessageEntity> {
        return try {
            getLocallyDeletedMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get locally deleted messages!" }
            emptyList()
        }
    }

    suspend fun markMessagesRead(messageIds: List<String>) {
        try {
            markMessagesReadInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as read!" }
        }
    }

    suspend fun markMessagesUnread(messageIds: List<String>) {
        try {
            markMessagesUnreadInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as unread!" }
        }
    }

    suspend fun markMessagesDeleted(messageIds: List<String>) {
        try {
            markMessagesDeletedInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as deleted!" }
        }
    }

    suspend fun markMessagesReadOrigin(messageIds: List<String>) {
        try {
            markMessagesReadOriginInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as read (origin)!" }
        }
    }

    suspend fun deleteMessages(messageIds: List<String>) {
        try {
            deleteMessagesInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to delete messages!" }
        }
    }

    suspend fun deleteAllMessages() {
        try {
            deleteAllMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to delete all messages!" }
        }
    }

    suspend fun messageExists(messageId: String): Boolean {
        return try {
            messageExistsInternal(messageId)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to check if message exists!" }
            false
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInternal(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessagesInternal(messages: List<MessageEntity>)

    @Transaction
    @Query("SELECT * FROM richpush WHERE message_id = :id")
    suspend fun getMessageInternal(id: String): MessageEntity?

    @Transaction
    @Query("SELECT * FROM richpush WHERE message_body_url = :url")
    suspend fun getMessageByUrlInternal(url: String): MessageEntity?

    @Transaction
    @Query("""
        SELECT * FROM richpush
        WHERE deleted = 0
        AND (expiration_timestamp IS NULL OR expiration_timestamp <= strftime('%s', 'now'))
        """)
    suspend fun getMessagesInternal(): List<MessageEntity>

    @Transaction
    @Query("""
        SELECT COUNT(*) FROM richpush
        WHERE deleted = 0
        AND (expiration_timestamp IS NULL OR expiration_timestamp <= strftime('%s', 'now'))
        """)
    suspend fun getMessageCountInternal(): Int

    @Transaction
    @Query("""
        SELECT * FROM richpush
        WHERE unread = 0
        AND (expiration_timestamp IS NULL OR expiration_timestamp <= strftime('%s', 'now'))
        """)
    suspend fun getReadMessagesInternal(): List<MessageEntity>

    @Transaction
    @Query("""
        SELECT COUNT(*) FROM richpush
        WHERE unread = 0
        AND (expiration_timestamp IS NULL OR expiration_timestamp <= strftime('%s', 'now'))
        """)
    suspend fun getReadMessageCountInternal(): Int

    @Transaction
    @Query("""
        SELECT * FROM richpush
        WHERE unread = 1
        AND deleted = 0
        AND (expiration_timestamp IS NULL OR expiration_timestamp <= strftime('%s', 'now'))
        """)
    suspend fun getUnreadMessagesInternal(): List<MessageEntity>

    @Transaction
    @Query("""
        SELECT COUNT(*) FROM richpush
        WHERE unread = 1
        AND deleted = 0
        AND (expiration_timestamp IS NULL OR expiration_timestamp <= strftime('%s', 'now'))
        """)
    suspend fun getUnreadMessageCountInternal(): Int

    @Transaction
    @Query("""
        SELECT message_id FROM richpush
        WHERE deleted = 0
        AND (expiration_timestamp IS NULL OR expiration_timestamp <= strftime('%s', 'now'))
        """)
    suspend fun getMessageIdsInternal(): List<String>

    @Transaction
    @Query("""
        SELECT * FROM richpush
        WHERE unread = 0
        AND unread <> unread_orig
        """)
    suspend fun getLocallyReadMessagesInternal(): List<MessageEntity>

    @Transaction
    @Query("SELECT * FROM richpush WHERE deleted = 1")
    suspend fun getLocallyDeletedMessagesInternal(): List<MessageEntity>

    @Transaction
    @Query("UPDATE richpush SET unread = 0 WHERE message_id IN (:messageIds)")
    suspend fun markMessagesReadInternal(messageIds: List<String>)

    @Transaction
    @Query("UPDATE richpush SET unread = 1 WHERE message_id IN (:messageIds)")
    suspend fun markMessagesUnreadInternal(messageIds: List<String>)

    @Transaction
    @Query("UPDATE richpush SET deleted = 1 WHERE message_id IN (:messageIds)")
    suspend fun markMessagesDeletedInternal(messageIds: List<String>)

    @Transaction
    @Query("UPDATE richpush SET unread_orig = 0 WHERE message_id IN (:messageIds)")
    suspend fun markMessagesReadOriginInternal(messageIds: List<String>)

    @Transaction
    fun deleteMessagesInternal(messageIds: List<String>) {
        val consumer = Consumer { ids: List<String> -> deleteMessagesBatchInternal(ids) }
        BatchedQueryHelper.runBatched(messageIds, consumer)
    }

    /**
     * This query is only for internal use, with a `BatchedQueryHelper`,
     * which stops us from bumping into the max query params limit of 999.
     */
    @Query("DELETE FROM richpush WHERE message_id IN (:messageIds)")
    fun deleteMessagesBatchInternal(messageIds: List<String>)

    @Transaction
    @Query("DELETE FROM richpush")
    suspend fun deleteAllMessagesInternal()

    @Query("SELECT EXISTS (SELECT 1 FROM richpush WHERE message_id = :id)")
    suspend fun messageExistsInternal(id: String): Boolean
}
