package com.urbanairship.util

import java.util.ArrayDeque
import java.util.concurrent.Executor

/**
 * Executor that executes [Runnable] serially on another executor.
 *
 * @hide
 */
internal class SerialExecutor(private val executor: Executor) : Executor {

    private val runnables = ArrayDeque<Runnable>()
    private var isExecuting = false

    override fun execute(runnable: Runnable?) {
        if (runnable == null) {
            return
        }

        val wrapped = Runnable {
            try {
                runnable.run()
            } finally {
                next()
            }
        }

        synchronized(runnables) {
            runnables.offer(wrapped)
            if (!isExecuting) {
                next()
            }
        }
    }

    private fun next() {
        synchronized(runnables) {
            val next = runnables.pollFirst()
            if (next != null) {
                isExecuting = true
                executor.execute(next)
            } else {
                isExecuting = false
            }
        }
    }
}
