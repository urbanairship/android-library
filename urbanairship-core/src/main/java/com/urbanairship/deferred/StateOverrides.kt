package com.urbanairship.deferred

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Locale

internal data class StateOverrides(
    val appVersionName: String,
    val sdkVersion: String,
    val notificationOptIn: Boolean,
    val locale: Locale?
) : JsonSerializable {
    companion object {
        private const val KEY_APP_VERSION = "app_version"
        private const val KEY_SDK_VERSION = "sdk_version"
        private const val KEY_NOTIFICATION_OPT_IN = "notification_opt_in"
        private const val KEY_LOCALE_LANGUAGE = "locale_language"
        private const val KEY_LOCALE_COUNTRY = "locale_country"
    }

    constructor(request: DeferredRequest) : this(
        appVersionName = request.appVersionName,
        sdkVersion = request.sdkVersion,
        notificationOptIn = request.notificationOptIn,
        locale = request.locale
    )

    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            KEY_APP_VERSION to appVersionName,
            KEY_SDK_VERSION to sdkVersion,
            KEY_NOTIFICATION_OPT_IN to notificationOptIn,
            KEY_LOCALE_COUNTRY to locale?.country,
            KEY_LOCALE_LANGUAGE to locale?.language
        ).toJsonValue()
    }
}
