/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.automation.limits.storage.LedgerDao
import com.urbanairship.automation.limits.storage.LedgerDatabase
import com.urbanairship.automation.limits.storage.LedgerEventEntity
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonException
import com.urbanairship.util.SerialQueue

/**
 * Persists and queries [LedgerEvent]s.
 *
 * Scope of this store is persistence only: writing real events and reading
 * during limit checks are handled by higher layers. It supports appending
 * events, querying the events eligible for a schedule's limit evaluation
 * (events recorded under the schedule's own ID or its current shared group
 * ID), and deleting events by scope.
 */
internal interface LedgerStoreInterface {

    /**
     * Appends events to the ledger.
     *
     * The batch is written atomically: if the append fails, no events are
     * persisted. Callers that retry a failed append depend on this, since a
     * partial write would be re-appended and double-count.
     */
    suspend fun recordEvents(events: List<LedgerEvent>)

    /**
     * Fetches the events eligible for a schedule's limit evaluation: every
     * event recorded under the schedule's own ID, plus every event recorded
     * under the schedule's current shared group ID (if any).
     *
     * @param scheduleId The evaluating schedule's ID.
     * @param sharedId The schedule's current shared group ID, if any.
     * @return The eligible events, oldest first.
     */
    suspend fun events(scheduleId: String, sharedId: String?): List<LedgerEvent>

    /**
     * True if any event is recorded under [scheduleId], including events whose
     * body cannot be decoded. Lets a caller tell "nothing recorded yet" apart
     * from "already recorded" without reading the events back.
     */
    suspend fun hasEvents(scheduleId: String): Boolean

    /** Deletes every event recorded under any of the given [scopes]. */
    suspend fun deleteEvents(scopes: List<LedgerScope>)
}

internal class LedgerStore(
    private val dao: LedgerDao
) : LedgerStoreInterface {

    private val queue = SerialQueue()

    internal constructor(context: Context, config: AirshipRuntimeConfig) : this(
        LedgerDatabase.createDatabase(context, config).dao
    )

    override suspend fun recordEvents(events: List<LedgerEvent>) {
        if (events.isEmpty()) {
            return
        }

        UALog.v { "Recording ledger events: $events" }

        queue.run {
            dao.insertAll(events.map { it.toEntity() })
        }
    }

    override suspend fun events(scheduleId: String, sharedId: String?): List<LedgerEvent> {
        UALog.v { "Fetching ledger events for schedule $scheduleId sharedId $sharedId" }

        return queue.run {
            // The query already orders by record time. Skip any undecodable rows
            // (e.g. a forward-incompatible event written by a newer SDK) rather
            // than failing the whole query.
            dao.getEvents(scheduleId, sharedId)
                .mapNotNull { entity ->
                    try {
                        LedgerEvent.fromJson(entity.body)
                    } catch (e: JsonException) {
                        UALog.e(e) { "Failed to decode ledger event, skipping: ${entity.body}" }
                        null
                    }
                }
        }
    }

    override suspend fun hasEvents(scheduleId: String): Boolean =
        queue.run { dao.hasEvents(scheduleId) }

    override suspend fun deleteEvents(scopes: List<LedgerScope>) {
        if (scopes.isEmpty()) {
            return
        }

        UALog.v { "Deleting ledger events for scopes: $scopes" }

        queue.run {
            val scheduleIds = scopes.filterIsInstance<LedgerScope.Schedule>().map { it.scheduleId }
            val sharedIds = scopes.filterIsInstance<LedgerScope.Shared>().map { it.sharedId }
            dao.deleteEvents(scheduleIds, sharedIds)
        }
    }

    private fun LedgerEvent.toEntity(): LedgerEventEntity {
        val entity = LedgerEventEntity()
        entity.scheduleId = scheduleId
        entity.sharedId = sharedId
        entity.timestamp = timestamp.toEpochMilli()
        entity.body = toJsonValue()
        return entity
    }
}
