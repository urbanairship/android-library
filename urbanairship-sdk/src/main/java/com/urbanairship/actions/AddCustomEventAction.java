/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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
 * Default Registration Predicate: Only accepts Situation.WEB_VIEW_INVOCATION and
 * Situation.MANUAL_INVOCATION
 */
public class AddCustomEventAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "add_custom_event_action";

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
            RichPushMessage message = UAirship.shared().getRichPushManager().getRichPushInbox().getMessage(messageId);

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

        CustomEvent event = eventBuilder.addEvent();
        if (event.isValid()) {
            return ActionResult.newEmptyResult();
        } else {
            return ActionResult.newErrorResult(new IllegalArgumentException("Unable to add custom event. Event is invalid."));
        }
    }

    @Override
    public boolean acceptsArguments(ActionArguments arguments) {
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


}
