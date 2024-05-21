/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Runs suspending blocks completely in FIFO order
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SerialQueue {

    private var nextTaskNumber = AtomicLong(0)
    private val currentTaskNumber = MutableStateFlow<Long>(0)

    public suspend fun <T> run(operation: suspend () -> T): T {
        val myTask = nextTaskNumber.getAndIncrement()

        return coroutineScope {
            currentTaskNumber.first { it == myTask }
            val result = operation.invoke()

            currentTaskNumber.compareAndSet(myTask, myTask.inc())
            result
        }
    }
}
