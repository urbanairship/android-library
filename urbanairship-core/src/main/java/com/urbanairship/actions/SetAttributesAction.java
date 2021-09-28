/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AttributeEditor;
import com.urbanairship.json.JsonValue;

import java.util.Date;
import java.util.Map;

import androidx.annotation.NonNull;

/**
 * An action that sets attributes.
 * <p>
 * Accepted situations: all
 * <p>
 * Accepted argument value types: A JSON payload for setting or removing attributes. An example JSON payload:
 * <pre>
 * {
 *   "channel": {
 *     set: {"key_1": value_1, "key_2": value_2},
 *     remove: ["attribute_1", "attribute_2", "attribute_3"]
 *   },
 *   "named_user": {
 *     set: {"key_4": value_4, "key_5": value_5},
 *     remove: ["attribute_4", "attribute_5", "attribute_6"]
 *   }
 * </pre>
 */
public class SetAttributesAction extends Action {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "set_attributes_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^a";

    /**
     * JSON key for channel attributes changes.
     */
    @NonNull
    private static final String CHANNEL_KEY = "channel";

    /**
     * JSON key for named user attributes changes.
     */
    @NonNull
    private static final String NAMED_USER_KEY = "named_user";

    /**
     * JSON key for setting attributes.
     */
    @NonNull
    private static final String SET_KEY = "set";

    /**
     * JSON key for removing attributes.
     */
    @NonNull
    private static final String REMOVE_KEY = "remove";

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        if (arguments.getValue().getMap() != null) {

            // Channel Attribute
            if (arguments.getValue().getMap().containsKey(CHANNEL_KEY)) {
                AttributeEditor channelAttributeEditor = UAirship.shared().getChannel().editAttributes();
                for (Map.Entry<String, JsonValue> entry : arguments.getValue().getMap().opt(CHANNEL_KEY).optMap().getMap().entrySet()) {
                    handleAttributeActions(channelAttributeEditor, entry);
                }
                channelAttributeEditor.apply();
            }

            // Contact Attribute
            if (arguments.getValue().getMap().containsKey(NAMED_USER_KEY)) {
                AttributeEditor contactEditor = UAirship.shared().getContact().editAttributes();
                for (Map.Entry<String, JsonValue> entry : arguments.getValue().getMap().opt(NAMED_USER_KEY).optMap().getMap().entrySet()) {
                    handleAttributeActions(contactEditor, entry);
                }
                contactEditor.apply();
            }

        }

        return ActionResult.newEmptyResult();
    }

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        if (arguments.getValue().isNull()) {
            return false;
        }

        if (arguments.getValue().getMap() == null) {
            return false;
        }

        // Channel attributes
        JsonValue channel = arguments.getValue().getMap().opt(CHANNEL_KEY);
        if (channel != JsonValue.NULL && !areAttributeMutationsValid(channel)) {
            return false;
        }

        // Named User attributes
        JsonValue namedUser = arguments.getValue().getMap().opt(NAMED_USER_KEY);
        if (namedUser != JsonValue.NULL && !areAttributeMutationsValid(namedUser)) {
            return false;
        }

        return channel != JsonValue.NULL || namedUser != JsonValue.NULL;
    }

    private boolean areAttributeMutationsValid(@NonNull JsonValue attributeMutations) {
        if (attributeMutations.getMap() == null) {
            return false;
        }

        JsonValue set = attributeMutations.optMap().opt(SET_KEY);
        if (set != JsonValue.NULL && !isSetAttributeMutationValid(set)) {
            return false;
        }

        JsonValue remove = attributeMutations.optMap().opt(REMOVE_KEY);
        if (remove != JsonValue.NULL && !isRemoveAttributeMutationValid(remove)) {
            return false;
        }

        return true;
    }

    private boolean isSetAttributeMutationValid(@NonNull JsonValue setAttributeMutation) {
        return setAttributeMutation.getMap() != null;
    }

    private boolean isRemoveAttributeMutationValid(@NonNull JsonValue removeAttributeMutation) {
        return removeAttributeMutation.getList() != null;
    }

    /**
     * Handles the attributes updates
     * @param attributeEditor The attribute editor
     * @param entry The attribute entry
     */
    private void handleAttributeActions(@NonNull AttributeEditor attributeEditor, @NonNull Map.Entry<String, JsonValue> entry) {
        switch (entry.getKey()) {
            case SET_KEY:
                for (Map.Entry<String, JsonValue> setAttributeEntry : entry.getValue().optMap().entrySet()) {
                    setAttribute(attributeEditor, setAttributeEntry.getKey(), setAttributeEntry.getValue().getValue());
                }
                break;
            case REMOVE_KEY:
                for (JsonValue jsonValue : entry.getValue().optList().getList()) {
                    attributeEditor.removeAttribute(jsonValue.optString());
                }
                break;
            default:
                break;
        }
    }

    /**
     * Apply the attribute settings.
     * @param attributeEditor The attribute editor.
     * @param key The attribute key.
     * @param value The attribute value.
     */
    private void setAttribute(@NonNull AttributeEditor attributeEditor, @NonNull String key, @NonNull Object value) {
        if (value instanceof Integer) {
            attributeEditor.setAttribute(key, (int) value);
        } else if (value instanceof Long) {
            attributeEditor.setAttribute(key, (long) value);
        } else if (value instanceof Float) {
            attributeEditor.setAttribute(key, (float) value);
        } else if (value instanceof Double) {
            attributeEditor.setAttribute(key, (double) value);
        } else if (value instanceof String) {
            attributeEditor.setAttribute(key, (String) value);
        } else if (value instanceof Date) {
            attributeEditor.setAttribute(key, (Date) value);
        } else {
            Logger.warn("SetAttributesAction - Invalid value type for the key: %s", key);
        }
    }

    /**
     * Default {@link SetAttributesAction.SetAttributesPredicate} predicate.
     */
    public static class SetAttributesPredicate implements ActionRegistry.Predicate {

        @Override
        public boolean apply(@NonNull ActionArguments arguments) {
            return Action.SITUATION_PUSH_RECEIVED != arguments.getSituation();
        }

    }

}
