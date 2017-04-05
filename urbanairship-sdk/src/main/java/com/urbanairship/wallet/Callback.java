/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.wallet;


/**
 * Callback when executing a {@link PassRequest}.
 */
public interface Callback {
    /**
     * Called when the {@link Pass} was successfully downloaded.
     *
     * @param pass The {@link Pass}.
     */
    void onResult(Pass pass);

    /**
     * Called when an error occurred.
     *
     * @param errorCode The error code.
     */
    void onError(int errorCode);
}