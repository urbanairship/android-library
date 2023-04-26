/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield

/**
 * Runs suspending blocks completely in FIFO order
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SerialQueue {

    private var nextTaskNumber = AtomicLong(0)
    private var currentTaskNumber = AtomicLong(0)
    @Volatile
    private var job: Job? = null

    public suspend fun <T> run(operation: suspend () -> T): T {
        val myTask = nextTaskNumber.getAndIncrement()

        return coroutineScope {
            while (currentTaskNumber.get() != myTask) {
                job?.join()
                if (currentTaskNumber.get() != myTask) {
                    yield()
                }
            }

            val deferred = async {
                operation.invoke()
            }
            job = deferred

            deferred.invokeOnCompletion {
                currentTaskNumber.incrementAndGet()
            }

            deferred.await()
        }
    }
}
