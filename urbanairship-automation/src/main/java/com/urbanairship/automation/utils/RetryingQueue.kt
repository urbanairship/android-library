package com.urbanairship.automation.utils

import com.urbanairship.UALog
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

internal class RetryingQueue(
    private val maxConcurrentOperations: Int = 3,
    private val maxPendingResults: Int = 2,
    private val initialBackOff: Duration = 15.seconds,
    private val maxBackOff: Duration = 60.seconds,
    private val taskSleeper: TaskSleeper = TaskSleeper.default
) {
    init {
        require(maxConcurrentOperations > 0)
        require(maxPendingResults >= 0)
        require(initialBackOff.isPositive())
        require(maxBackOff.isPositive())
    }

    sealed class Result<T> {
        data class Success<T>(
            val result: T,
            val ignoreReturnOrder: Boolean = false
        ) : Result<T>()

        data class Retry<T>(val retryAfter: Duration? = null) : Result<T>()
    }

    private var nextTaskNumber = AtomicLong(0)

    // Used to start operations in order
    private val pendingStartTasks = MutableStateFlow<Set<Long>>(emptySet())

    // Used to return results in order
    private val startedTasks = MutableStateFlow<Set<Long>>(emptySet())

    // Used to prevent too many operations from running concurrently
    private val performingTasks = MutableStateFlow<Set<Long>>(emptySet())

    // Used to prevent operations from running concurrently if we have too many pending results
    private val pendingResultTasks = MutableStateFlow<Set<Long>>(emptySet())

    suspend fun <T> run(
        name: String,
        operation: suspend () -> Result<T>
    ): T {
        return coroutineScope {
            doTask(this, name, operation)
        }
    }

    private suspend fun awaitTaskId(scope: CoroutineScope, name: String): Long {
        val taskId = nextTaskNumber.getAndIncrement()

        pendingStartTasks.update { it + taskId }

        // Wait for the tasks turn
        pendingStartTasks.first {
            it.minOrNull() == taskId
        }

        checkCancelled(scope, name, taskId)

        // Wait until we are not running too many operations in parallel
        performingTasks.first {
            it.size < maxConcurrentOperations
        }

        checkCancelled(scope, name, taskId)

        // Wait until we are not waiting for too many pending results
        pendingResultTasks.first {
            it.size < maxPendingResults
        }

        checkCancelled(scope, name, taskId)

        pendingStartTasks.update { it - taskId }
        startedTasks.update { it + taskId  }
        performingTasks.update { it + taskId }

        return taskId
    }

    private fun checkCancelled(scope: CoroutineScope, name: String, taskId: Long? = null) {
        if (!scope.isActive) {
            UALog.v  {"Operation $name cancelled" }
            taskId?.let { id ->
                startedTasks.update { it - id  }
                performingTasks.update { it - id }
                pendingResultTasks.update { it - id }
                pendingStartTasks.update { it - id }
            }
            scope.ensureActive()
        }
    }

    private suspend fun <T> doTask(scope: CoroutineScope, name: String, operation: suspend  () -> Result<T>): T {
        var backOff = initialBackOff

        while (true) {
            checkCancelled(scope, name)

            val taskId = awaitTaskId(scope, name)

            checkCancelled(scope, name, taskId)

            val result = try {
                operation.invoke()
            } catch(e: Exception) {
                UALog.e(e) { "Operation $name failed with exception. Retrying" }
                Result.Retry()
            }

            performingTasks.update {
                it - taskId
            }

            when (result) {
                is Result.Retry -> {
                    startedTasks.update { it - taskId }
                    checkCancelled(scope, name, taskId)

                    UALog.v { "Operation $name retrying $result" }
                    val delay = if (result.retryAfter?.isFinite() == true && !result.retryAfter.isNegative()) {
                        result.retryAfter
                    } else {
                        backOff
                    }

                    taskSleeper.sleep(delay)
                    backOff = (delay * 2).coerceIn(initialBackOff..maxBackOff)
                }

                is Result.Success -> {
                    UALog.v { "Operation $name finished" }

                    if (!result.ignoreReturnOrder) {
                        checkCancelled(scope, name, taskId)

                        UALog.v { "Operation $name waiting to return" }

                        pendingResultTasks.update { it + taskId }

                        // Wait for the operations task number be the next up (lowest) before returning
                        startedTasks.first {
                            it.minOrNull() == taskId
                        }

                        pendingResultTasks.update { it - taskId }
                    }

                    startedTasks.update { it - taskId }

                    UALog.v { "Operation $name returning result" }
                    return result.result
                }
            }
        }
    }
}
