/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.urbanairship.json.JsonValue

/**
 * A persisted ledger event.
 *
 * The scope columns ([scheduleId] and optional [sharedId]) and [timestamp] are
 * pulled out of the event so they can be indexed and queried directly; [body]
 * holds the full JSON-encoded `LedgerEvent`.
 */
@Entity(
    tableName = "ledger_events",
    indices = [Index("scheduleId"), Index("sharedId"), Index("timestamp")]
)
internal data class LedgerEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** ID of the schedule that recorded the event. */
    val scheduleId: String,

    /** Shared group ID the recording schedule had at record time, if any. */
    val sharedId: String? = null,

    /**
     * When the event was recorded, as epoch milliseconds. Duplicated out of
     * [body] so reads can order and window on it without decoding every row.
     */
    val timestamp: Long,

    /** The JSON-encoded `LedgerEvent`. */
    val body: JsonValue
)
