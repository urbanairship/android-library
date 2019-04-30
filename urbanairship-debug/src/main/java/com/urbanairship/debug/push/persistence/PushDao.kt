/* Copyright Airship and Contributors */

package com.urbanairship.debug.push.persistence

import android.arch.lifecycle.LiveData
import android.arch.paging.DataSource
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query
import android.support.annotation.RestrictTo

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
