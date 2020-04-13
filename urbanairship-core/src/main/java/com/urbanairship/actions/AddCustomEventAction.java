/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.Logger;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.Checks;

import androidx.annotation.NonNull;

/**
 * An action that adds a custom event.
 * <p>
 * Accepted situations: all
 * <p>
 * Accepted argument value - A map of fields for the custom event:
 * <ul>
 * <li>{@link com.urbanairship.analytics.CustomEvent#EVENT_NAME}: String, Required</li>
 * <li>{@link com.urbanairship.analytics.CustomEvent#EVENT_VALUE}: number as a String or Number</li>
 * <li>{@link com.urbanairship.analytics.CustomEvent#TRANSACTION_ID}: String</li>
 * <li>{@link com.urbanairship.analytics.CustomEvent#INTERACTION_ID}: String</li>
 * <li>{@link com.urbanairship.analytics.CustomEvent#INTERACTION_TYPE}: String</li>
 * <li>{@link com.urbanairship.analytics.CustomEvent#PROPERTIES}: JsonMap of Strings, Booleans, Numbers, or arrays of Strings</li>
 * </ul>
 * When a custom event action is triggered from a Message Center Rich Push Message, the interaction type
 * and ID will automatically be filled for the message if they are left blank.
 * <p>
 * Result value: <code>null</code>
 * <p>
 * Default Registration Name: add_custom_event_action
 * <p>
 * Default Registration Predicate: Rejects SITUATION_PUSH_RECEIVED
 */
public class AddCustomEventAction extends Action {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "add_custom_event_action";

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        JsonMap customEventMap = arguments.getValue().toJsonValue().optMap();

        // Parse the event values from the map
        String eventName = customEventMap.opt(CustomEvent.EVENT_NAME).getString();
        Checks.checkNotNull(eventName, "Missing event name");

        String eventStringValue = customEventMap.opt(CustomEvent.EVENT_VALUE).getString();
        double eventDoubleValue = customEventMap.opt(CustomEvent.EVENT_VALUE).getDouble(0);

        String transactionId = customEventMap.opt(CustomEvent.TRANSACTION_ID).getString();
        String interactionType = customEventMap.opt(CustomEvent.INTERACTION_TYPE).getString();
        String interactionId = customEventMap.opt(CustomEvent.INTERACTION_ID).getString();
        JsonMap properties = customEventMap.opt(CustomEvent.PROPERTIES).getMap();

        CustomEvent.Builder eventBuilder = CustomEvent.newBuilder(eventName)
                .setTransactionId(transactionId)
                .setAttribution((PushMessage) arguments.getMetadata().getParcelable(ActionArguments.PUSH_MESSAGE_METADATA))
                .setInteraction(interactionType, interactionId);

        if (eventStringValue != null) {
            eventBuilder.setEventValue(eventStringValue);
        } else {
            eventBuilder.setEventValue(eventDoubleValue);
        }

        // Try to fill in the interaction if its not set
        if (interactionId == null && interactionType == null) {
            String messageId = arguments.getMetadata().getString(ActionArguments.RICH_PUSH_ID_METADATA);

            if (messageId != null) {
                eventBuilder.setMessageCenterInteraction(messageId);
            }
        }

        if (properties != null) {
            eventBuilder.setProperties(properties);
        }

        CustomEvent event = eventBuilder.build();
        event.track();
        if (event.isValid()) {
            return ActionResult.newEmptyResult();
        } else {
            return ActionResult.newErrorResult(new IllegalArgumentException("Unable to add custom event. Event is invalid."));
        }
    }

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        if (arguments.getValue().getMap() != null) {
            if (arguments.getValue().getMap().get("event_name") == null) {
                Logger.error("CustomEventAction requires an event name in the event data.");
                return false;
            }
            return true;
        }

        Logger.error("CustomEventAction requires a map of event data.");
        return false;
    }

    /**
     * Default {@link AddCustomEventAction} predicate.
     */
    public static class AddCustomEventActionPredicate implements ActionRegistry.Predicate {

        @Override
        public boolean apply(@NonNull ActionArguments arguments) {
            return Action.SITUATION_PUSH_RECEIVED != arguments.getSituation();
        }

    }

}
