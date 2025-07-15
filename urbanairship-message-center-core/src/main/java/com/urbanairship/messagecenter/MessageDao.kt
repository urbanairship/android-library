package com.urbanairship.messagecenter

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.util.Consumer
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.urbanairship.UALog
import com.urbanairship.analytics.data.BatchedQueryHelper
import com.urbanairship.db.SuspendingBatchedQueryHelper.runBatched
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Message Data Access Object.
 *
 * Note: In order to avoid potential crashes in customer apps, all generated DAO methods
 * should call through to a separate "internal" method that wraps the internal DAO call with a try/catch.
 *
 * @hide
 */
@Dao
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class MessageDao {

    @VisibleForTesting
    internal var queryClock: Clock = Clock.DEFAULT_CLOCK

    @VisibleForTesting
    internal val currentTimestamp: String
        get() = DateUtils.createIso8601TimeStamp(queryClock.currentTimeMillis())

    @VisibleForTesting
    internal suspend fun insert(message: MessageEntity) = try {
        insertInternal(message)
    } catch (e: Exception) {
        UALog.e(e) { "Failed to insert message!" }
    }

    internal suspend fun insertMessages(messages: List<MessageEntity>) = try {
        insertMessagesInternal(messages)
    } catch (e: Exception) {
        UALog.e(e) { "Failed to insert messages!" }
    }

    internal suspend fun getMessage(id: String): MessageEntity? {
        return try {
            getMessageInternal(id)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get message!" }
            null
        }
    }

    internal suspend fun getMessageByUrl(url: String): MessageEntity? {
        return try {
            getMessageByUrlInternal(url)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get message by url!" }
            null
        }
    }

    internal suspend fun getMessages(): List<MessageEntity> {
        return try {
            getMessagesInternal(currentTimestamp)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get messages!" }
            emptyList()
        }
    }

    internal fun getMessagesFlow(): Flow<List<MessageEntity>> {
        return try {
            getMessagesFlowInternal(currentTimestamp)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get messages flow!" }
            emptyFlow()
        }
    }

    internal suspend fun getMessageCount(): Int {
        return try {
            getMessageCountInternal(currentTimestamp)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get message count!" }
            0
        }
    }

    internal suspend fun getReadMessages(): List<MessageEntity> {
        return try {
            getReadMessagesInternal(currentTimestamp)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get read messages!" }
            emptyList()
        }
    }

    internal suspend fun getReadMessageCount(): Int {
        return try {
            getReadMessageCountInternal(currentTimestamp)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get read message count!" }
            0
        }
    }

    internal suspend fun getUnreadMessages(): List<MessageEntity> {
        return try {
            getUnreadMessagesInternal(currentTimestamp)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get unread messages!" }
            emptyList()
        }
    }

    internal fun getUnreadMessagesFlow(): Flow<List<MessageEntity>> {
        return try {
            getUnreadMessagesFlowInternal(currentTimestamp)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get unread messages flow!" }
            emptyFlow()
        }
    }

    internal suspend fun getUnreadMessageCount(): Int {
        return try {
            getUnreadMessageCountInternal(currentTimestamp)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get unread message count!" }
            0
        }
    }

    internal suspend fun getMessageIds(): List<String> {
        return try {
            getMessageIdsInternal(currentTimestamp)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get message IDs!" }
            emptyList()
        }
    }

    internal suspend fun getLocallyReadMessages(): List<MessageEntity> {
        return try {
            getLocallyReadMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get locally read messages!" }
            emptyList()
        }
    }

    internal suspend fun getLocallyDeletedMessages(): List<MessageEntity> {
        return try {
            getLocallyDeletedMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to get locally deleted messages!" }
            emptyList()
        }
    }

    internal suspend fun markMessagesRead(messageIds: Set<String>) {
        try {
            runBatched(messageIds) { markMessagesReadInternal(messageIds) }
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as read!" }
        }
    }

    internal suspend fun markMessagesUnread(messageIds: Set<String>) {
        try {
            runBatched(messageIds) { markMessagesUnreadInternal(messageIds) }
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as unread!" }
        }
    }

    internal suspend fun markMessagesDeleted(messageIds: Set<String>) {
        try {
            runBatched(messageIds) { markMessagesDeletedInternal(messageIds) }
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as deleted!" }
        }
    }

    internal suspend fun markAllMessagesDeleted() {
        try {
            markAllMessagesDeletedInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as deleted!" }
        }
    }

    internal suspend fun markMessagesReadOrigin(messageIds: Set<String>) {
        try {
            runBatched(messageIds) { markMessagesReadOriginInternal(messageIds) }
        } catch (e: Exception) {
            UALog.e(e) { "Failed to mark messages as read (origin)!" }
        }
    }

    internal fun deleteMessages(messageIds: Set<String>) {
        try {
            deleteMessagesInternal(messageIds)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to delete messages!" }
        }
    }

    internal suspend fun deleteAllMessages() {
        try {
            deleteAllMessagesInternal()
        } catch (e: Exception) {
            UALog.e(e) { "Failed to delete all messages!" }
        }
    }

    internal suspend fun messageExists(messageId: String): Boolean {
        return try {
            messageExistsInternal(messageId)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to check if message exists!" }
            false
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    internal abstract suspend fun insertInternal(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    internal abstract suspend fun insertMessagesInternal(messages: List<MessageEntity>)

    @Transaction
    @Query("SELECT * FROM richpush WHERE message_id = :id")
    internal abstract suspend fun getMessageInternal(id: String): MessageEntity?

    @Transaction
    @Query("SELECT * FROM richpush WHERE message_body_url = :url")
    internal abstract suspend fun getMessageByUrlInternal(url: String): MessageEntity?

    @Transaction
    @Query("SELECT * FROM richpush WHERE $NOT_EXPIRED_OR_DELETED")
    internal abstract suspend fun getMessagesInternal(
        currentTimestamp: String
    ): List<MessageEntity>

    @Transaction
    @Query("SELECT * FROM richpush WHERE $NOT_EXPIRED_OR_DELETED")
    internal abstract fun getMessagesFlowInternal(
        currentTimestamp: String
    ): Flow<List<MessageEntity>>

    @Transaction
    @Query("SELECT COUNT(*) FROM richpush WHERE $NOT_EXPIRED_OR_DELETED")
    internal abstract suspend fun getMessageCountInternal(
        currentTimestamp: String
    ): Int

    @Transaction
    @Query("SELECT * FROM richpush WHERE unread = 0 AND $NOT_EXPIRED_OR_DELETED")
    internal abstract suspend fun getReadMessagesInternal(
        currentTimestamp: String
    ): List<MessageEntity>

    @Transaction
    @Query("SELECT COUNT(*) FROM richpush WHERE unread = 0 AND $NOT_EXPIRED_OR_DELETED")
    internal abstract suspend fun getReadMessageCountInternal(
        currentTimestamp: String
    ): Int

    @Transaction
    @Query("SELECT * FROM richpush WHERE unread = 1 AND $NOT_EXPIRED_OR_DELETED")
    internal abstract suspend fun getUnreadMessagesInternal(
        currentTimestamp: String
    ): List<MessageEntity>

    @Transaction
    @Query("SELECT * FROM richpush WHERE unread = 1 AND $NOT_EXPIRED_OR_DELETED")
    internal abstract fun getUnreadMessagesFlowInternal(
        currentTimestamp: String
    ): Flow<List<MessageEntity>>

    @Transaction
    @Query("SELECT COUNT(*) FROM richpush WHERE unread = 1 AND $NOT_EXPIRED_OR_DELETED")
    internal abstract suspend fun getUnreadMessageCountInternal(
        currentTimestamp: String
    ): Int

    @Transaction
    @Query("SELECT message_id FROM richpush WHERE $NOT_EXPIRED_OR_DELETED")
    internal abstract suspend fun getMessageIdsInternal(
        currentTimestamp: String
    ): List<String>

    @Transaction
    @Query("SELECT * FROM richpush WHERE unread = 0 AND unread <> unread_orig")
    internal abstract suspend fun getLocallyReadMessagesInternal(): List<MessageEntity>

    @Transaction
    @Query("SELECT * FROM richpush WHERE deleted = 1")
    internal abstract suspend fun getLocallyDeletedMessagesInternal(): List<MessageEntity>

    @Transaction
    @Query("UPDATE richpush SET unread = 0 WHERE message_id IN (:messageIds)")
    internal abstract suspend fun markMessagesReadInternal(messageIds: Set<String>)

    @Transaction
    @Query("UPDATE richpush SET unread = 1 WHERE message_id IN (:messageIds)")
    internal abstract suspend fun markMessagesUnreadInternal(messageIds: Set<String>)

    @Transaction
    @Query("UPDATE richpush SET deleted = 1 WHERE message_id IN (:messageIds)")
    internal abstract suspend fun markMessagesDeletedInternal(messageIds: Set<String>)

    @Transaction
    @Query("UPDATE richpush SET deleted = 1")
    internal abstract suspend fun markAllMessagesDeletedInternal()

    @Transaction
    @Query("UPDATE richpush SET unread_orig = 0 WHERE message_id IN (:messageIds)")
    internal abstract suspend fun markMessagesReadOriginInternal(messageIds: Set<String>)

    @Transaction
    internal open fun deleteMessagesInternal(messageIds: Set<String>) {
        val consumer = Consumer { ids: List<String> -> deleteMessagesBatchInternal(ids) }
        BatchedQueryHelper.runBatched(messageIds.toList(), consumer)
    }

    /**
     * This query is only for internal use, with a `BatchedQueryHelper`,
     * which stops us from bumping into the max query params limit of 999.
     */
    @Query("DELETE FROM richpush WHERE message_id IN (:messageIds)")
    internal abstract fun deleteMessagesBatchInternal(messageIds: List<String>)

    @Transaction
    @Query("DELETE FROM richpush")
    internal abstract suspend fun deleteAllMessagesInternal()

    @Query("SELECT EXISTS(SELECT 1 FROM richpush WHERE message_id = :id)")
    internal abstract suspend fun messageExistsInternal(id: String): Boolean

    private companion object {
        private const val NOT_EXPIRED = "(expiration_timestamp IS NULL OR datetime(expiration_timestamp) >= datetime(:currentTimestamp))"
        private const val NOT_DELETED = "deleted = 0"

        private const val NOT_EXPIRED_OR_DELETED = "$NOT_EXPIRED AND $NOT_DELETED"
    }
}
