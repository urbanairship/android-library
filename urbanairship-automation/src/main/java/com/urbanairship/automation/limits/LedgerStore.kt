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

    /** Appends events to the ledger. */
    suspend fun recordEvents(events: List<LedgerEvent>)

    /**
     * Fetches the events eligible for a schedule's limit evaluation: every
     * event recorded under the schedule's own ID, plus every event recorded
     * under the schedule's current shared group ID (if any).
     *
     * @param scheduleId The evaluating schedule's ID.
     * @param sharedId The schedule's current shared group ID, if any.
     * @return The eligible events.
     */
    suspend fun events(scheduleId: String, sharedId: String?): List<LedgerEvent>

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
            events.forEach { dao.insert(it.toEntity()) }
        }
    }

    override suspend fun events(scheduleId: String, sharedId: String?): List<LedgerEvent> {
        UALog.v { "Fetching ledger events for schedule $scheduleId sharedId $sharedId" }

        return queue.run {
            val entities = if (sharedId != null) {
                dao.getEvents(scheduleId, sharedId)
            } else {
                dao.getEvents(scheduleId)
            }

            // Skip any undecodable rows (e.g. a forward-incompatible event
            // written by a newer SDK) rather than failing the whole query,
            // and return a stable ordering by record time.
            entities
                .mapNotNull { entity ->
                    try {
                        LedgerEvent.fromJson(entity.body)
                    } catch (e: JsonException) {
                        UALog.e(e) { "Failed to decode ledger event, skipping: ${entity.body}" }
                        null
                    }
                }
                .sortedBy { it.timestamp }
        }
    }

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
        entity.body = toJsonValue()
        return entity
    }
}
