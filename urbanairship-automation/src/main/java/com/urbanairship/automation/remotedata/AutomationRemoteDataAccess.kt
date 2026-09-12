/* Copyright Airship and Contributors */

package com.urbanairship.automation.remotedata

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.json.JsonException
import com.urbanairship.automation.InAppAutomationRemoteDataStatus
import com.urbanairship.automation.limits.FrequencyConstraint
import com.urbanairship.iam.InAppMessage
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataPayload
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.DateUtils
import com.urbanairship.util.Network
import java.time.Instant
import kotlin.collections.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Remote data access for automation
 */
internal interface AutomationRemoteDataAccessInterface {

    val status: InAppAutomationRemoteDataStatus
    val statusUpdates: Flow<InAppAutomationRemoteDataStatus>

    val updatesFlow: Flow<InAppRemoteData>
    fun isCurrent(schedule: AutomationSchedule): Boolean
    fun requiredUpdate(schedule: AutomationSchedule): Boolean
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
        .map { payloads ->
            // fromPayloads isolates failures per payload. This only guards against an unexpected
            // throw escaping it, which would otherwise cancel the subscriber's collection and
            // silently disable automation sync for the rest of the session.
            try {
                InAppRemoteData.fromPayloads(payloads)
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to parse in-app remote data payloads" }
                InAppRemoteData(emptyMap())
            }
        }

    override val status: InAppAutomationRemoteDataStatus
        get() {
            return RemoteDataSource.entries
                .map(remoteData::status)
                .map { it.toInAppDataSource() }
                .let(InAppAutomationRemoteDataStatus::reduce)
        }

    override val statusUpdates: Flow<InAppAutomationRemoteDataStatus>
        get() {
            return RemoteDataSource.entries
                .mapNotNull(remoteData::statusFlow)
                .map { flow -> flow.map { it.toInAppDataSource() } }
                .let { flows -> combine(flows) { it.toList() } }
                .map(InAppAutomationRemoteDataStatus::reduce)
        }

    override fun isCurrent(schedule: AutomationSchedule): Boolean {
        if (!isRemote(schedule)) {
            return true
        }

        val remoteDataInfo = remoteDataInfo(schedule) ?: return false

        return remoteData.isCurrent(remoteDataInfo)
    }

    override fun requiredUpdate(schedule: AutomationSchedule): Boolean {
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
        val infoJson = metadata[InAppRemoteData.REMOTE_INFO_METADATA_KEY] ?: return null
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

internal data class InAppRemoteData(
    val payload: Map<RemoteDataSource, Payload>
) {
    data class Data(
        val schedules: List<AutomationSchedule>,
        val constraints: List<FrequencyConstraint>?,
        val failedSchedules: List<FailedScheduleRecord> = emptyList()
    ) {
        companion object {
            private const val SCHEDULES = "in_app_messages"
            private const val CONSTRAINTS = "frequency_constraints"
            private const val IDENTIFIER = "id"
            private const val CREATED = "created"
            private const val MIN_SDK_VERSION = "min_sdk_version"

            @Throws(JsonException::class)
            fun fromJson(value: JsonMap, payloadTimestamp: Long): Data {
                val schedules = mutableListOf<AutomationSchedule>()
                val failedSchedules = mutableListOf<FailedScheduleRecord>()

                value.require(SCHEDULES).requireList().forEach {
                    try {
                        schedules.add(AutomationSchedule.fromJson(it))
                    } catch (ex: Exception) {
                        UALog.e(ex) { "Failed to parse a schedule from $it" }
                        partialSchedule(it, payloadTimestamp)?.let(failedSchedules::add)
                    }
                }

                return Data(
                    schedules = schedules,
                    constraints = value[CONSTRAINTS]?.requireList()?.map(FrequencyConstraint::fromJson),
                    failedSchedules = failedSchedules
                )
            }

            /**
             * Best-effort extraction of just enough of a schedule to track it after a parse
             * failure. Returns null if we can't even recover an identifier, in which case the
             * schedule is untrackable and stays dropped.
             */
            private fun partialSchedule(value: JsonValue, payloadTimestamp: Long): FailedScheduleRecord? {
                return try {
                    val content = value.requireMap()
                    val created = content[CREATED]?.string?.let {
                        try {
                            DateUtils.parseIso8601(it).toEpochMilli()
                        } catch (ex: Exception) {
                            null
                        }
                    } ?: payloadTimestamp

                    FailedScheduleRecord(
                        identifier = content.require(IDENTIFIER).requireString(),
                        createdDate = created,
                        // A malformed min SDK version only costs us the retry hint. Dropping the
                        // whole record over it would make the schedule untrackable, and the next
                        // sync would read its absence as a recovery and forget it entirely.
                        minSDKVersion = content[MIN_SDK_VERSION]?.string
                    )
                } catch (ex: Exception) {
                    UALog.e(ex) { "Failed to parse a partial schedule from $value" }
                    null
                }
            }
        }

        fun copyWithUpdateSchedules(updateBlock: (AutomationSchedule) -> AutomationSchedule): Data {
            return Data(
                schedules = this.schedules.map(updateBlock),
                constraints = this.constraints,
                failedSchedules = this.failedSchedules
            )
        }
    }

    data class Payload(
        val data: Data,
        val timestamp: Instant,
        val remoteDataInfo: RemoteDataInfo? = null
    )

    /** Every schedule that failed to parse across all payloads. */
    val failedSchedules: List<FailedScheduleRecord>
        get() = payload.values.flatMap { it.data.failedSchedules }

    companion object {
        const val LEGACY_REMOTE_INFO_METADATA_KEY = "com.urbanairship.iaa.REMOTE_DATA_METADATA"
        const val REMOTE_INFO_METADATA_KEY = "com.urbanairship.iaa.REMOTE_DATA_INFO"

        fun fromPayloads(payloads: List<RemoteDataPayload>): InAppRemoteData {
            val parsed = mutableMapOf<RemoteDataSource, Payload>()
            payloads.forEach { payload ->
                val source = payload.remoteDataInfo?.source ?: RemoteDataSource.APP
                // A payload we can't parse is left out so it reads as "no payload" for its own
                // source, rather than taking down the other sources with it.
                parse(payload)?.let { parsed[source] = it }
            }

            return InAppRemoteData(parsed)
        }

        private fun parse(payload: RemoteDataPayload): Payload? {
            return try {
                parsePayload(payload)
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to parse in-app remote data payload $payload" }
                null
            }
        }

        @Throws(JsonException::class)
        private fun parsePayload(payload: RemoteDataPayload): Payload {
            val metadata = jsonMapOf(
                LEGACY_REMOTE_INFO_METADATA_KEY to "",
                REMOTE_INFO_METADATA_KEY to payload.remoteDataInfo
            ).toJsonValue()

            val data = Data.fromJson(payload.data, payload.timestamp.toEpochMilli()).copyWithUpdateSchedules { local ->
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

private fun RemoteData.Status.toInAppDataSource(): InAppAutomationRemoteDataStatus {
    return when(this) {
        RemoteData.Status.UP_TO_DATE -> InAppAutomationRemoteDataStatus.UP_TO_DATE
        RemoteData.Status.STALE -> InAppAutomationRemoteDataStatus.STALE
        RemoteData.Status.OUT_OF_DATE -> InAppAutomationRemoteDataStatus.OUT_OF_DATE
    }
}
