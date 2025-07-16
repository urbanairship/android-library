/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf

internal class AppBackgroundEvent(
    timeMilliseconds: Long
) : Event(timeMilliseconds = timeMilliseconds) {

    override val type: EventType = EventType.APP_BACKGROUND

    @Throws(com.urbanairship.json.JsonException::class)
    override fun getEventData(conversionData: ConversionData): JsonMap = jsonMapOf(
        CONNECTION_TYPE_KEY to connectionType,
        CONNECTION_SUBTYPE_KEY to connectionSubType,
        PUSH_ID_KEY to conversionData.conversionSendId,
        METADATA_KEY to conversionData.conversionMetadata
    )
}
