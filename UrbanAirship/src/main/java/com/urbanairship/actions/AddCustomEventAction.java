/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.actions;

import com.urbanairship.Logger;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushMessage;

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
 * </ul>
 * When a custom event action is triggered from a Message Center Rich Push Message, the interaction type
 * and ID will automatically be filled for the message if they are left blank.
 * <p/>
 * Result value: <code>null</code>
 * <p/>
 * Default Registration Name: add_custom_event_action
 * <p/>
 * Default Registration Predicate: Only accepts Situation.WEB_VIEW_INVOCATION and
 * Situation.MANUAL_INVOCATION
 */
public class AddCustomEventAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "add_custom_event_action";


    @Override
    public ActionResult perform(String actionName, ActionArguments arguments) {
        Map map = (Map) arguments.getValue();

        // Parse the event values from the map
        String eventName = parseStringFromMap(map, CustomEvent.EVENT_NAME);
        String eventValue = parseStringFromMap(map, CustomEvent.EVENT_VALUE);
        String transactionId = parseStringFromMap(map, CustomEvent.TRANSACTION_ID);
        String interactionType = parseStringFromMap(map, CustomEvent.INTERACTION_TYPE);
        String interactionId = parseStringFromMap(map, CustomEvent.INTERACTION_ID);

        CustomEvent.Builder eventBuilder = new CustomEvent.Builder(eventName)
                .setEventValue(eventValue)
                .setTransactionId(transactionId)
                .setInteraction(interactionType, interactionId)
                .setAttribution(arguments.<PushMessage>getMetadata(ActionArguments.PUSH_MESSAGE_METADATA));

        // Try to fill in the interaction if its not set
        if (interactionId == null && interactionType == null) {
            RichPushMessage message = arguments.getMetadata(ActionArguments.RICH_PUSH_METADATA);
            if (message != null) {
                eventBuilder.setInteraction(message);
            }
        }

        eventBuilder.addEvent();
        return ActionResult.newEmptyResult();
    }

    @Override
    public boolean acceptsArguments(ActionArguments arguments) {
        if (!super.acceptsArguments(arguments)) {
            return false;
        }

        if (arguments.getValue() instanceof Map) {
            Map map = (Map) arguments.getValue();
            if (map.get("event_name") == null) {
                Logger.debug("CustomEventAction requires an event name in the event data.");
                return false;
            }
            return true;
        }

        Logger.debug("CustomEventAction requires a map of event data.");
        return false;
    }

    /**
     * Helper method to parse the string value of an object from a map.
     *
     * @param map The map of objects.
     * @param key The object's key.
     * @return The string value of the object, or null if the object does not exist.
     */
    private String parseStringFromMap(Map map, String key) {
        Object value = map.get(key);
        if (value != null) {
            return String.valueOf(value);
        }
        return null;
    }
}
