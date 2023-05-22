/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.urbanairship.json.JsonMap

@Entity(tableName = "live_update_state")
internal data class LiveUpdateState(
    @PrimaryKey
    val name: String,
    val type: String,
    val isActive: Boolean,
    /** Timestamp of the last START or STOP event for this Live Update. */
    @ColumnInfo(name = "last_start_stop_time")
    val timestamp: Long,
    /** Optional timestamp, to auto-dismiss the Live Update. */
    @ColumnInfo(name = "dismissal_date")
    val dismissalDate: Long? = null
)

@Entity(tableName = "live_update_content")
internal data class LiveUpdateContent(
    @PrimaryKey
    val name: String,
    val content: JsonMap,
    /** Timestamp of the last UPDATE event for this Live Update. */
    @ColumnInfo(name = "last_update_time")
    val timestamp: Long
)

/** Wrapper data class representing a Live Update's state, joined with the latest content. */
internal data class LiveUpdateStateWithContent(
    @Embedded
    val state: LiveUpdateState,
    @Relation(parentColumn = "name", entityColumn = "name")
    val content: LiveUpdateContent?
)
