/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.os.Parcelable
import androidx.annotation.RestrictTo
import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.actions.ActionResult.Companion.newEmptyResult
import com.urbanairship.actions.ActionResult.Companion.newErrorResult
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.CustomEvent.Companion.newBuilder
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.push.PushMessage

/**
 * An action that adds a custom event.
 *
 *
 * Accepted situations: all
 *
 *
 * Accepted argument value - A map of fields for the custom event:
 *
 *  * [com.urbanairship.analytics.CustomEvent.EVENT_NAME]: String, Required
 *  * [com.urbanairship.analytics.CustomEvent.EVENT_VALUE]: number as a String or Number
 *  * [com.urbanairship.analytics.CustomEvent.TRANSACTION_ID]: String
 *  * [com.urbanairship.analytics.CustomEvent.INTERACTION_ID]: String
 *  * [com.urbanairship.analytics.CustomEvent.INTERACTION_TYPE]: String
 *  * [com.urbanairship.analytics.CustomEvent.PROPERTIES]: JsonMap of Strings, Booleans, Numbers, or arrays of Strings
 *
 * When a custom event action is triggered from a Message Center Rich Push Message, the interaction type
 * and ID will automatically be filled for the message if they are left blank.
 *
 *
 * Result value: `null`
 *
 *
 * Default Registration Name: [DEFAULT_NAMES]
 *
 *
 * Default Registration Predicate: Rejects [Action.Situation.PUSH_RECEIVED]
 */
public class AddCustomEventAction(
    private val eventRecord: (CustomEvent) -> Unit = {
        Airship.analytics.recordCustomEvent(it)
    }
) : Action() {

    override fun perform(arguments: ActionArguments): ActionResult {
        val customEventMap = arguments.value.toJsonValue().optMap()

        // Parse the event values from the map
        val eventName = getEventName(customEventMap)
        requireNotNull(eventName) { "Missing event name" }

        val eventDoubleValue: Double = getEventValue(customEventMap) ?: 0.0

        val interactionType = customEventMap.optionalField<String>(CustomEvent.INTERACTION_TYPE)
        val interactionId = customEventMap.optionalField<String>(CustomEvent.INTERACTION_ID)

        val eventBuilder = newBuilder(eventName)
            .setTransactionId(customEventMap.optionalField(CustomEvent.TRANSACTION_ID))
            .setAttribution(arguments.metadata.getParcelable<Parcelable>(ActionArguments.PUSH_MESSAGE_METADATA) as PushMessage?)
            .setInteraction(interactionType, interactionId)

        eventBuilder.setEventValue(eventDoubleValue)

        arguments.metadata.getString(IN_APP_CONTEXT_METADATA_KEY)?.let { inApp ->
            try {
                eventBuilder.setInAppContext(JsonValue.parseString(inApp))
            } catch (e: Exception) {
                UALog.w("Failed to parse in-app context for custom event", e)
            }
        }

        // Try to fill in the interaction if its not set
        if (interactionId == null && interactionType == null) {
            arguments.metadata.getString(ActionArguments.RICH_PUSH_ID_METADATA)?.let {
                eventBuilder.setMessageCenterInteraction(it)
            }
        }

        eventBuilder.setProperties(customEventMap.optionalMap(CustomEvent.PROPERTIES))

        val event = eventBuilder.build()

        return if (event.isValid()) {
            eventRecord(event)
            newEmptyResult()
        } else {
            newErrorResult(IllegalArgumentException("Unable to add custom event. Event is invalid."))
        }
    }

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        val map = arguments.value.map ?: run {
            UALog.e("CustomEventAction requires a map of event data.")
            return false
        }

        if (!map.containsKey(KEY_NAME) && !map.containsKey(CustomEvent.EVENT_NAME)) {
            UALog.e("CustomEventAction requires an event name in the event data.")
            return false
        }

        return true
    }

    private fun getEventName(map: JsonMap): String? {
        return map.optionalField(KEY_NAME)
            ?: map.optionalField(CustomEvent.EVENT_NAME)
    }

    private fun getEventValue(map: JsonMap): Double? {
        return map.optionalField<String>(KEY_VALUE)?.toDoubleOrNull()
            ?: map.optionalField<Double>(KEY_VALUE)
            ?: map.optionalField<Double>(CustomEvent.EVENT_VALUE)
    }

    /**
     * Default [AddCustomEventAction] predicate.
     */
    public class AddCustomEventActionPredicate public constructor() : ActionPredicate {

        override fun apply(arguments: ActionArguments): Boolean {
            return Situation.PUSH_RECEIVED != arguments.situation
        }
    }

    public companion object {

        internal const val KEY_NAME: String = "name"
        internal const val KEY_VALUE: String = "value"

        /**
         * Default action names.
         */
        public val DEFAULT_NAMES: Set<String> = setOf("add_custom_event_action", "^+e")

        /**
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val IN_APP_CONTEXT_METADATA_KEY: String = "in_app_metadata"
    }
}
