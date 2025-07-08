/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.os.Parcelable
import com.urbanairship.UALog
import com.urbanairship.actions.ActionResult.Companion.newEmptyResult
import com.urbanairship.actions.ActionResult.Companion.newErrorResult
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.CustomEvent.Companion.newBuilder
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.push.PushMessage
import com.urbanairship.util.Checks

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
 * Default Registration Name: [DEFAULT_REGISTRY_NAME]
 *
 *
 * Default Registration Predicate: Rejects [Action.Situation.PUSH_RECEIVED]
 */
public class AddCustomEventAction public constructor() : Action() {

    override fun perform(arguments: ActionArguments): ActionResult {
        val customEventMap = arguments.value.toJsonValue().optMap()

        // Parse the event values from the map
        val eventName = customEventMap.optionalField<String>(CustomEvent.EVENT_NAME)
        requireNotNull(eventName) { "Missing event name" }

        val eventStringValue = customEventMap.optionalField<String>(CustomEvent.EVENT_VALUE)
        val eventDoubleValue = customEventMap.optionalField<Double>(CustomEvent.EVENT_VALUE) ?: 0.0

        val interactionType = customEventMap.optionalField<String>(CustomEvent.INTERACTION_TYPE)
        val interactionId = customEventMap.optionalField<String>(CustomEvent.INTERACTION_ID)

        val eventBuilder = newBuilder(eventName)
            .setTransactionId(customEventMap.optionalField(CustomEvent.TRANSACTION_ID))
            .setAttribution(arguments.metadata.getParcelable<Parcelable>(ActionArguments.PUSH_MESSAGE_METADATA) as PushMessage?)
            .setInteraction(interactionType, interactionId)

        if (eventStringValue != null) {
            eventBuilder.setEventValue(eventStringValue)
        } else {
            eventBuilder.setEventValue(eventDoubleValue)
        }

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
        event.track()

        return if (event.isValid) {
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

        if (map["event_name"] == null) {
            UALog.e("CustomEventAction requires an event name in the event data.")
            return false
        }

        return true
    }

    /**
     * Default [AddCustomEventAction] predicate.
     */
    public class AddCustomEventActionPredicate public constructor() : ActionRegistry.Predicate {

        override fun apply(arguments: ActionArguments): Boolean {
            return Situation.PUSH_RECEIVED != arguments.situation
        }
    }

    public companion object {

        /**
         * Default registry name
         */
        public const val DEFAULT_REGISTRY_NAME: String = "add_custom_event_action"

        public const val IN_APP_CONTEXT_METADATA_KEY: String = "in_app_metadata"
    }
}
