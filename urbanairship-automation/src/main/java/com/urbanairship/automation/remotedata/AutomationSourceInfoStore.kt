/* Copyright Airship and Contributors */

package com.urbanairship.automation.remotedata

import com.urbanairship.preferences.AsyncPrefKey
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireEpochMillis
import com.urbanairship.json.requireField
import com.urbanairship.json.toJsonList
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource
import java.time.Instant

/**
 * A schedule that failed to parse, with just enough info to evaluate newness when we retry it on
 * a later sync.
 */
internal data class FailedScheduleRecord(
    val identifier: String,
    val createdDate: Long,
    val minSDKVersion: String?
) : JsonSerializable {
    companion object {
        private const val IDENTIFIER = "identifier"
        private const val CREATED_DATE = "createdDate"
        private const val MIN_SDK_VERSION = "minSDKVersion"

        fun fromJson(value: JsonValue): FailedScheduleRecord? {
            return try {
                val content = value.requireMap()
                FailedScheduleRecord(
                    identifier = content.require(IDENTIFIER).requireString(),
                    createdDate = content.requireField(CREATED_DATE),
                    minSDKVersion = content[MIN_SDK_VERSION]?.requireString()
                )
            } catch (_: JsonException) {
                null
            }
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        IDENTIFIER to identifier,
        CREATED_DATE to createdDate,
        MIN_SDK_VERSION to minSDKVersion
    ).toJsonValue()
}

internal data class AutomationSourceInfo(
    val remoteDataInfo: RemoteDataInfo?,
    val payloadTimestamp: Instant,
    val airshipSDKVersion: String?,
    /**
     * Schedules that failed to parse, carried forward across syncs until they either parse
     * successfully or are removed from remote data. Null when nothing is failing.
     */
    val failedSchedules: List<FailedScheduleRecord>? = null
) : JsonSerializable {
    companion object {
        private const val REMOTE_DATA_INFO = "remoteDataInfo"
        private const val PAYLOAD_TIMESTAMP = "payloadTimestamp"
        private const val AIRSHIP_SDK_VERSION = "airshipSDKVersion"
        private const val FAILED_SCHEDULES = "failedSchedules"

        fun fromJson(value: JsonValue): AutomationSourceInfo? {
            return try {
                val content = value.requireMap()
                AutomationSourceInfo(
                    remoteDataInfo = content[REMOTE_DATA_INFO]?.let { RemoteDataInfo(it) },
                    payloadTimestamp = content.requireEpochMillis(PAYLOAD_TIMESTAMP),
                    airshipSDKVersion = content[AIRSHIP_SDK_VERSION]?.requireString(),
                    // Parsed leniently so a single bad record can't discard the whole checkpoint.
                    failedSchedules = content[FAILED_SCHEDULES]
                        ?.optList()
                        ?.mapNotNull(FailedScheduleRecord::fromJson)
                        ?.ifEmpty { null }
                )
            } catch (_: JsonException) {
                null
            }
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        REMOTE_DATA_INFO to remoteDataInfo,
        PAYLOAD_TIMESTAMP to payloadTimestamp.toEpochMilli(),
        AIRSHIP_SDK_VERSION to airshipSDKVersion,
        FAILED_SCHEDULES to failedSchedules?.toJsonList()
    ).toJsonValue()
}

/** Stores information about a remote-data source used for scheduling. */
internal class AutomationSourceInfoStore(
    private val dataStore: PreferenceStore
) {

    suspend fun getSourceInfo(source: RemoteDataSource, contactID: String?): AutomationSourceInfo? {
        return dataStore.get(infoKey(source, contactID)) ?: recoverSource(source, contactID)
    }

    suspend fun setSourceInfo(info: AutomationSourceInfo, source: RemoteDataSource, contactID: String?) {
        dataStore.put(infoKey(source, contactID), info)
    }

    private fun infoKey(source: RemoteDataSource, contactID: String?): AsyncPrefKey<AutomationSourceInfo> {
        val name = when (source) {
            RemoteDataSource.CONTACT -> "$SOURCE_INFO_KEY_PREFIX.$source.${contactID ?: ""}"
            else -> "$SOURCE_INFO_KEY_PREFIX.$source"
        }
        return AsyncPrefKey.jsonSerializable(
            name = name,
            fromJson = { AutomationSourceInfo.fromJson(it) ?: throw JsonException("Failed to parse AutomationSourceInfo") }
        )
    }

    private suspend fun recoverSource(source: RemoteDataSource, contactID: String?): AutomationSourceInfo? {
        return when (source) {
            RemoteDataSource.APP -> {
                recoverStore(
                    key = infoKey(source, contactID),
                    legacyTimestampKey = LEGACY_APP_LAST_PAYLOAD_TIMESTAMP_KEY,
                    legacySdkVersionKey = LEGACY_APP_LAST_SDK_VERSION_KEY
                )
            }

            RemoteDataSource.CONTACT -> {
                recoverStore(
                    key = infoKey(source, contactID),
                    legacyTimestampKey = LEGACY_CONTACT_LAST_PAYLOAD_TIMESTAMP_KEY,
                    legacySdkVersionKey = LEGACY_CONTACT_LAST_SDK_VERSION_KEY
                )
            }
        }
    }

    private suspend fun recoverStore(
        key: AsyncPrefKey<AutomationSourceInfo>,
        legacyTimestampKey: AsyncPrefKey<Long>,
        legacySdkVersionKey: AsyncPrefKey<String>
    ): AutomationSourceInfo? {
        val lastSDKVersion = dataStore.get(legacySdkVersionKey)
        val lastUpdate: Long = dataStore.get(legacyTimestampKey) ?: -1L

        if (lastSDKVersion == null || lastUpdate == -1L) {
            return null
        }

        val store = AutomationSourceInfo(
            remoteDataInfo = null,
            payloadTimestamp = Instant.ofEpochMilli(lastUpdate),
            airshipSDKVersion = lastSDKVersion
        )

        dataStore.put(key, store)
        dataStore.remove(legacyTimestampKey)
        dataStore.remove(legacySdkVersionKey)
        return store
    }

    companion object {
        private const val SOURCE_INFO_KEY_PREFIX = "AutomationSourceInfo"

        // Legacy store keys
        private val LEGACY_APP_LAST_PAYLOAD_TIMESTAMP_KEY = AsyncPrefKey.long("com.urbanairship.iam.data.LAST_PAYLOAD_TIMESTAMP")
        private val LEGACY_APP_LAST_SDK_VERSION_KEY = AsyncPrefKey.string("com.urbanairship.iaa.last_sdk_version")
        private val LEGACY_CONTACT_LAST_PAYLOAD_TIMESTAMP_KEY = AsyncPrefKey.long("com.urbanairship.iam.data.contact_last_payload_timestamp")
        private val LEGACY_CONTACT_LAST_SDK_VERSION_KEY = AsyncPrefKey.string("com.urbanairship.iaa.contact_last_sdk_version")
    }
}
