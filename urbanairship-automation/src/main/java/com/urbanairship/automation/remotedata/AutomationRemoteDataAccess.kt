/* Copyright Airship and Contributors */

package com.urbanairship.automation.remotedata

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.iam.InAppMessage
import com.urbanairship.automation.limits.FrequencyConstraint
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataPayload
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.Network
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Remote data access for automation
 */
internal interface AutomationRemoteDataAccessInterface {
    val updatesFlow: Flow<InAppRemoteData>
    suspend fun isCurrent(schedule: AutomationSchedule): Boolean
    suspend fun requiredUpdate(schedule: AutomationSchedule): Boolean
    suspend fun waitForFullRefresh(schedule: AutomationSchedule)
    suspend fun bestEffortRefresh(schedule: AutomationSchedule): Boolean
    suspend fun notifyOutdated(schedule: AutomationSchedule)
    fun contactIdFor(schedule: AutomationSchedule): String?
    fun sourceFor(schedule: AutomationSchedule): RemoteDataSource?
}

internal class AutomationRemoteDataAccess(
    private val context: Context,
    private val remoteData: RemoteData,
    private val network: Network = Network.shared()
): AutomationRemoteDataAccessInterface {
    internal companion object {
        private val REMOTE_DATA_TYPES = listOf("in_app_messages")
    }

    override val updatesFlow: Flow<InAppRemoteData> = remoteData
        .payloadFlow(REMOTE_DATA_TYPES)
        .map(InAppRemoteData.Companion::fromPayloads)

    override suspend fun isCurrent(schedule: AutomationSchedule): Boolean {
        if (!isRemote(schedule)) {
            return true
        }

        val remoteDataInfo = remoteDataInfo(schedule) ?: return false

        return remoteData.isCurrent(remoteDataInfo)
    }

    override suspend fun requiredUpdate(schedule: AutomationSchedule): Boolean {
        if (!isRemote(schedule)) {
            return false
        }

        val remoteDataInfo = remoteDataInfo(schedule) ?: return true
        if (!remoteData.isCurrent(remoteDataInfo)) {
            return true
        }

        return when(remoteData.status(remoteDataInfo.source)) {
            RemoteData.Status.UP_TO_DATE -> false
            RemoteData.Status.STALE -> false
            RemoteData.Status.OUT_OF_DATE -> true
        }
    }

    override suspend fun waitForFullRefresh(schedule: AutomationSchedule) {
        if (!isRemote(schedule)) {
            return
        }

        val source = remoteDataInfo(schedule)?.source ?: RemoteDataSource.APP
        remoteData.waitForRefresh(source)
    }

    override suspend fun bestEffortRefresh(schedule: AutomationSchedule): Boolean {
        if (!isRemote(schedule)) {
            return true
        }

        val remoteDataInfo = remoteDataInfo(schedule) ?: return false
        if (!remoteData.isCurrent(remoteDataInfo)) {
            return false
        }

        if (remoteData.status(remoteDataInfo.source) == RemoteData.Status.UP_TO_DATE) {
            return true
        }

        if (network.isConnected(context)) {
            remoteData.waitForRefreshAttempt(remoteDataInfo.source)
        }

        return remoteData.isCurrent(remoteDataInfo)
    }

    override suspend fun notifyOutdated(schedule: AutomationSchedule) {
        val info = remoteDataInfo(schedule) ?: return
        remoteData.notifyOutdated(info)
    }

    override fun contactIdFor(schedule: AutomationSchedule): String? {
        return remoteDataInfo(schedule)?.contactId
    }

    override fun sourceFor(schedule: AutomationSchedule): RemoteDataSource? {
        if (!isRemote(schedule)) {
            return null
        }

        return remoteDataInfo(schedule)?.source ?: RemoteDataSource.APP
    }

    private fun isRemote(schedule: AutomationSchedule): Boolean {
        val metadata = schedule.metadata?.optMap() ?: jsonMapOf()
        if (metadata.containsKey(InAppRemoteData.REMOTE_INFO_METADATA_KEY) ||
            metadata.containsKey(InAppRemoteData.LEGACY_REMOTE_INFO_METADATA_KEY)) {
            return true
        }

        // legacy way
        when(schedule.data) {
            is AutomationSchedule.ScheduleData.InAppMessageData -> {
                return schedule.data.message.source == InAppMessage.Source.REMOTE_DATA
            }
            else -> {}
        }

        return false
    }

    private fun remoteDataInfo(schedule: AutomationSchedule): RemoteDataInfo? {
        val metadata = schedule.metadata?.optMap() ?: return null
        val infoJson = metadata.get(InAppRemoteData.REMOTE_INFO_METADATA_KEY) ?: return null
        return try {
            if (infoJson.isString) {
                // 17.x and older
                RemoteDataInfo(JsonValue.parseString(infoJson.string))
            } else {
                RemoteDataInfo(infoJson)
            }
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to parse remote info from schedule $schedule" }
            null
        }
    }

}

internal class InAppRemoteData(
    val payload: Map<RemoteDataSource, Payload>
) {

    data class Data(
        val schedules: List<AutomationSchedule>,
        val constraints: List<FrequencyConstraint>?
    ) {
        companion object {
            private const val SCHEDULES = "in_app_messages"
            private const val CONSTRAINTS = "frequency_constraints"

            @Throws(JsonException::class)
            fun fromJson(value: JsonMap): Data {
                return Data(
                    schedules = value.require(SCHEDULES).requireList().mapNotNull {
                        try {
                            AutomationSchedule.fromJson(it)
                        } catch (ex: Exception) {
                            UALog.e(ex) { "Failed to parse a schedule from $it" }
                            null
                        }
                    },
                    constraints = value.get(CONSTRAINTS)?.requireList()?.map(FrequencyConstraint::fromJson)
                )
            }
        }

        fun copyWithUpdateSchedules(updateBlock: (AutomationSchedule) -> AutomationSchedule): Data {
            return Data(
                schedules = this.schedules.map(updateBlock),
                constraints = this.constraints
            )
        }
    }

    data class Payload(
        val data: Data,
        val timestamp: Long,
        val remoteDataInfo: RemoteDataInfo? = null
    )

    companion object {
        const val LEGACY_REMOTE_INFO_METADATA_KEY = "com.urbanairship.iaa.REMOTE_DATA_METADATA"
        const val REMOTE_INFO_METADATA_KEY = "com.urbanairship.iaa.REMOTE_DATA_INFO";

        fun fromPayloads(payloads: List<RemoteDataPayload>): InAppRemoteData {
            val parsed = mutableMapOf<RemoteDataSource, Payload>()
            payloads.forEach { payload ->
                parsed.put(payload.remoteDataInfo?.source ?: RemoteDataSource.APP, parse(payload))
            }

            return InAppRemoteData(parsed)
        }

        private fun parse(payload: RemoteDataPayload): Payload {
            val metadata = jsonMapOf(
                LEGACY_REMOTE_INFO_METADATA_KEY to "",
                REMOTE_INFO_METADATA_KEY to payload.remoteDataInfo
            ).toJsonValue()

            val data = Data.fromJson(payload.data).copyWithUpdateSchedules { local ->
                val result = local.copyWith(metadata = metadata)

                when(result.data) {
                    is AutomationSchedule.ScheduleData.InAppMessageData ->  {
                        result.data.message.source = InAppMessage.Source.REMOTE_DATA
                    }
                    else -> {}
                }

                result
            }

            return Payload(
                data = data,
                timestamp = payload.timestamp,
                remoteDataInfo = payload.remoteDataInfo
            )
        }
    }
}
