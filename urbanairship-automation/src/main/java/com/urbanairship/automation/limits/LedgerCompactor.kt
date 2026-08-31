/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.annotation.VisibleForTesting
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Pure ledger-compaction math, kept separate from persistence so the merge and
 * bucketing logic is testable without a database.
 *
 * Compaction merges events that are *mergeable* — identical in every field
 * except `timestamp` and `count`, recorded under the same scope — into one
 * event that sums `count` and keeps the newest `timestamp`. Events differing in
 * scope, `type`, `trigger_id`, `result`, or `cancel` never merge.
 *
 * How aggressively mergeable events actually merge depends on their age, so
 * recent history keeps the full timestamp precision that exclusion rules match
 * time bounds against:
 * - younger than a year: raw (never merged by time)
 * - one to two years: merged into monthly buckets
 * - older than two years: merged into yearly buckets
 *
 * A global backstop guards against unbounded growth: if the ledger still holds
 * more than [DEFAULT_MAX_EVENTS] after age-tiered merging, whole mergeable
 * groups are collapsed across all ages, oldest history first, until under the
 * cap.
 */
internal object LedgerCompactor {

    /** Events younger than this stay raw (never merged by time). One year. */
    val RAW_MAX_AGE: Duration = Duration.ofDays(365)

    /** Events at least this old collapse into yearly buckets. Two years. */
    val YEARLY_MIN_AGE: Duration = Duration.ofDays(2 * 365)

    /** Global cap before the age-agnostic backstop kicks in. */
    const val DEFAULT_MAX_EVENTS: Int = 10_000

    /** UTC so bucketing is stable regardless of the device's time zone. */
    private val ZONE: ZoneOffset = ZoneOffset.UTC

    /**
     * The fields that must match for two events to be mergeable. `timestamp`
     * and `count` are deliberately excluded: timestamp is bucketed by age and
     * count is summed.
     */
    internal data class Key(
        val type: LedgerEventType,
        val scheduleId: String,
        val sharedId: String?,
        val triggerId: String?,
        val result: LedgerExecutionResult?,
        val cancel: Boolean?
    )

    /** The time bucket an event merges within, coarsening with age. */
    internal sealed class Bucket {
        /** Younger than a year: the exact timestamp, so distinct times never merge. */
        data class Raw(val timestamp: Instant) : Bucket()

        /** One to two years old: calendar month. */
        data class Monthly(val year: Int, val month: Int) : Bucket()

        /** Older than two years: calendar year. */
        data class Yearly(val year: Int) : Bucket()
    }

    private data class BucketKey(val key: Key, val bucket: Bucket)

    /**
     * A set of rows that compaction folds into a single row, identified by
     * whatever the caller uses to address them. A cluster of one row is a row
     * nothing merged with, and is left exactly as it is.
     */
    private data class Cluster<ID>(val ids: List<ID>, val event: LedgerEvent)

    /**
     * The rewrite compaction implies over identified rows: delete
     * [replacedIds], insert [merged]. Rows that merged with nothing appear in
     * neither list, so the caller leaves them in place — a pass that folds one
     * pair does not rewrite the whole ledger.
     */
    internal data class Plan<ID>(val replacedIds: List<ID>, val merged: List<LedgerEvent>)

    internal fun key(event: LedgerEvent): Key = when (event) {
        is LedgerEvent.Triggered -> Key(
            type = event.type,
            scheduleId = event.scheduleId,
            sharedId = event.sharedId,
            triggerId = event.triggerId,
            result = null,
            cancel = null
        )

        is LedgerEvent.Execution -> Key(
            type = event.type,
            scheduleId = event.scheduleId,
            sharedId = event.sharedId,
            triggerId = event.triggerId,
            result = event.result,
            cancel = event.cancel
        )
    }

    internal fun bucket(timestamp: Instant, now: Instant): Bucket {
        val age = Duration.between(timestamp, now)
        if (age < RAW_MAX_AGE) {
            return Bucket.Raw(timestamp)
        }

        val date = timestamp.atZone(ZONE)
        return if (age < YEARLY_MIN_AGE) {
            Bucket.Monthly(year = date.year, month = date.monthValue)
        } else {
            Bucket.Yearly(year = date.year)
        }
    }

    /**
     * Plans compaction over identified rows: which rows to replace, and the
     * merged events that replace them.
     *
     * Rows no group merged are absent from the plan entirely, so applying it
     * touches only what actually changed.
     */
    internal fun <ID> plan(
        rows: List<Pair<ID, LedgerEvent>>,
        now: Instant,
        maxEvents: Int = DEFAULT_MAX_EVENTS
    ): Plan<ID> {
        if (rows.isEmpty()) {
            return Plan(emptyList(), emptyList())
        }

        // 1. Age-tiered merge: group by (mergeable key, age bucket), merge each.
        var clusters = rows
            .groupBy { (_, event) -> BucketKey(key(event), bucket(event.timestamp, now)) }
            .values
            .map(::cluster)

        // 2. Backstop: each cluster is one row, so if that is still over the cap,
        //    collapse whole keys across all ages, oldest history first.
        if (clusters.size > maxEvents) {
            clusters = applyBackstop(clusters, maxEvents)
        }

        // Only clusters that actually folded rows together imply a rewrite.
        val folded = clusters.filter { it.ids.size > 1 }

        return Plan(
            replacedIds = folded.flatMap { it.ids },
            merged = folded.map { it.event }.sortedWith(ORDERING)
        )
    }

    /**
     * Compacts [events] and returns the whole merged set in a deterministic
     * order — the declarative form of the policy, which the tests assert
     * against. Persistence applies [plan] instead, so it can rewrite only the
     * rows that changed.
     */
    @VisibleForTesting
    internal fun compact(
        events: List<LedgerEvent>,
        now: Instant,
        maxEvents: Int = DEFAULT_MAX_EVENTS
    ): List<LedgerEvent> {
        val rows = events.withIndex().map { (index, event) -> index to event }
        val plan = plan(rows, now, maxEvents)
        val replaced = plan.replacedIds.toSet()

        val survivors = rows.filterNot { it.first in replaced }.map { it.second }
        return (survivors + plan.merged).sortedWith(ORDERING)
    }

    /**
     * Folds a group of rows known to share a [Key] and an age bucket into one
     * cluster. A group of one is carried through untouched, so its original
     * `count` and `timestamp` are never rewritten.
     */
    private fun <ID> cluster(group: List<Pair<ID, LedgerEvent>>): Cluster<ID> = Cluster(
        ids = group.map { it.first },
        event = merge(group.map { it.second })
    )

    /**
     * Merges events known to share a [Key]: sums `count`, keeps the newest
     * timestamp. A single event is returned untouched. The input is always a
     * non-empty group.
     */
    private fun merge(events: List<LedgerEvent>): LedgerEvent {
        val first = events.first()
        if (events.size == 1) {
            return first
        }

        val total = events.sumOf { it.effectiveCount }
        val newest = events.maxOf { it.timestamp }

        return when (first) {
            is LedgerEvent.Triggered -> first.copy(timestamp = newest, count = total)
            is LedgerEvent.Execution -> first.copy(timestamp = newest, count = total)
        }
    }

    private fun <ID> applyBackstop(
        clusters: List<Cluster<ID>>,
        maxEvents: Int
    ): List<Cluster<ID>> {
        val groups = clusters.groupBy { key(it.event) }.toMutableMap()

        // Collapse the groups holding the oldest events first.
        val orderedKeys = groups.entries
            .sortedBy { entry -> entry.value.minOf { it.event.timestamp } }
            .map { it.key }

        var count = clusters.size
        for (key in orderedKeys) {
            if (count <= maxEvents) {
                break
            }

            val group = groups[key] ?: continue
            if (group.size <= 1) {
                continue
            }

            groups[key] = listOf(
                Cluster(
                    ids = group.flatMap { it.ids },
                    event = merge(group.map { it.event })
                )
            )
            count -= group.size - 1
        }

        return groups.values.flatten()
    }

    /**
     * Stable ordering for deterministic output. Ties are broken all the way
     * down the mergeable-key fields so the result never depends on fetch or
     * iteration order — two events equal on every field here would have merged.
     */
    private val ORDERING: Comparator<LedgerEvent> = compareBy(
        { it.timestamp },
        { it.scheduleId },
        { it.sharedId ?: "" },
        { it.type.json },
        { it.triggerId ?: "" },
        { (it as? LedgerEvent.Execution)?.result?.json ?: "" },
        { (it as? LedgerEvent.Execution)?.cancel?.toString() ?: "" }
    )
}
