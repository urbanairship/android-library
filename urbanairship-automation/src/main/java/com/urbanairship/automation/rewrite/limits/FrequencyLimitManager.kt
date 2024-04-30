package com.urbanairship.automation.rewrite.limits

import android.content.Context
import android.database.sqlite.SQLiteException
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.automation.rewrite.limits.storage.ConstraintEntity
import com.urbanairship.automation.rewrite.limits.storage.FrequencyLimitDao
import com.urbanairship.automation.rewrite.limits.storage.FrequencyLimitDatabase
import com.urbanairship.automation.rewrite.limits.storage.OccurrenceEntity
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.util.Clock
import com.urbanairship.util.SerialQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

internal class FrequencyLimitManager(
    private val dao: FrequencyLimitDao,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) {

    private val lock = ReentrantLock()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val constraintMap: MutableMap<String, ConstraintInfo> = mutableMapOf()
    private val pendingOccurrences: MutableList<OccurrenceEntity> = mutableListOf()
    private val queue = SerialQueue()

    internal constructor(context: Context, config: AirshipRuntimeConfig) : this(FrequencyLimitDatabase.createDatabase(context, config).dao)


    @VisibleForTesting
    internal fun isOverLimit(constraintIDs: Collection<String>): Boolean {
        if (constraintIDs.isEmpty()) {
            return false
        }

        return lock.withLock {
            constraintIDs.any {
                val info = constraintMap[it] ?: return@any false

                if (info.occurrences.size < info.constraint.count) {
                    return@any false
                }

                info.occurrences.sortWith(OccurrenceEntity.Comparator())

                val occurrenceTimestamp = info.occurrences.get(info.occurrences.size - info.constraint.count).timeStamp
                return@any (clock.currentTimeMillis() - occurrenceTimestamp) <= info.constraint.range
            }
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

        recordOccurrence(constraintIDs)
        return true
    }

    private fun recordOccurrence(constraintIDs: List<String>) {
        if (constraintIDs.isEmpty()) {
            return
        }

        val time = clock.currentTimeMillis()

        lock.withLock {
            constraintIDs.forEach { id ->
                constraintMap[id]?.let { info ->
                    val occurrence = OccurrenceEntity()
                    occurrence.parentConstraintId = id
                    occurrence.timeStamp = time

                    info.occurrences.add(occurrence)
                    pendingOccurrences.add(occurrence)
                }
            }
        }


        scope.launch {
            queue.run {
                savePendingOccurrences()
            }
        }
    }

    @VisibleForTesting
    internal suspend fun savePendingOccurrences() {
        val toSave = lock.withLock {
            val copy = pendingOccurrences.map { it }
            pendingOccurrences.clear()
            copy
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
     * @return Instance of [Result<FrequencyChecker>].
     */
    suspend fun getFrequencyChecker(constraintIds: List<String>?): Result<FrequencyChecker?> {
        if (constraintIds.isNullOrEmpty()) {
            return Result.success(null)
        }

        return queue.run {
            savePendingOccurrences()

            val fetched = constraintMap.keys
            val need = constraintIds.subtract(fetched)

            for (id in need) {
                val constraint = dao.getConstraint(id) ?: return@run Result.failure(
                    IllegalStateException("Missing frequency constraint: $id")
                )

                val occurrenceEntities = dao.getOccurrences(id) ?: listOf()
                constraintMap[id] = ConstraintInfo(constraint, occurrenceEntities.toMutableList())
            }

            val frequencyChecker = object: FrequencyChecker {
                override fun isOverLimit(): Boolean = this@FrequencyLimitManager.isOverLimit(constraintIds)
                override fun checkAndIncrement(): Boolean = this@FrequencyLimitManager.checkAndIncrement(constraintIds)
            }

            return@run Result.success(frequencyChecker)
        }
    }

    /**
     * Called to update constraints.
     *
     * @param constraints The constraints.
     * @return Instance of [Result<Unit>].
     */
    suspend fun setConstraints(constraints: List<FrequencyConstraint>): Result<Unit> {
        return queue.run {
            savePendingOccurrences()

            try {
                val existing = dao.getAllConstraints() ?: listOf()
                val incomingIds = constraints.map { it.identifier }

                // upsert new
                val toUpsert = constraints
                    .map { it.makeEntity() }
                    .filter { !existing.contains(it) }

                // delete old
                val toDelete = existing
                    .filter { constraint ->
                        if (!incomingIds.contains(constraint.constraintId)) {
                            return@filter true
                        }

                        return@filter constraints.any { incoming ->
                            constraint.constraintId == incoming.identifier && constraint.range != incoming.range
                        }
                    }
                    .mapNotNull { it.constraintId }


                if (toDelete.isNotEmpty()) {
                    dao.delete(toDelete)
                    dao.deleteOccurrences(toDelete)

                    lock.withLock {
                        toDelete.forEach(constraintMap::remove)
                    }
                }

                toUpsert.forEach { entity ->
                    dao.insert(entity)
                    entity.constraintId?.let {
                        lock.withLock {
                            constraintMap[it] = ConstraintInfo(entity, mutableListOf())
                        }
                    }
                }
                Result.success(Unit)
            } catch (ex: Exception) {
                UALog.e(ex) { "failed to update constraints" }
                Result.failure(ex)
            }
        }
    }

}

private data class ConstraintInfo(
    val constraint: ConstraintEntity,
    val occurrences: MutableList<OccurrenceEntity>
)
