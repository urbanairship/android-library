/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.urbanairship.json.JsonValue

/**
 * A persisted ledger event.
 *
 * The scope columns ([scheduleId] and optional [sharedId]) are pulled out of
 * the event so they can be indexed and queried directly; [body] holds the full
 * JSON-encoded `LedgerEvent`.
 */
@Entity(
    tableName = "ledger_events",
    indices = [Index("scheduleId"), Index("sharedId")]
)
internal class LedgerEventEntity {

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    /** ID of the schedule that recorded the event. */
    var scheduleId: String = ""

    /** Shared group ID the recording schedule had at record time, if any. */
    var sharedId: String? = null

    /** The JSON-encoded `LedgerEvent`. */
    var body: JsonValue = JsonValue.NULL
}
