/* Copyright Airship and Contributors */

package com.urbanairship

import androidx.annotation.RestrictTo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Coroutine dispatchers using the Airship thread pools.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object AirshipDispatchers {

    /**
     * Dispatcher that uses the full thread pool
     */
    public val IO: CoroutineDispatcher = AirshipExecutors.threadPoolExecutor().asCoroutineDispatcher()

    /**
     * Creates a new serial dispatcher.
     */
    public fun newSerialDispatcher(): CoroutineDispatcher {
        return AirshipExecutors.newSerialExecutor().asCoroutineDispatcher()
    }
}
