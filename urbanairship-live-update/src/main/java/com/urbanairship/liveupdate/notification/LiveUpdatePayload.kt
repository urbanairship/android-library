/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.notification

import com.urbanairship.UALog
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.liveupdate.LiveUpdateEvent
import com.urbanairship.liveupdate.util.optionalField
import com.urbanairship.liveupdate.util.requireField

/**
 * Live Update push payload.
 *
 * Contains metadata and `content` for a Live Update push.
 * @hide
 */
internal data class LiveUpdatePayload(
    /** Unique name for the Live Update. */
    val name: String,
    /** Live Update event type. */
    val event: LiveUpdateEvent,
    /** Live Update type. */
    val type: String?,
    /** Scheduled dismiss date, in ms. */
    val dismissalDate: Long?,
    /** The timestamp for this update, in ms. */
    val timestamp: Long,
    /** Live Update content. */
    val content: JsonMap
) {
    internal companion object {
        internal fun fromJson(json: String): LiveUpdatePayload? =
            try {
                JsonValue.parseString(json).map?.let { fromJson(it) }
            } catch (e: Exception) {
                UALog.w(e, "Failed to parse live update payload: $json")
                null
            }

        private fun fromJson(json: JsonMap): LiveUpdatePayload {
            val content = json.opt("content_state").let { contentState ->
                when {
                    // If the content state is a map, use it directly.
                    contentState.isJsonMap -> contentState.optMap()
                    // Otherwise, try parsing as a json string.
                    contentState.isString -> JsonValue.parseString(contentState.string).optMap()
                    // Invalid content.
                    else -> {
                        UALog.w("Invalid Live Update content_state: '$contentState'")
                        JsonMap.EMPTY_MAP
                    }
                }
            }

            return LiveUpdatePayload(
                name = json.requireField("name"),
                event = json.requireField<String>("event").let { LiveUpdateEvent.from(it) },
                type = json.optionalField<String>("type"),
                dismissalDate = json.optionalField<Long?>("dismissal_date")?.let { it * 1000 },
                timestamp = json.requireField<Long>("timestamp") * 1000,
                content = content
            )
        }
    }
}
