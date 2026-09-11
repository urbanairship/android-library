/* Copyright Airship and Contributors */

package com.urbanairship.embedded

import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import com.urbanairship.ai.EvaluationContext
import com.urbanairship.json.JsonMap
import com.urbanairship.json.emptyJsonMap

/**
 * Information about a pending embedded view.
 *
 * @param instanceId The instance ID (a unique identifier for an embedded layout)
 * @param embeddedId The embedded ID of the targeted embedded view
 * @param priority The priority. Lower value is higher priority.
 * @param extras A [JsonMap] containing any extras that were included with the embedded layout
 * @param contentDescription What this content is about, from the layout's `content_description`.
 * Null when the layout doesn't describe itself.
 */
public class AirshipEmbeddedInfo @JvmOverloads public constructor(
    public val instanceId: String,
    public val embeddedId: String,
    public val priority: Int = 0,
    public val extras: JsonMap = emptyJsonMap(),
    public val contentDescription: String? = null,
    /**
     * Layout-authored user context, from `content_description.additional_context`.
     *
     * Pooled across the pending candidates and merged with the app provider's context when a
     * model chooses between them, so these are facts about the *user*, not about this
     * instance — [contentDescription] is what describes the instance.
     *
     * Restricted rather than app-facing: it exists to feed a model, not to be read by apps.
     *
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val additionalContext: List<EvaluationContext.Item> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AirshipEmbeddedInfo

        if (instanceId != other.instanceId) return false
        if (embeddedId != other.embeddedId) return false
        if (extras != other.extras) return false
        if (priority != other.priority) return false
        if (contentDescription != other.contentDescription) return false
        if (additionalContext != other.additionalContext) return false

        return true
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(
            instanceId, embeddedId, extras, priority, contentDescription, additionalContext
        )
    }

    override fun toString(): String {
        return "AirshipEmbeddedInfo(instanceId='$instanceId', embeddedId='$embeddedId', " +
                "priority=$priority, extras=$extras, contentDescription=$contentDescription)"
    }
}
