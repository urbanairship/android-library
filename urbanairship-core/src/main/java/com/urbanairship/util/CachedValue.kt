/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.core.util.Predicate

/**
 * Caches a value in memory with an expiration.
 *
 * @param <T>
 *
 * @hide
</T> */
internal class CachedValue<T> (
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {

    private val lock = Any()
    private var expirationTime: Long = 0
    private var value: T? = null

    fun set(value: T?, expiresAt: Long) {
        synchronized(lock) {
            this.value = value
            this.expirationTime = expiresAt
        }
    }

    fun expire() {
        synchronized(lock) {
            this.value = null
            this.expirationTime = 0
        }
    }

    fun expireIf(predicate: Predicate<T>) {
        synchronized(lock) {
            if (value == null) { return@synchronized }
            if (!predicate.test(value)) { return@synchronized }

            this.value = null
            this.expirationTime = 0
        }
    }

    fun remainingCacheTimeMillis(): Long {
        return maxOf(expirationTime - clock.currentTimeMillis(), 0)
    }

    fun get(): T? {
        synchronized(lock) {
            if (remainingCacheTimeMillis() > 0) {
                return value
            }

            value = null
            return value
        }
    }
}
