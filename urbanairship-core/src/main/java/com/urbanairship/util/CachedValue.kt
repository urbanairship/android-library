/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.core.util.Predicate
import java.time.Duration as JavaDuration
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

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
    private var expiration: Instant = Instant.EPOCH
    private var value: T? = null

    fun set(value: T?, expiresAt: Instant) {
        synchronized(lock) {
            this.value = value
            this.expiration = expiresAt
        }
    }

    fun expire() {
        synchronized(lock) {
            this.value = null
            this.expiration = Instant.EPOCH
        }
    }

    fun expireIf(predicate: Predicate<T>) {
        synchronized(lock) {
            if (value == null) { return@synchronized }
            if (!predicate.test(value)) { return@synchronized }

            this.value = null
            this.expiration = Instant.EPOCH
        }
    }

    fun remainingCacheTime(): Duration {
        val remaining = JavaDuration.between(clock.now(), expiration)
        return if (remaining.isNegative) Duration.ZERO else remaining.toKotlinDuration()
    }

    fun get(): T? {
        synchronized(lock) {
            if (remainingCacheTime() > Duration.ZERO) {
                return value
            }

            value = null
            return value
        }
    }
}
