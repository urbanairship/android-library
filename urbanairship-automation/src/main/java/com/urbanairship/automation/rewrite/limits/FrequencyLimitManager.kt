package com.urbanairship.automation.rewrite.limits

import android.content.Context
import android.database.sqlite.SQLiteException
import com.urbanairship.UALog
import com.urbanairship.automation.rewrite.limits.storage.ConstraintEntity
import com.urbanairship.automation.rewrite.limits.storage.FrequencyLimitDao
import com.urbanairship.automation.rewrite.limits.storage.FrequencyLimitDatabase
import com.urbanairship.automation.rewrite.limits.storage.OccurrenceEntity
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.util.Clock
import com.urbanairship.util.SerialQueue
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.VisibleForTesting

internal interface FrequencyLimitManagerInterface {
    suspend fun getFrequencyChecker(constraintIDs: List<String>?): FrequencyCheckerInterface
    suspend fun setConstraints(constraints: List<FrequencyConstraint>)
}
//TODO: make sure the implementation is correct from threading stand point
internal class FrequencyLimitManager(
    private val dao: FrequencyLimitDao,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
) : FrequencyLimitManagerInterface {

    private val lock = Object()
    private val cachedConstraints: MutableMap<String, ConstraintInfo> = mutableMapOf()
    private val pendingOccurrences: MutableList<OccurrenceEntity> = mutableListOf()
    private val queue = SerialQueue()

    internal constructor(context: Context, config: AirshipRuntimeConfig) : this(FrequencyLimitDatabase.createDatabase(context, config).dao)

    private val emptyChecker = object : FrequencyCheckerInterface {
        override suspend fun isOverLimit(): Boolean = false
        override suspend fun checkAndIncrement(): Boolean = true
    }

    private fun hasCachedConstraint(id: String): Boolean {
        return synchronized(lock) { cachedConstraints.containsKey(id) }
    }

    private fun cacheConstraint(id: String, entity: ConstraintEntity) {
        synchronized(lock) {
            if (!cachedConstraints.containsKey(id)) {
                cachedConstraints[id] = ConstraintInfo(entity, mutableListOf())
            }
        }
    }

    private fun cacheOccurrences(id: String, items: List<OccurrenceEntity>) {
        synchronized(lock) {
            cachedConstraints.get(id)?.occurrences?.addAll(items)
        }
    }

    @VisibleForTesting
    internal fun isOverLimit(constraintIDs: List<String>): Boolean {
        if (constraintIDs.isEmpty()) {
            return false
        }

        return constraintIDs.any {
            val info = synchronized(lock) { cachedConstraints[it] } ?: return@any false

            if (info.occurrences.size < info.constraint.count) {
                return@any false
            }

            info.occurrences.sortWith(OccurrenceEntity.Comparator())

            val occurrenceTimestamp = info.occurrences.get(info.occurrences.size - info.constraint.count).timeStamp
            return@any (clock.currentTimeMillis() - occurrenceTimestamp) <= info.constraint.range
        }
    }

    @VisibleForTesting
    internal fun checkAndIncrement(constraintIDs: List<String>): Boolean {
        if (constraintIDs.isEmpty()) {
            return true
        }

        if (isOverLimit(constraintIDs)) {
            return false
        }

        runBlocking { recordOccurrence(constraintIDs) }
        return true
    }

    private suspend fun recordOccurrence(constraintIDs: List<String>) {
        if (constraintIDs.isEmpty()) {
            return
        }

        val time = clock.currentTimeMillis()
        val pending = constraintIDs.mapNotNull { id ->
            if (synchronized(lock) { !cachedConstraints.containsKey(id) }) {
                return@mapNotNull null
            }

            val result = OccurrenceEntity()
            result.parentConstraintId = id
            result.timeStamp = time

            synchronized(lock) {
                cachedConstraints[id]?.occurrences?.add(result)
            }

            return@mapNotNull result
        }

        synchronized(lock) { pendingOccurrences.addAll(pending) }
        savePendingOccurrences()
    }

    @VisibleForTesting
    internal suspend fun savePendingOccurrences() {
        val toSave = synchronized(lock) {
            val copy = pendingOccurrences.map { it }
            pendingOccurrences.clear()
            return@synchronized copy
        }

        toSave.forEach { item ->
            try {
                dao.insert(item)
            } catch (ex: SQLiteException) {
                UALog.v(ex)
            }
        }
    }

    /**
     * Gets a frequency checker for the current constraints.
     *
     * The checker will keep a snapshot of the constraint definition at the time of checker creation.
     * Any updates to the constraints will be ignored until a new checker is created.
     *
     * @param constraintIds The collection of constraint Ids.
     * @return Instance of [FrequencyCheckerInterface].
     */
    override suspend fun getFrequencyChecker(constraintIDs: List<String>?): FrequencyCheckerInterface {
        val constraints = constraintIDs ?: return emptyChecker

        return queue.run {
            for (id in constraints) {
                if (hasCachedConstraint(id)) {
                    continue
                }

                val occurrenceEntities = dao.getOccurrences(id) ?: listOf()
                val constraint = dao.getConstraint(id) ?: return@run emptyChecker

                cacheConstraint(id, constraint)
                cacheOccurrences(id, occurrenceEntities)
            }

            return@run object : FrequencyCheckerInterface {
                override suspend fun isOverLimit(): Boolean = this@FrequencyLimitManager.isOverLimit(constraints)
                override suspend fun checkAndIncrement(): Boolean = this@FrequencyLimitManager.checkAndIncrement(constraintIDs)
            }
        }
    }

    /**
     * Called to update constraints.
     *
     * @param constraints The constraints.
     */
    override suspend fun setConstraints(constraints: List<FrequencyConstraint>) {
        savePendingOccurrences()

        queue.run {
            try {
                val existing = dao.getAllConstraints() ?: listOf()

                //upsert new
                val toUpsert = constraints
                    .map { it.makeEntity() }
                    .filter { !existing.contains(it) }

                //delete old
                val toDelete = existing
                    .filter { item ->
                        val incoming = constraints.firstOrNull { it.identifier == item.constraintId } ?: return@filter true
                        return@filter incoming.range != item.range
                    }
                    .mapNotNull { it.constraintId }

                if (toDelete.isNotEmpty()) {
                    dao.delete(toDelete)
                    dao.deleteOccurrences(toDelete)
                    synchronized(lock) { toDelete.forEach(cachedConstraints::remove) }
                }

                toUpsert.forEach { entity ->
                    dao.insert(entity)
                    entity.constraintId?.let { cacheConstraint(it, entity) }
                }
            } catch (ex: Exception) {
                UALog.e(ex) { "failed to update constraints" }
            }
        }
    }
}

private data class ConstraintInfo(
    val constraint: ConstraintEntity,
    val occurrences: MutableList<OccurrenceEntity>
)
