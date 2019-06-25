/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import androidx.annotation.NonNull;

/**
 * Factory class for creating {@link com.urbanairship.actions.ActionRunRequest}.
 */
public class ActionRunRequestFactory {

    /**
     * Creates an action run request with a given action name.
     *
     * @param actionName The action name.
     * @return An action run request.
     */
    @NonNull
    public ActionRunRequest createActionRequest(@NonNull String actionName) {
        return ActionRunRequest.createRequest(actionName);
    }

}
