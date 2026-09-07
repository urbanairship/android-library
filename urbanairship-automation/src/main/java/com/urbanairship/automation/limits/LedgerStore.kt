/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.automation.limits.storage.LedgerDao
import com.urbanairship.automation.limits.storage.LedgerDatabase
import com.urbanairship.automation.limits.storage.LedgerEventEntity
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonException
import com.urbanairship.util.SerialQueue
import java.time.Instant

/**
 * Persists and queries [LedgerEvent]s.
 *
 * Scope of this store is persistence only: writing real events and reading
 * during limit checks are handled by higher layers. It supports appending
 * events, querying the events eligible for a schedule's limit evaluation
 * (events recorded under the schedule's own ID or its current shared group
 * ID), deleting events by scope, and the two maintenance passes that bound
 * storage — retention and compaction.
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
     * Appends [events] unless one of the schedule's eligible events already
     * satisfies [alreadyRecorded].
     *
     * The read and the append are one critical section, so two callers cannot
     * both find nothing and both write. [alreadyRecorded] is tested against the
     * same event set [events] would join.
     *
     * @return true when the events were appended.
     */
    suspend fun recordEventsUnless(
        scheduleId: String,
        sharedId: String?,
        events: List<LedgerEvent>,
        alreadyRecorded: (LedgerEvent) -> Boolean
    ): Boolean

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

    /**
     * Retention: drops every event whose recording scopes are all dead.
     *
     * An event survives while any of the IDs it was recorded under is still
     * live, so a shared group's pooled history outlives individual variants. An
     * event is deleted only when its `scheduleId` is not in [liveScheduleIds]
     * **and** its `sharedId` (if any) is not in [liveSharedIds].
     *
     * @param liveScheduleIds Schedule IDs that still reference the ledger.
     * @param liveSharedIds Shared group IDs that still reference the ledger.
     */
    suspend fun retainEvents(liveScheduleIds: Set<String>, liveSharedIds: Set<String>)

    /**
     * Compaction: merges mergeable events per the age-tiered policy of
     * [LedgerCompactor] and enforces the global backstop cap, rewriting the
     * merged events in place.
     *
     * @param now Reference time used to bucket events by age.
     */
    suspend fun compact(now: Instant)
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

    override suspend fun recordEventsUnless(
        scheduleId: String,
        sharedId: String?,
        events: List<LedgerEvent>,
        alreadyRecorded: (LedgerEvent) -> Boolean
    ): Boolean = queue.run {
        val existing = dao.getEvents(scheduleId, sharedId).mapNotNull(::decode)
        if (existing.any(alreadyRecorded)) {
            return@run false
        }

        if (events.isNotEmpty()) {
            UALog.v { "Recording ledger events: $events" }
            dao.insertAll(events.map { it.toEntity() })
        }
        true
    }

    override suspend fun events(scheduleId: String, sharedId: String?): List<LedgerEvent> {
        UALog.v { "Fetching ledger events for schedule $scheduleId sharedId $sharedId" }

        return queue.run {
            // The query already orders by record time. Skip any undecodable rows
            // (e.g. a forward-incompatible event written by a newer SDK) rather
            // than failing the whole query.
            dao.getEvents(scheduleId, sharedId).mapNotNull(::decode)
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

    override suspend fun retainEvents(liveScheduleIds: Set<String>, liveSharedIds: Set<String>) {
        UALog.v {
            "Retaining ledger events for live schedules $liveScheduleIds shared $liveSharedIds"
        }

        queue.run {
            val deleted = dao.deleteOrphanedEvents(liveScheduleIds, liveSharedIds)
            if (deleted > 0) {
                UALog.v { "Deleted $deleted orphaned ledger events" }
            }
        }
    }

    override suspend fun compact(now: Instant) {
        compact(now, LedgerCompactor.DEFAULT_MAX_EVENTS)
    }

    /**
     * Cap-injectable variant used by tests to exercise the backstop path
     * without seeding tens of thousands of rows.
     */
    @VisibleForTesting
    internal suspend fun compact(now: Instant, maxEvents: Int) {
        queue.run {
            // Cheap pre-check before the full fetch and decode: compaction can
            // only remove rows when the ledger is over the global cap (the
            // backstop) or holds events old enough to age-bucket. Both are
            // answerable from indexed columns, so the common case — a ledger
            // under the cap with nothing older than a year — decodes nothing.
            val total = dao.count()
            if (total == 0) {
                return@run
            }

            val oldest = dao.oldestTimestamp()?.let(Instant::ofEpochMilli)
            val hasAgedEvents =
                oldest != null && oldest.isBefore(now.minus(LedgerCompactor.RAW_MAX_AGE))
            if (total <= maxEvents && !hasAgedEvents) {
                return@run
            }

            // Undecodable rows are left in place rather than dropped, so a
            // forward-incompatible event written by a newer SDK survives a
            // compaction pass by this one. They cannot be merged but still
            // occupy the cap, so they come off the compactor's budget.
            val rows = dao.getAllEvents()
            val decodable = rows.mapNotNull { row -> decode(row)?.let { row.id to it } }
            val undecodable = rows.size - decodable.size
            val budget = (maxEvents - undecodable).coerceAtLeast(0)

            val plan = LedgerCompactor.plan(decodable, now, budget)
            val remaining = rows.size - plan.replacedIds.size + plan.merged.size

            // Non-mergeable events can keep the ledger over the cap; log it
            // rather than silently exceeding the bound.
            if (remaining > maxEvents) {
                UALog.v {
                    "Ledger still holds $remaining events after compaction, over the cap " +
                            "of $maxEvents; remaining events are non-mergeable."
                }
            }

            if (plan.merged.isEmpty()) {
                return@run
            }

            UALog.v { "Compacting ledger from ${rows.size} to $remaining events" }

            // Only the rows that folded together are rewritten; every other row
            // keeps its identity, so one merged pair does not churn the ledger.
            dao.replaceEvents(
                deleteIds = plan.replacedIds,
                events = plan.merged.map { it.toEntity() }
            )
        }
    }

    /**
     * Decodes a stored row, or null when its body cannot be parsed (e.g. a
     * forward-incompatible event written by a newer SDK).
     */
    private fun decode(entity: LedgerEventEntity): LedgerEvent? = try {
        LedgerEvent.fromJson(entity.body)
    } catch (e: JsonException) {
        UALog.e(e) { "Failed to decode ledger event, skipping: ${entity.body}" }
        null
    }

    private fun LedgerEvent.toEntity(): LedgerEventEntity = LedgerEventEntity(
        scheduleId = scheduleId,
        sharedId = sharedId,
        timestamp = timestamp.toEpochMilli(),
        body = toJsonValue()
    )
}
