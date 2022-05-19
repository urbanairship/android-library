/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;

/**
 * Factory class for creating {@link com.urbanairship.actions.ActionRunRequest}.
 */
public class ActionRunRequestFactory {

    private final Function<String, ActionRunRequest> factoryFunction;

    public ActionRunRequestFactory() {
        this.factoryFunction = ActionRunRequest::createRequest;
    }

    public ActionRunRequestFactory(Function<String, ActionRunRequest> function) {
        this.factoryFunction = function;
    }

    /**
     * Creates an action run request with a given action name.
     *
     * @param actionName The action name.
     * @return An action run request.
     */
    @NonNull
    public ActionRunRequest createActionRequest(@NonNull String actionName) {
        return this.factoryFunction.apply(actionName);
    }

}
