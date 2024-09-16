/* Copyright Airship and Contributors */

package com.urbanairship.embedded

import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonMap
import com.urbanairship.json.emptyJsonMap

/**
 * Information about a pending embedded view.
 *
 * @param instanceId The instance ID (a unique identifier for an embedded layout)
 * @param embeddedId The embedded ID of the targeted embedded view
 * @param priority The priority. Lower value is higher priority.
 * @param extras A [JsonMap] containing any extras that were included with the embedded layout
 */
public class AirshipEmbeddedInfo(
    public val instanceId: String,
    public val embeddedId: String,
    public val priority: Int = 0,
    public val extras: JsonMap = emptyJsonMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AirshipEmbeddedInfo

        if (instanceId != other.instanceId) return false
        if (embeddedId != other.embeddedId) return false
        if (extras != other.extras) return false
        if (priority != other.priority) return false

        return true
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(instanceId, embeddedId, extras, priority)
    }

    override fun toString(): String {
        return "AirshipEmbeddedInfo(instanceId='$instanceId', embeddedId='$embeddedId', priority=$priority, extras=$extras)"
    }
}
