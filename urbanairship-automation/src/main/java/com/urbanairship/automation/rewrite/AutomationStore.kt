package com.urbanairship.automation.rewrite

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
import androidx.room.TypeConverters
import androidx.room.Update
import com.urbanairship.UALog
import com.urbanairship.automation.rewrite.engine.AutomationScheduleState
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.engine.TriggeringInfo
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonTypeConverters
import com.urbanairship.json.JsonValue
import java.io.File
import org.jetbrains.annotations.VisibleForTesting

internal interface ScheduleStoreInterface {
    suspend fun getSchedules(): List<AutomationScheduleData>
    suspend fun updateSchedule(
        id: String,
        closure: (AutomationScheduleData) -> AutomationScheduleData): AutomationScheduleData?

    suspend fun upsertSchedules(
        ids: List<String>,
        closure: (String, AutomationScheduleData?) -> AutomationScheduleData): List<AutomationScheduleData>

    suspend fun deleteSchedules(ids: List<String>)
    suspend fun deleteSchedules(group: String)

    suspend fun getSchedule(id: String): AutomationScheduleData?
    suspend fun getSchedules(group: String): List<AutomationScheduleData>
    suspend fun getSchedules(ids: List<String>): List<AutomationScheduleData>
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = [ScheduleEntity::class], version = 1)
public abstract class AutomationStore : RoomDatabase(), ScheduleStoreInterface {
    internal abstract val dao: AutomationDao

    internal companion object {
        fun createDatabase(context: Context, config: AirshipRuntimeConfig): AutomationStore {
            val name = config.configOptions.appKey + "_automation_store"
            val path = File(ContextCompat.getNoBackupFilesDir(context), name).absolutePath
            return databaseBuilder(
                context,
                AutomationStore::class.java,
                path
            ).fallbackToDestructiveMigrationOnDowngrade().build()
        }

        @VisibleForTesting
        internal fun createInMemoryDatabase(context: Context): AutomationStore =
            Room.inMemoryDatabaseBuilder(context, AutomationStore::class.java)
                .allowMainThreadQueries()
                .build()
    }

    override suspend fun getSchedules(): List<AutomationScheduleData> {
        return dao.getAllSchedules()?.mapNotNull { it.toScheduleData() } ?: listOf()
    }

    override suspend fun getSchedules(group: String): List<AutomationScheduleData> {
        return dao.getSchedules(group)?.mapNotNull { it.toScheduleData() } ?: listOf()
    }

    override suspend fun getSchedules(ids: List<String>): List<AutomationScheduleData> {
        return dao.getSchedules(ids)?.mapNotNull { it.toScheduleData() } ?: listOf()
    }

    override suspend fun updateSchedule(
        id: String, closure: (AutomationScheduleData) -> AutomationScheduleData
    ): AutomationScheduleData? {

        val current = dao.getSchedule(id)?.toScheduleData() ?: return null

        val updated = closure(current)
        dao.update(ScheduleEntity.fromScheduleData(updated))

        return updated
    }

    override suspend fun upsertSchedules(
        ids: List<String>, closure: (String, AutomationScheduleData?) -> AutomationScheduleData
    ): List<AutomationScheduleData> {

        val current = dao.getSchedules(ids)
            ?.mapNotNull { it.toScheduleData() }
            ?.associateBy { it.schedule.identifier } ?: mapOf()

        val result = mutableListOf<AutomationScheduleData>()

        ids.forEach {id ->
            val updated = closure(id, current[id])
            dao.insert(ScheduleEntity.fromScheduleData(updated))
            result.add(updated)
        }

        return result
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
}

@Dao
internal interface AutomationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity)

    @Query("SELECT * FROM schedules")
    suspend fun getAllSchedules(): List<ScheduleEntity>?

    @Query("SELECT * FROM schedules WHERE (`group` = :group)")
    suspend fun getSchedules(group: String): List<ScheduleEntity>?

    @Query("SELECT * FROM schedules WHERE (identifier IN (:ids))")
    suspend fun getSchedules(ids: List<String>): List<ScheduleEntity>?

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("SELECT * FROM schedules WHERE identifier = :id")
    suspend fun getSchedule(id: String): ScheduleEntity?

    @Query("DELETE FROM schedules WHERE (identifier IN (:ids))")
    suspend fun deleteSchedules(ids: List<String>)

    @Query("DELETE FROM schedules WHERE `group` = :group")
    suspend fun deleteSchedules(group: String)
}

@Entity(tableName = "schedules")
@TypeConverters(JsonTypeConverters::class)
internal class ScheduleEntity(
    @PrimaryKey
    var identifier: String,
    var group: String?,
    var executionCount: Int,
    var preparedScheduleInfo: JsonValue?,
    var schedule: JsonValue,
    var scheduleState: String,
    var scheduleStateChangeDate: Long,
    var triggerInfo: JsonValue?
) {
    companion object {
        fun fromScheduleData(data: AutomationScheduleData): ScheduleEntity {
            return ScheduleEntity(
                identifier = data.schedule.identifier,
                group = data.schedule.group,
                executionCount = data.executionCount,
                preparedScheduleInfo = data.preparedScheduleInfo?.toJsonValue(),
                schedule = data.schedule.toJsonValue(),
                scheduleState = data.scheduleState.toString(),
                scheduleStateChangeDate = data.scheduleStateChangeDate,
                triggerInfo = data.triggerInfo?.toJsonValue()
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
                preparedScheduleInfo = preparedScheduleInfo?.let(PreparedScheduleInfo::fromJson)
            )
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to convert schedule entity to schedule data $this" }
            null
        }
    }
}
