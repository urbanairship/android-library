/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.os.Build
import com.urbanairship.UAirship
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf

internal class AppForegroundEvent(
    timeMilliseconds: Long
) : Event(timeMilliseconds = timeMilliseconds) {

    override val type: EventType = EventType.APP_FOREGROUND

    @Throws(com.urbanairship.json.JsonException::class)
    override fun getEventData(conversionData: ConversionData): JsonMap = jsonMapOf(
        TIME_ZONE_KEY to timezone,
        DAYLIGHT_SAVINGS_KEY to isDaylightSavingsTime,
        OS_VERSION_KEY to Build.VERSION.RELEASE,
        LIB_VERSION_KEY to UAirship.getVersion(),
        PACKAGE_VERSION_KEY to UAirship.applicationContext.packageManager
            .getPackageInfo(UAirship.applicationContext.packageName, 0)?.versionName,
        PUSH_ID_KEY to conversionData.conversionSendId,
        METADATA_KEY to conversionData.conversionMetadata,
        LAST_METADATA_KEY to conversionData.lastReceivedMetadata
    )
}
