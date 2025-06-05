/* Copyright Airship and Contributors */
package com.urbanairship

import androidx.annotation.RestrictTo
import com.urbanairship.util.AirshipThreadFactory
import com.urbanairship.util.SerialExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Common Executors for Airship.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object AirshipExecutors {

    private val THREAD_POOL_EXECUTOR: ExecutorService =
        Executors.newCachedThreadPool(AirshipThreadFactory.DEFAULT_THREAD_FACTORY)

    /**
     * The shared thread pool executor.
     */
    @JvmStatic
    public fun threadPoolExecutor(): ExecutorService {
        return THREAD_POOL_EXECUTOR
    }

    /**
     * Creates a new serial executor that shares threads with the [.THREAD_POOL_EXECUTOR].
     *
     * @return A new serial executor.
     */
    @JvmStatic
    public fun newSerialExecutor(): Executor {
        return SerialExecutor(THREAD_POOL_EXECUTOR)
    }
}
