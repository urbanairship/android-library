/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An action that adds a custom event.
 * <p/>
 * Accepted situations: all
 * <p/>
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
 * <p/>
 * Result value: <code>null</code>
 * <p/>
 * Default Registration Name: add_custom_event_action
 * <p/>
 * Default Registration Predicate: Rejects SITUATION_PUSH_RECEIVED
 */
public class AddCustomEventAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "add_custom_event_action";

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {

        JsonMap customEventMap = arguments.getValue().getMap();

        // Parse the event values from the map
        String eventName = customEventMap.opt(CustomEvent.EVENT_NAME).getString();

        String eventStringValue = customEventMap.opt(CustomEvent.EVENT_VALUE).getString();
        double eventDoubleValue = customEventMap.opt(CustomEvent.EVENT_VALUE).getDouble(0);

        String transactionId = customEventMap.opt(CustomEvent.TRANSACTION_ID).getString();
        String interactionType = customEventMap.opt(CustomEvent.INTERACTION_TYPE).getString();
        String interactionId = customEventMap.opt(CustomEvent.INTERACTION_ID).getString();
        JsonMap properties = customEventMap.opt(CustomEvent.PROPERTIES).getMap();

        CustomEvent.Builder eventBuilder = new CustomEvent.Builder(eventName)
                .setTransactionId(transactionId)
                .setInteraction(interactionType, interactionId)
                .setAttribution((PushMessage) arguments.getMetadata().getParcelable(ActionArguments.PUSH_MESSAGE_METADATA));

        if (eventStringValue != null) {
            eventBuilder.setEventValue(eventStringValue);
        } else {
            eventBuilder.setEventValue(eventDoubleValue);
        }

        // Try to fill in the interaction if its not set
        if (interactionId == null && interactionType == null) {
            String messageId = arguments.getMetadata().getString(ActionArguments.RICH_PUSH_ID_METADATA);
            RichPushMessage message = UAirship.shared().getInbox().getMessage(messageId);

            if (message != null) {
                eventBuilder.setInteraction(message);
            }
        }

        if (properties != null) {
            for (Map.Entry<String, JsonValue> property : properties) {
                if (property.getValue().isBoolean()) {
                    eventBuilder.addProperty(property.getKey(), property.getValue().getBoolean(false));
                } else if (property.getValue().isDouble()) {
                    eventBuilder.addProperty(property.getKey(), property.getValue().getDouble(0));
                } else if (property.getValue().isNumber()) {
                    eventBuilder.addProperty(property.getKey(), property.getValue().getNumber().longValue());
                } else if (property.getValue().isString()) {
                    eventBuilder.addProperty(property.getKey(), property.getValue().getString());
                } else if (property.getValue().isJsonList()) {
                    List<String> strings = new ArrayList<>();

                    for (JsonValue jsonValue : property.getValue().getList()) {
                        if (jsonValue.isString()) {
                            strings.add(jsonValue.getString());
                        } else {
                            strings.add(jsonValue.toString());
                        }
                    }

                    eventBuilder.addProperty(property.getKey(), strings);
                }
            }
        }

        CustomEvent event = eventBuilder.create();
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
                Logger.debug("CustomEventAction requires an event name in the event data.");
                return false;
            }
            return true;
        }

        Logger.debug("CustomEventAction requires a map of event data.");
        return false;
    }

    /**
     * Default {@link AddCustomEventAction} predicate.
     */
    public static class AddCustomEventActionPredicate implements ActionRegistry.Predicate {

        @Override
        public boolean apply(ActionArguments arguments) {
            return Action.SITUATION_PUSH_RECEIVED != arguments.getSituation();
        }

    }
}
