/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import androidx.annotation.NonNull;

/**
 * Extends an action run request.
 */
public interface ActionRunRequestExtender {

    /**
     * Extends an action run request.
     *
     * @param request The request.
     * @return The extended request.
     */
    @NonNull
    ActionRunRequest extend(@NonNull ActionRunRequest request);

}
