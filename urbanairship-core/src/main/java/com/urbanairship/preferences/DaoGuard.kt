/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import com.urbanairship.UALog
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a DAO operation, re-throwing [CancellationException] so coroutine cancellation propagates
 * normally and logging anything else before returning [default]. Used by [AsyncPreferenceStore]
 * and the one-off obsolete-keys cleanup in [PreferenceStore].
 */
internal inline fun <T> guardDao(op: String, default: T, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    UALog.e(e) { "Preference DAO op failed: $op" }
    default
}
