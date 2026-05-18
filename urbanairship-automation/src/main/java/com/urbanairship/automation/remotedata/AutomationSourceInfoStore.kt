/* Copyright Airship and Contributors */

package com.urbanairship.automation.remotedata

import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.preferences.SyncPrefKey
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource

internal data class AutomationSourceInfo(
    val remoteDataInfo: RemoteDataInfo?,
    val payloadTimestamp: Long,
    val airshipSDKVersion: String?
) : JsonSerializable {
    companion object {
        private const val REMOTE_DATA_INFO = "remoteDataInfo"
        private const val PAYLOAD_TIMESTAMP = "payloadTimestamp"
        private const val AIRSHIP_SDK_VERSION = "airshipSDKVersion"

        fun fromJson(value: JsonValue): AutomationSourceInfo? {
            return try {
                val content = value.requireMap()
                AutomationSourceInfo(
                    remoteDataInfo = content[REMOTE_DATA_INFO]?.let { RemoteDataInfo(it) },
                    payloadTimestamp = content.requireField(PAYLOAD_TIMESTAMP),
                    airshipSDKVersion = content[AIRSHIP_SDK_VERSION]?.requireString()
                )
            } catch (_: JsonException) {
                null
            }
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        REMOTE_DATA_INFO to remoteDataInfo,
        PAYLOAD_TIMESTAMP to payloadTimestamp,
        AIRSHIP_SDK_VERSION to airshipSDKVersion
    ).toJsonValue()
}

/** Stores information about a remote-data source used for scheduling. */
internal class AutomationSourceInfoStore(
    private val dataStore: PreferenceStore
) {

    fun getSourceInfo(source: RemoteDataSource, contactID: String?): AutomationSourceInfo? {
        return dataStore.get(infoKey(source, contactID)) ?: recoverSource(source, contactID)
    }

    fun setSourceInfo(info: AutomationSourceInfo, source: RemoteDataSource, contactID: String?) {
        dataStore.put(infoKey(source, contactID), info)
    }

    private fun infoKey(source: RemoteDataSource, contactID: String?): SyncPrefKey<AutomationSourceInfo> {
        val name = when (source) {
            RemoteDataSource.CONTACT -> "$SOURCE_INFO_KEY_PREFIX.$source.${contactID ?: ""}"
            else -> "$SOURCE_INFO_KEY_PREFIX.$source"
        }
        return SyncPrefKey.jsonSerializable(
            name = name,
            fromJson = { AutomationSourceInfo.fromJson(it) ?: throw JsonException("Failed to parse AutomationSourceInfo") }
        )
    }

    private fun recoverSource(source: RemoteDataSource, contactID: String?): AutomationSourceInfo? {
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

    private fun recoverStore(
        key: SyncPrefKey<AutomationSourceInfo>,
        legacyTimestampKey: SyncPrefKey<Long>,
        legacySdkVersionKey: SyncPrefKey<String>
    ): AutomationSourceInfo? {
        val lastSDKVersion = dataStore.get(legacySdkVersionKey)
        val lastUpdate: Long = dataStore.get(legacyTimestampKey) ?: -1L

        if (lastSDKVersion == null || lastUpdate == -1L) {
            return null
        }

        val store = AutomationSourceInfo(
            remoteDataInfo = null,
            payloadTimestamp = lastUpdate,
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
        private val LEGACY_APP_LAST_PAYLOAD_TIMESTAMP_KEY = SyncPrefKey.long("com.urbanairship.iam.data.LAST_PAYLOAD_TIMESTAMP")
        private val LEGACY_APP_LAST_SDK_VERSION_KEY = SyncPrefKey.string("com.urbanairship.iaa.last_sdk_version")
        private val LEGACY_CONTACT_LAST_PAYLOAD_TIMESTAMP_KEY = SyncPrefKey.long("com.urbanairship.iam.data.contact_last_payload_timestamp")
        private val LEGACY_CONTACT_LAST_SDK_VERSION_KEY = SyncPrefKey.string("com.urbanairship.iaa.contact_last_sdk_version")
    }
}
