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
    // Long, matching SQLite's own 64-bit ROWID: an autoincrement PK issues a
    // fresh value for every insert rather than filling the gaps compaction and
    // retention leave behind, so a device old enough to accumulate more than
    // Int.MAX_VALUE inserts over its lifetime must not have this (or
    // LedgerSharedIdEntity.eventId, which references it) silently wrap.
    //
    // Monotonic within a table's life, not across the v1 -> v2 rebuild: that
    // drops the table and so resets the counter to the largest surviving id.
    // Harmless, because an id is never read outside this table — see
    // LedgerDatabase.MIGRATION_1_2.
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
 * A foreign key with cascading delete ties a row's lifetime to its event: an
 * event row is only ever removed via a delete on `ledger_events`, and this
 * table must never accumulate rows no query can reach. Room enables SQLite
 * foreign-key enforcement automatically once an entity declares one, so this
 * cascade is enforced by the database itself, not by application code — a
 * `NSBatchDeleteRequest`-style bulk delete that bypasses the object graph
 * still cascades correctly.
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
