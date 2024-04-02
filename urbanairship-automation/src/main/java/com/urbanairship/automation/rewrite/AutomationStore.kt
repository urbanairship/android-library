package com.urbanairship.automation.rewrite

import android.content.Context
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
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.urbanairship.automation.rewrite.engine.AutomationScheduleState
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.engine.TriggeringInfo
import com.urbanairship.automation.rewrite.limits.storage.ConstraintEntity
import com.urbanairship.automation.rewrite.limits.storage.OccurrenceEntity
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonTypeConverters
import com.urbanairship.json.JsonValue
import java.io.File
import kotlin.jvm.Throws
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

@Database(entities = [ScheduleEntity::class], version = 1)
internal abstract class AutomationStore : RoomDatabase(), ScheduleStoreInterface {
    abstract val dao: AutomationDao

    companion object {
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
        return dao.getAllSchedules()?.map { it.toScheduleData() } ?: listOf()
    }

    override suspend fun getSchedules(group: String): List<AutomationScheduleData> {
        return dao.getSchedules(group)?.map { it.toScheduleData() } ?: listOf()
    }

    override suspend fun getSchedules(ids: List<String>): List<AutomationScheduleData> {
        return dao.getSchedules(ids)?.map { it.toScheduleData() } ?: listOf()
    }

    override suspend fun updateSchedule(
        id: String, closure: (AutomationScheduleData) -> AutomationScheduleData
    ): AutomationScheduleData? {

        val current = dao.getSchedule(id) ?: return null

        val updated = closure(current.toScheduleData())
        dao.update(ScheduleEntity.fromScheduleData(updated))

        return updated
    }

    override suspend fun upsertSchedules(
        ids: List<String>, closure: (String, AutomationScheduleData?) -> AutomationScheduleData
    ): List<AutomationScheduleData> {

        val current = dao.getSchedules(ids)
            ?.map { it.toScheduleData() }
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

    @Throws(JsonException::class, IllegalArgumentException::class)
    fun toScheduleData(): AutomationScheduleData {
        return AutomationScheduleData(
            schedule = AutomationSchedule.fromJson(schedule),
            scheduleState = AutomationScheduleState.fromString(scheduleState),
            scheduleStateChangeDate = scheduleStateChangeDate,
            executionCount = executionCount,
            triggerInfo = triggerInfo?.let(TriggeringInfo::fromJson),
            preparedScheduleInfo = preparedScheduleInfo?.let(PreparedScheduleInfo::fromJson)
        )
    }
}
