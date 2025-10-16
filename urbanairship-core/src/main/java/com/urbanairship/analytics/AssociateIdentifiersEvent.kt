/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Event to set the associated identifiers.
 */
internal class AssociateIdentifiersEvent(
    ids: AssociatedIdentifiers
) : Event() {

    private val identifiers: Map<String, String> = ids.ids
    override val type: EventType = EventType.ASSOCIATE_IDENTIFIERS

    override fun isValid(): Boolean {
        if (identifiers.size > AssociatedIdentifiers.MAX_IDS) {
            UALog.e("Associated identifiers exceeds ${AssociatedIdentifiers.MAX_IDS}")
            return false
        }

        for ((key, value) in identifiers) {
            if (key.length > AssociatedIdentifiers.MAX_CHARACTER_COUNT) {
                UALog.e("Associated identifiers key $key exceeds ${AssociatedIdentifiers.MAX_CHARACTER_COUNT} characters")
                return false
            }
            if (value.length > AssociatedIdentifiers.MAX_CHARACTER_COUNT) {
                UALog.e("Associated identifiers value for key $key exceeds ${AssociatedIdentifiers.MAX_CHARACTER_COUNT} characters")
                return false
            }
        }

        return true
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getEventData(context: Context, conversionData: ConversionData): JsonMap = JsonValue.wrapOpt(identifiers).optMap()
}
