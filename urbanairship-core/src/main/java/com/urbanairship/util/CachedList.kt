/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

/**
 * A list of values that each expire after a given duration.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CachedList<T>(private val clock: Clock = Clock.DEFAULT_CLOCK) {
    private val lock = ReentrantLock()
    private val entries = mutableListOf<Entry<T>>()

    public val values: List<T> get() {
        return lock.withLock {
            trim()
            entries.map { entry -> entry.value }
        }
    }

    private fun trim() {
        val cutOff = clock.now()
        entries.removeAll { entry ->
            !cutOff.isBefore(entry.expiration)
        }
    }

    public fun append(value: T, expiresIn: Duration) {
        val entry = Entry(
            value = value,
            expiration = clock.now() + expiresIn
        )

        lock.withLock {
            trim()
            entries.add(entry)
        }
    }

    private data class Entry<T>(val value: T, val expiration: Instant)
}
