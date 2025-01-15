/* Copyright Airship and Contributors */

package com.urbanairship.util;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.Predicate;

/**
 * Caches a value in memory with an expiration.
 *
 * @param <T>
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CachedValue<T> {

    private final Object lock = new Object();
    private final Clock clock;
    private long expiration;
    private T value;

    public CachedValue() {
        this(Clock.DEFAULT_CLOCK);
    }

    public CachedValue(@NonNull Clock clock) {
        this.clock = clock;
    }
    public void set(@Nullable T value, long expiryDateMs) {
        synchronized (lock) {
            this.value = value;
            this.expiration = expiryDateMs;
        }
    }

    public void expire() {
        synchronized (lock) {
            this.value = null;
            this.expiration = 0;
        }
    }

    public void expireIf(Predicate<T> predicate) {
        synchronized (lock) {
            if (value != null && predicate.test(value)) {
                this.value = null;
                this.expiration = 0;
            }
        }
    }

    public long remainingCacheTimeMillis() {
        long remaining = expiration - clock.currentTimeMillis();
        if (remaining >= 0) {
            return remaining;
        }
        return 0;
    }

    @Nullable
    public T get() {
        synchronized (lock) {
            if (clock.currentTimeMillis() >= expiration) {
                return null;
            }

            return value;
        }
    }

}
