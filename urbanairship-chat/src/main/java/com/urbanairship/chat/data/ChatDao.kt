/* Copyright Airship and Contributors */

package com.urbanairship.chat.data

import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * @hide
 */
@Dao
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal interface ChatDao {
    @WorkerThread
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(message: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY isPending ASC, createdOn ASC")
    fun getMessageDataSourceFactory(): DataSource.Factory<Int, MessageEntity>

    @WorkerThread
    @Query("SELECT * FROM messages ORDER BY isPending DESC, createdOn DESC LIMIT :limit")
    fun getMessages(limit: Int = 50): List<MessageEntity>

    @WorkerThread
    @Query("SELECT * FROM messages WHERE isPending == 1 ORDER BY createdOn DESC")
    fun getPendingMessages(): List<MessageEntity>

    @WorkerThread
    @Query("DELETE FROM messages WHERE messageId = :messageId")
    fun delete(messageId: String)

    @WorkerThread
    @Query("SELECT EXISTS(SELECT * FROM messages WHERE isPending == 1)")
    fun hasPendingMessages(): Boolean

    @WorkerThread
    @Query("DELETE FROM messages")
    fun deleteMessages()
}
