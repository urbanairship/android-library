/* Copyright Airship and Contributors */

package com.urbanairship.util

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
        val cutOff = clock.currentTimeMillis()
        entries.removeAll { entry ->
            cutOff >= entry.expiration
        }
    }

    public fun append(value: T, expiresInMilliseconds: Long) {
        val entry = Entry(
            value = value,
            expiration = clock.currentTimeMillis() + expiresInMilliseconds
        )

        lock.withLock {
            trim()
            entries.add(entry)
        }
    }

    private data class Entry<T>(val value: T, val expiration: Long)
}
