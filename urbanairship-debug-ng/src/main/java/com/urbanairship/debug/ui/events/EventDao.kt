/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import androidx.annotation.RestrictTo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Data Access Object for the event table.
 * @hide
 */
@Dao
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal interface EventDao {

    @Insert
    fun insertEvent(event: EventEntity)

    @Query("SELECT * FROM events ORDER BY id DESC")
    fun getEvents(): List<EventEntity>

    @Query("SELECT * FROM events WHERE type IN(:types) ORDER BY id DESC")
    fun getEvents(types: List<String>): List<EventEntity>

    @Query("select * from events where eventId = :eventId")
    fun getEvent(eventId: String): EventEntity?

    @Query("DELETE FROM events where eventId NOT IN (SELECT eventId from events ORDER BY time LIMIT :count)")
    fun trimEvents(count: Long)

    @Query("DELETE FROM events WHERE datetime( time / 1000 , 'unixepoch') < datetime('now', '-' || :days || ' day')")
    fun trimOldEvents(days: Int)
}
