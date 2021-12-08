/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Image cache.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ImageCache {

    /**
     * Gets the cached image location.
     * @param url The original image URL.
     * @return The cached image location if cached, otherwise null.
     */
    @Nullable
    @MainThread
    String get(@NonNull String url);
}
