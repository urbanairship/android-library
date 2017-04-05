/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.support.annotation.NonNull;

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
    public ActionRunRequest createActionRequest(String actionName) {
        return ActionRunRequest.createRequest(actionName);
    }

}
