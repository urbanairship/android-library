/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import com.urbanairship.UALog
import kotlin.coroutines.cancellation.CancellationException

/**
 * Wraps a DAO operation that should degrade gracefully on failure.
 *
 * Re-throws [CancellationException] so coroutine cancellation propagates normally; logs any
 * other [Throwable] and returns [default]. [op] is a short label included in the log message
 * so failures can be traced back to a specific call site.
 */
internal inline fun <T> guardDao(op: String, default: T, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    UALog.e(e) { "Preference DAO op failed: $op" }
    default
}
