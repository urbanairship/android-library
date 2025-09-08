/* Copyright Airship and Contributors */

package com.urbanairship.automation.remotedata

import com.urbanairship.PreferenceDataStore
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
    private val dataStore: PreferenceDataStore
) {

    fun getSourceInfo(source: RemoteDataSource, contactID: String?): AutomationSourceInfo? {
        val key = makeInfoKey(source, contactID)
        val json = dataStore.getJsonValue(key)
        return if (json.isNull) {
            recoverSource(source, contactID)
        } else {
            AutomationSourceInfo.fromJson(json)
        }
    }

    fun setSourceInfo(info: AutomationSourceInfo, source: RemoteDataSource, contactID: String?) {
        val key = makeInfoKey(source, contactID)
        dataStore.put(key, info)
    }

    private fun makeInfoKey(source: RemoteDataSource, contactID: String?): String {
        return when(source) {
            RemoteDataSource.CONTACT -> "$SOURCE_INFO_KEY_PREFIX.$source.${contactID ?: ""}"
            else -> "$SOURCE_INFO_KEY_PREFIX.$source"
        }
    }

    private fun recoverSource(source: RemoteDataSource, contactID: String?): AutomationSourceInfo? {
        val key = makeInfoKey(source, contactID)

        return when (source) {
            RemoteDataSource.APP -> {
                recoverStore(
                    key = key,
                    legacyTimestampKey = LEGACY_APP_LAST_PAYLOAD_TIMESTAMP_KEY,
                    legacySdkVersionKey = LEGACY_APP_LAST_SDK_VERSION_KEY
                )
            }

            RemoteDataSource.CONTACT -> {
                recoverStore(
                    key = key,
                    legacyTimestampKey = LEGACY_CONTACT_LAST_PAYLOAD_TIMESTAMP_KEY,
                    legacySdkVersionKey = LEGACY_CONTACT_LAST_SDK_VERSION_KEY
                )
            }
        }
    }

    private fun recoverStore(
        key: String,
        legacyTimestampKey: String,
        legacySdkVersionKey: String
    ): AutomationSourceInfo? {
        val lastSDKVersion = dataStore.getString(legacySdkVersionKey, null)
        val lastUpdate: Long = dataStore.getLong(legacyTimestampKey, -1L)

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
        private const val LEGACY_APP_LAST_PAYLOAD_TIMESTAMP_KEY = "com.urbanairship.iam.data.LAST_PAYLOAD_TIMESTAMP"
        private const val LEGACY_APP_LAST_SDK_VERSION_KEY = "com.urbanairship.iaa.last_sdk_version"
        private const val LEGACY_CONTACT_LAST_PAYLOAD_TIMESTAMP_KEY = "com.urbanairship.iam.data.contact_last_payload_timestamp"
        private const val LEGACY_CONTACT_LAST_SDK_VERSION_KEY =  "com.urbanairship.iaa.contact_last_sdk_version"
    }
}
