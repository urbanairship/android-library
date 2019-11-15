/* Copyright Airship and Contributors */

package com.urbanairship.debug.push.persistence

import androidx.annotation.RestrictTo
import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Data Access Object for the push table.
 * @hide
 */
@Dao
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface PushDao {
    @Insert
    fun insertPush(push: PushEntity)

    @Query("SELECT * FROM pushes ORDER BY id DESC")
    fun getPushes(): DataSource.Factory<Int, PushEntity>

    @Query("SELECT * FROM pushes WHERE pushId = :pushId")
    fun getPush(pushId: String): LiveData<PushEntity?>

    @Query("DELETE FROM pushes where id NOT IN (SELECT id from pushes ORDER BY time LIMIT :count)")
    fun trimPushes(count: Long)
}
