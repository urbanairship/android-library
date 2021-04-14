/* Copyright Airship and Contributors */

package com.urbanairship.chat.data

import androidx.annotation.RestrictTo
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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(message: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY isPending DESC, createdOn DESC")
    fun getMessageDataSourceFactory(): DataSource.Factory<Int, MessageEntity>

    @Query("SELECT * FROM messages WHERE isPending == 1 ORDER BY createdOn DESC")
    fun getPendingMessages(): List<MessageEntity>

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    fun delete(messageId: String)

    @Query("SELECT EXISTS(SELECT * FROM messages WHERE isPending == 1)")
    fun hasPendingMessages(): Boolean
}
