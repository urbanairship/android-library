/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.Nullable;

import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.json.JsonValue;

import java.util.Map;

/**
 * Action utils for in-app messaging.
 */
public abstract class InAppActionUtils {

    /**
     * Runs actions from the button info.
     *
     * @param buttonInfo The button info.
     */
    public static void runActions(@Nullable ButtonInfo buttonInfo) {
        if (buttonInfo != null) {
            runActions(buttonInfo.getActions());
        }
    }

    /**
     * Runs a map of actions.
     *
     * @param actionsMap The action map.
     */
    public static void runActions(@Nullable Map<String, JsonValue> actionsMap) {
        runActions(actionsMap, null);
    }

    /**
     * Runs a map of actions.
     *
     * @param actionsMap The action map.
     * @param requestFactory Optional action request factory.
     */
    public static void runActions(@Nullable Map<String, JsonValue> actionsMap, @Nullable ActionRunRequestFactory requestFactory) {
        if (actionsMap == null) {
            return;
        }

        for (Map.Entry<String, JsonValue> entry : actionsMap.entrySet()) {
            ActionRunRequest request = requestFactory == null ? ActionRunRequest.createRequest(entry.getKey()) : requestFactory.createActionRequest(entry.getKey());
            request.setValue(entry.getValue()).run();
        }
    }

}
