/* Copyright Airship and Contributors */
package com.urbanairship.actions

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
    public fun extend(request: ActionRunRequest): ActionRunRequest
}
