/* Copyright Airship and Contributors */
package com.urbanairship.remoteconfig

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField

/**
 * Remote Airship config from remote-data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class RemoteAirshipConfig @JvmOverloads internal constructor(
    public val remoteDataUrl: String? = null,
    public val deviceApiUrl: String? = null,
    public val walletUrl: String? = null,
    public val analyticsUrl: String? = null
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        REMOTE_DATA_URL_KEY to remoteDataUrl,
        DEVICE_API_URL_KEY to deviceApiUrl,
        ANALYTICS_URL_KEY to analyticsUrl,
        WALLET_URL_KEY to walletUrl,
    ).toJsonValue()

    public constructor(jsonValue: JsonValue) : this(
        remoteDataUrl = jsonValue.optMap().optionalField(REMOTE_DATA_URL_KEY),
        deviceApiUrl = jsonValue.optMap().optionalField(DEVICE_API_URL_KEY),
        walletUrl = jsonValue.optMap().optionalField(WALLET_URL_KEY),
        analyticsUrl = jsonValue.optMap().optionalField(ANALYTICS_URL_KEY),
    )

    public companion object {

        private const val REMOTE_DATA_URL_KEY = "remote_data_url"
        private const val DEVICE_API_URL_KEY = "device_api_url"
        private const val WALLET_URL_KEY = "wallet_url"
        private const val ANALYTICS_URL_KEY = "analytics_url"

        @JvmStatic
        public fun fromJson(jsonValue: JsonValue): RemoteAirshipConfig {
            return RemoteAirshipConfig(jsonValue)
        }
    }
}
