/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.content.Context
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import java.time.Instant

internal class AppBackgroundEvent(
    timestamp: Instant
) : Event(timestamp = timestamp) {

    override val type: EventType = EventType.APP_BACKGROUND

    @Throws(com.urbanairship.json.JsonException::class)
    override fun getEventData(context: Context, conversionData: ConversionData): JsonMap = jsonMapOf(
        PUSH_ID_KEY to conversionData.conversionSendId,
        METADATA_KEY to conversionData.conversionMetadata
    )
}
