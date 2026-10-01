/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.urbanairship.json.JsonValue

/**
 * A persisted ledger event.
 *
 * The [scheduleId] and [timestamp] columns are pulled out of the event so
 * they can be indexed and queried directly; [body] holds the full
 * JSON-encoded `LedgerEvent`. The shared group IDs the event was recorded
 * under, if any, live in their own indexed table ([LedgerSharedIdEntity])
 * rather than a column here, since an event can now belong to several groups
 * at once.
 */
@Entity(
    tableName = "ledger_events",
    indices = [Index("scheduleId"), Index("timestamp")]
)
internal data class LedgerEventEntity(
    // Long, matching SQLite's ROWID: an autoincrement PK issues a fresh value
    // per insert rather than reusing the gaps compaction leaves, so a
    // long-lived device must not wrap this (or LedgerSharedIdEntity.eventId,
    // which references it) at Int.MAX_VALUE.
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** ID of the schedule that recorded the event. */
    val scheduleId: String,

    /**
     * When the event was recorded, as epoch milliseconds. Duplicated out of
     * [body] so reads can order and window on it without decoding every row.
     */
    val timestamp: Long,

    /** The JSON-encoded `LedgerEvent`. */
    val body: JsonValue
)

/**
 * One shared group ID an event was recorded under, promoted out of
 * [LedgerEventEntity.body] into its own indexed table so a limit check can
 * find events by group without decoding every row.
 *
 * A foreign key with cascading delete ties a row's lifetime to its event, so
 * this table cannot accumulate rows no query can reach. Room turns on SQLite
 * foreign-key enforcement once an entity declares one, so the cascade holds
 * for any delete on `ledger_events`, including one issued as raw SQL.
 */
@Entity(
    tableName = "ledger_shared_ids",
    indices = [Index("value"), Index("eventId")],
    foreignKeys = [
        ForeignKey(
            entity = LedgerEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class LedgerSharedIdEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The [LedgerEventEntity.id] this shared group ID was recorded under. */
    val eventId: Long,

    /** The shared group ID. */
    val value: String
)
