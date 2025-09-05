/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.core.content.ContextCompat
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.urbanairship.UALog
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.engine.triggerprocessor.TriggerData
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.db.SuspendingBatchedQueryHelper.collectBatched
import com.urbanairship.db.SuspendingBatchedQueryHelper.runBatched
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonTypeConverters
import com.urbanairship.json.JsonValue
import com.urbanairship.util.SerialQueue
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.util.UUID

internal interface AutomationStoreInterface: ScheduleStoreInterface, TriggerStoreInterface {}

internal interface ScheduleStoreInterface {
    suspend fun getSchedules(): List<AutomationScheduleData>
    suspend fun updateSchedule(
        id: String,
        closure: (AutomationScheduleData) -> AutomationScheduleData
    ): AutomationScheduleData?

    suspend fun upsertSchedules(
        ids: List<String>,
        closure: (String, AutomationScheduleData?) -> AutomationScheduleData
    ): List<AutomationScheduleData>

    suspend fun deleteSchedules(ids: List<String>)
    suspend fun deleteSchedules(group: String)

    suspend fun getSchedule(id: String): AutomationScheduleData?
    suspend fun getSchedules(group: String): List<AutomationScheduleData>
    suspend fun getSchedules(ids: List<String>): List<AutomationScheduleData>
}

internal interface TriggerStoreInterface {
    @Throws(JsonException::class)
    suspend fun getTrigger(scheduleID: String, triggerID: String): TriggerData?
    suspend fun upsertTriggers(triggers: List<TriggerData>)
    suspend fun deleteTriggersExcluding(scheduleIDs: List<String>)
    suspend fun deleteTriggers(scheduleIDs: List<String>)
    suspend fun deleteTriggers(scheduleID: String, triggerIDs: Set<String>)
}

internal class SerialAccessAutomationStore(private val store: AutomationStoreInterface):
    AutomationStoreInterface {

    private val queue = SerialQueue()
    override suspend fun getSchedules(): List<AutomationScheduleData> = queue.run { store.getSchedules() }

    override suspend fun getSchedules(group: String): List<AutomationScheduleData> = queue.run { store.getSchedules(group) }

    override suspend fun getSchedules(ids: List<String>): List<AutomationScheduleData> = queue.run { store.getSchedules(ids) }

    override suspend fun updateSchedule(
        id: String, closure: (AutomationScheduleData) -> AutomationScheduleData
    ): AutomationScheduleData? = queue.run { store.updateSchedule(id, closure) }

    override suspend fun upsertSchedules(
        ids: List<String>, closure: (String, AutomationScheduleData?) -> AutomationScheduleData
    ): List<AutomationScheduleData> = queue.run { store.upsertSchedules(ids, closure) }

    override suspend fun deleteSchedules(ids: List<String>) = queue.run { store.deleteSchedules(ids) }

    override suspend fun deleteSchedules(group: String) = queue.run { store.deleteSchedules(group) }

    override suspend fun getSchedule(id: String): AutomationScheduleData? = queue.run { store.getSchedule(id) }

    override suspend fun getTrigger(scheduleID: String, triggerID: String): TriggerData? = queue.run { store.getTrigger(scheduleID, triggerID) }

    override suspend fun upsertTriggers(triggers: List<TriggerData>) = queue.run { store.upsertTriggers(triggers) }

    override suspend fun deleteTriggersExcluding(scheduleIDs: List<String>) = queue.run { store.deleteTriggersExcluding(scheduleIDs) }

    override suspend fun deleteTriggers(scheduleIDs: List<String>) = queue.run { store.deleteTriggers(scheduleIDs) }

    override suspend fun deleteTriggers(scheduleID: String, triggerIDs: Set<String>) = queue.run { store.deleteTriggers(scheduleID, triggerIDs) }
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = [ScheduleEntity::class, TriggerEntity::class], version = 4)
internal abstract class AutomationStore : RoomDatabase(), AutomationStoreInterface {
    internal abstract val dao: AutomationDao

    internal companion object {

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN triggerSessionId TEXT")
                db.execSQL("ALTER TABLE schedules ADD COLUMN associatedData TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE automation_trigger_data")
                db.execSQL("CREATE TABLE IF NOT EXISTS automation_trigger_data (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `triggerId` TEXT NOT NULL, `scheduleId` TEXT NOT NULL, `state` TEXT NOT NULL)")
            }
        }

        /**
         * This migration cleans up any schedules that may have been created with an empty
         * or null fields that shouldn't be nullable and ensures that we have the desired schema,
         * regardless of migration path.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val newTable = "schedules_new"
                val oldTable = "schedules"

                // Create new schedules table (no schema changes from v3).
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `$newTable` (
                    `scheduleId` TEXT NOT NULL,
                    `group` TEXT,
                    `executionCount` INTEGER NOT NULL,
                    `preparedScheduleInfo` TEXT,
                    `schedule` TEXT NOT NULL,
                    `scheduleState` TEXT NOT NULL,
                    `scheduleStateChangeDate` INTEGER NOT NULL,
                    `triggerInfo` TEXT,
                    `triggerSessionId` TEXT,
                    `associatedData` TEXT,
                    PRIMARY KEY(`scheduleId`))
                """.trimIndent())

                // Copy existing rows to the new table, where we have values for all non-null fields.
                db.execSQL("""
                    INSERT INTO `$newTable` (scheduleId, `group`, executionCount, preparedScheduleInfo, schedule, scheduleState, scheduleStateChangeDate, triggerInfo, triggerSessionId, associatedData)
                    SELECT scheduleId, `group`, executionCount, preparedScheduleInfo, schedule, scheduleState, scheduleStateChangeDate, triggerInfo, triggerSessionId, associatedData
                    FROM `$oldTable`
                    WHERE scheduleId IS NOT NULL AND scheduleId != ''
                    AND executionCount IS NOT NULL
                    AND schedule IS NOT NULL AND schedule != ''
                    AND scheduleState IS NOT NULL AND scheduleState != ''
                    AND scheduleStateChangeDate IS NOT NULL
                """.trimIndent())

                // Drop the old schedules table and rename the new one in its place.
                db.execSQL("DROP TABLE IF EXISTS `$oldTable`")
                db.execSQL("ALTER TABLE `$newTable` RENAME TO `$oldTable`")
            }
        }

        fun createDatabase(context: Context, config: AirshipRuntimeConfig): AutomationStore {
            val name = config.configOptions.appKey + "_automation_store"
            val path = File(ContextCompat.getNoBackupFilesDir(context), name).absolutePath
            return databaseBuilder(context, AutomationStore::class.java, path)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }

        @VisibleForTesting
        internal fun createInMemoryDatabase(context: Context): AutomationStore =
            Room.inMemoryDatabaseBuilder(context, AutomationStore::class.java)
                .allowMainThreadQueries()
                .build()
    }

    override suspend fun getSchedules(): List<AutomationScheduleData> {
        val allScheduleEntities = dao.getAllSchedules() ?: return listOf()
        val validScheduleData = mutableListOf<AutomationScheduleData>()
        val schedulesToDelete = mutableListOf<String>()

        allScheduleEntities.forEach { entity ->
            val scheduleData = entity.toScheduleData()
            if (scheduleData != null) {
                validScheduleData.add(scheduleData)
            } else {
                schedulesToDelete.add(entity.scheduleId)
            }
        }

        if (schedulesToDelete.isNotEmpty()) {
            UALog.e("Deleting schedules due to parse exceptions: $schedulesToDelete")
            dao.deleteSchedules(schedulesToDelete)
        }

        return validScheduleData
    }

    override suspend fun getSchedules(group: String): List<AutomationScheduleData> {
        return dao.getSchedules(group)?.mapNotNull { it.toScheduleData() } ?: listOf()
    }

    @Transaction
    override suspend fun getSchedules(ids: List<String>): List<AutomationScheduleData> {
        return dao.getSchedules(ids).mapNotNull { it.toScheduleData() }
    }

    override suspend fun updateSchedule(
        id: String, closure: (AutomationScheduleData) -> AutomationScheduleData
    ): AutomationScheduleData? {
        return dao.updateSchedule(id, closure)
    }

    override suspend fun upsertSchedules(
        ids: List<String>, closure: (String, AutomationScheduleData?) -> AutomationScheduleData
    ): List<AutomationScheduleData> {
        return dao.upsertSchedules(ids, closure)
    }

    override suspend fun deleteSchedules(ids: List<String>) {
        dao.deleteSchedules(ids)
    }

    override suspend fun deleteSchedules(group: String) {
        dao.deleteSchedules(group)
    }

    override suspend fun getSchedule(id: String): AutomationScheduleData? {
        return dao.getSchedule(id)?.toScheduleData()
    }

    override suspend fun getTrigger(scheduleID: String, triggerID: String): TriggerData? {
        return try {
            dao.getTrigger(scheduleID, triggerID)?.toTriggerData()
        } catch (e: JsonException) {
            UALog.w(e) { "Failed to get trigger: $triggerID for schedule: $scheduleID" }
            null
        }
    }

    override suspend fun upsertTriggers(triggers: List<TriggerData>) {
        dao.upsertTriggers(triggers.map { TriggerEntity(it) })
    }

    override suspend fun deleteTriggersExcluding(scheduleIDs: List<String>) {
        dao.deleteTriggersExcluding(scheduleIDs)
    }

    override suspend fun deleteTriggers(scheduleIDs: List<String>) {
        dao.deleteTriggers(scheduleIDs)
    }

    override suspend fun deleteTriggers(scheduleID: String, triggerIDs: Set<String>) {
        dao.deleteTriggers(scheduleID, triggerIDs)
    }
}

@Dao
internal interface AutomationDao {

    @Transaction
    suspend fun upsertSchedules(
        ids: List<String>, closure: (String, AutomationScheduleData?) -> AutomationScheduleData
    ): List<AutomationScheduleData> {
        val current = getSchedules(ids)
            .mapNotNull { it.toScheduleData() }
            .associateBy { it.schedule.identifier }

        val result = mutableListOf<AutomationScheduleData>()

        upsertSchedulesInternal(ids.map { id ->
            val updated = closure(id, current[id])
            ScheduleEntity.fromScheduleData(updated)
                .also { result.add(updated) }
        })

        return result
    }

    /**
     * This query is only for internal use, with batched queries
     * to avoid the max query params limit of 999.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedulesInternal(schedules: List<ScheduleEntity>)

    @Query("SELECT * FROM schedules")
    suspend fun getAllSchedules(): List<ScheduleEntity>?

    @Query("SELECT * FROM schedules WHERE (`group` = :group)")
    suspend fun getSchedules(group: String): List<ScheduleEntity>?

    @Transaction
    suspend fun getSchedules(ids: List<String>): List<ScheduleEntity> =
        collectBatched(ids) { batch -> getSchedulesBatchInternal(batch) }

    /**
     * This query is only for internal use, with batched queries
     * to avoid the max query params limit of 999.
     */
    @Query("SELECT * FROM schedules WHERE (scheduleId IN (:ids))")
    suspend fun getSchedulesBatchInternal(ids: List<String>): List<ScheduleEntity>?

    @Transaction
    suspend fun updateSchedule(
        id: String, closure: (AutomationScheduleData) -> AutomationScheduleData
    ): AutomationScheduleData? {
        val current = getSchedule(id)?.toScheduleData() ?: return null
        val updated = closure(current)

        updateScheduleInternal(ScheduleEntity.fromScheduleData(updated))

        return updated
    }

    @Update
    suspend fun updateScheduleInternal(schedule: ScheduleEntity)

    @Query("SELECT * FROM schedules WHERE scheduleId = :id")
    suspend fun getSchedule(id: String): ScheduleEntity?

    @Transaction
    suspend fun deleteSchedules(ids: List<String>) {
        runBatched(ids) { deleteSchedulesBatchInternal(it) }
    }

    /**
     * This query is only for internal use, with batched queries
     * to avoid the max query params limit of 999.
     */
    @Query("DELETE FROM schedules WHERE (scheduleId IN (:ids))")
    suspend fun deleteSchedulesBatchInternal(ids: List<String>)

    @Query("DELETE FROM schedules WHERE `group` = :group")
    suspend fun deleteSchedules(group: String)

    @Query("SELECT scheduleId FROM automation_trigger_data")
    suspend fun getTriggersScheduleIds(): List<String>?

    @Transaction
    suspend fun upsertTriggers(triggers: List<TriggerEntity>) {
        runBatched(triggers) { upsertTriggersInternal(it) }
    }

    /**
     * This query is only for internal use, with batched queries
     * to avoid the max query params limit of 999.
     */
    @Upsert
    suspend fun upsertTriggersInternal(triggers: List<TriggerEntity>)

    @Transaction
    @Query("SELECT * FROM automation_trigger_data WHERE scheduleId = :scheduleId AND triggerId = :triggerId LIMIT 1")
    suspend fun getTrigger(scheduleId: String, triggerId: String): TriggerEntity?

    @Transaction
    @Query("DELETE FROM automation_trigger_data WHERE scheduleId = :scheduleId AND triggerId = :triggerId")
    suspend fun deleteTrigger(scheduleId: String, triggerId: String)

    @Transaction
    suspend fun deleteTriggers(scheduleIds: List<String>) {
        runBatched(scheduleIds) { deleteTriggersInternal(it) }
    }

    /**
     * This query is only for internal use, with batched queries
     * to avoid the max query params limit of 999.
     */
    @Query("DELETE FROM automation_trigger_data WHERE scheduleId IN (:scheduleIds)")
    suspend fun deleteTriggersInternal(scheduleIds: List<String>)

    /**
     * Deletes all triggers for schedules not in the provided list.
     *
     * In order to avoid the max query params limit of 999, this method will
     * fetch all existing schedule IDs, remove the provided schedule IDs, and
     * then batch-delete the remaining schedule IDs.
     */
    @Transaction
    suspend fun deleteTriggersExcluding(scheduleIds: List<String>) {
        val allScheduleIds = getTriggersScheduleIds() ?: return
        val idsToDelete = allScheduleIds - scheduleIds.toSet()
        runBatched(idsToDelete) { deleteTriggersInternal(it) }
    }

    @Transaction
    suspend fun deleteTriggers(scheduleIds: String, triggerIds: Set<String>) {
        runBatched(triggerIds) { deleteTriggersInternal(scheduleIds, it) }
    }

    /**
     * This query is only for internal use, with batched queries
     * to avoid the max query params limit of 999.
     */
    @Query("DELETE FROM automation_trigger_data WHERE scheduleId = :scheduleIds AND triggerId IN (:triggerIds) ")
    suspend fun deleteTriggersInternal(scheduleIds: String, triggerIds: Set<String>)
}

@Entity(tableName = "schedules")
@TypeConverters(JsonTypeConverters::class)
internal class ScheduleEntity(
    @PrimaryKey
    var scheduleId: String,
    var group: String?,
    var executionCount: Int,
    var preparedScheduleInfo: JsonValue?,
    var schedule: JsonValue,
    var scheduleState: String,
    var scheduleStateChangeDate: Long,
    var triggerInfo: JsonValue?,
    var triggerSessionId: String?,
    var associatedData: JsonValue?
) {

    companion object {
        fun fromScheduleData(data: AutomationScheduleData): ScheduleEntity {
            return ScheduleEntity(
                scheduleId = data.schedule.identifier,
                group = data.schedule.group,
                executionCount = data.executionCount,
                preparedScheduleInfo = data.preparedScheduleInfo?.toJsonValue(),
                schedule = data.schedule.toJsonValue(),
                scheduleState = data.scheduleState.toString(),
                scheduleStateChangeDate = data.scheduleStateChangeDate,
                triggerInfo = data.triggerInfo?.toJsonValue(),
                triggerSessionId = data.triggerSessionId,
                associatedData = data.associatedData
            )
        }
    }

    fun toScheduleData(): AutomationScheduleData? {
        return try {
            AutomationScheduleData(
                schedule = AutomationSchedule.fromJson(schedule),
                scheduleState = AutomationScheduleState.fromString(scheduleState),
                scheduleStateChangeDate = scheduleStateChangeDate,
                executionCount = executionCount,
                triggerInfo = triggerInfo?.let(TriggeringInfo::fromJson),
                preparedScheduleInfo = preparedScheduleInfo?.let(PreparedScheduleInfo::fromJson),
                triggerSessionId = this.triggerSessionId ?: UUID.randomUUID().toString(),
                associatedData = associatedData
            )
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to convert schedule entity to schedule data $this" }
            null
        }
    }
}

/** @hide */
@Entity(tableName = "automation_trigger_data")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@TypeConverters(JsonTypeConverters::class)
internal data class TriggerEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    val triggerId: String,
    val scheduleId: String,
    val state: JsonValue
) {

    constructor(triggerData: TriggerData) : this(
        triggerId = triggerData.triggerId,
        scheduleId = triggerData.scheduleId,
        state = triggerData.toJsonValue()
    )

    @Throws(JsonException::class)
    fun toTriggerData(): TriggerData = TriggerData.fromJson(state)
}
