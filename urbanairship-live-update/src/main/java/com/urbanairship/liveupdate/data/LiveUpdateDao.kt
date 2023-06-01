/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.data

import androidx.annotation.RestrictTo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * Live Update DAO.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Dao
internal interface LiveUpdateDao {

    @Transaction
    @Upsert
    suspend fun upsert(state: LiveUpdateState)

    @Transaction
    @Upsert
    suspend fun upsert(content: LiveUpdateContent)

    @Transaction
    suspend fun upsert(state: LiveUpdateState? = null, content: LiveUpdateContent? = null) {
        state?.let { upsert(it) }
        content?.let { upsert(it) }
    }

    @Transaction
    @Query("SELECT * FROM live_update_state WHERE name = :name LIMIT 1")
    suspend fun get(name: String): LiveUpdateStateWithContent?

    @Transaction
    @Query("SELECT * FROM live_update_state WHERE name = :name LIMIT 1")
    suspend fun getState(name: String): LiveUpdateState?

    @Transaction
    @Query("SELECT * FROM live_update_content WHERE name = :name LIMIT 1")
    suspend fun getContent(name: String): LiveUpdateContent?

    @Transaction
    @Query("SELECT * FROM live_update_state WHERE isActive = 1")
    suspend fun getAllActive(): List<LiveUpdateStateWithContent>

    @Transaction
    @Query("DELETE FROM live_update_state WHERE name = :name")
    suspend fun deleteState(name: String)

    @Transaction
    @Query("DELETE FROM live_update_content WHERE name = :name")
    suspend fun deleteContent(name: String)

    @Transaction
    suspend fun delete(name: String) {
        deleteState(name)
        deleteContent(name)
    }

    @Transaction
    @Query("DELETE FROM live_update_state")
    suspend fun deleteAllState()

    @Transaction
    @Query("DELETE FROM live_update_content")
    suspend fun deleteAllContent()

    @Transaction
    suspend fun deleteAll() {
        deleteAllState()
        deleteAllContent()
    }

    @Query("SELECT COUNT(*) > 0 FROM live_update_state WHERE isActive = 1")
    suspend fun isAnyActive(): Boolean

    @Query("SELECT COUNT(*) FROM live_update_state")
    suspend fun countState(): Int

    @Query("SELECT COUNT(*) FROM live_update_content")
    suspend fun countContent(): Int
}
