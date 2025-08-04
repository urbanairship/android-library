/* Copyright Airship and Contributors */
package com.urbanairship.wallet

/**
 * Callback when executing a [PassRequest].
 */
public interface Callback {

    /**
     * Called when the [Pass] was successfully downloaded.
     *
     * @param pass The [Pass].
     */
    public fun onResult(pass: Pass)

    /**
     * Called when an error occurred.
     *
     * @param errorCode The error code.
     */
    public fun onError(errorCode: Int)
}
