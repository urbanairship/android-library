/* Copyright Airship and Contributors */

package com.urbanairship.chat

import com.urbanairship.AirshipExecutors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Coroutine dispatchers using the Airship thread pools.
 */
internal object AirshipDispatchers {

    /**
     * Dispatcher that uses the full thread pool
     */
    val IO: CoroutineDispatcher = AirshipExecutors.threadPoolExecutor().asCoroutineDispatcher()

    /**
     * Creates a new single thread dispatcher.
     */
    fun newSingleThreadDispatcher(): CoroutineDispatcher {
        return AirshipExecutors.newSerialExecutor().asCoroutineDispatcher()
    }
}
