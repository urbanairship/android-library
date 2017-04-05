/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.support.annotation.NonNull;

/**
 * An interface for callbacks signaling the completion of an
 * {@link com.urbanairship.actions.Action}.
 */
public interface ActionCompletionCallback {
    /**
     * Signals completion of the action.
     *
     * @param arguments The action arguments.
     * @param result The result of the action.
     */
    void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result);
}
