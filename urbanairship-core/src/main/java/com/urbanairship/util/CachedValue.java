package com.urbanairship.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Caches a value in memory with an expiration.
 * @param <T>
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

    public void set(@Nullable T value, long expiresMs) {
        synchronized (lock) {
            this.value = value;
            this.expiration = clock.currentTimeMillis() + expiresMs;
        }
    }

    public void invalidate() {
        synchronized (lock) {
            this.value = null;
            this.expiration = 0;
        }
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
