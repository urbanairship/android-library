package com.urbanairship;

import android.support.annotation.Nullable;

/**
 * Result callback interface.
 *
 * @param <T> The type of result.
 */
public interface ResultCallback<T> {
    void onResult(@Nullable T result);
}
