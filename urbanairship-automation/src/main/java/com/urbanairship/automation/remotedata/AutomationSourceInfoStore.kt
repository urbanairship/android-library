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
                    remoteDataInfo = content.get(REMOTE_DATA_INFO)?.let { RemoteDataInfo(it) },
                    payloadTimestamp = content.requireField(PAYLOAD_TIMESTAMP),
                    airshipSDKVersion = content.get(AIRSHIP_SDK_VERSION)?.requireString()
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
        return AutomationSourceInfo.fromJson(dataStore.getJsonValue(key))
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

    companion object {
        private const val SOURCE_INFO_KEY_PREFIX = "AutomationSourceInfo"
    }
}
