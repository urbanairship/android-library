/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo

/**
 * Image cache.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun interface ImageCache {
    /**
     * Gets the cached image location.
     * @param url The original image URL.
     * @return The cached image location if cached, otherwise null.
     */
    @MainThread
    public fun get(url: String): CachedImage?
}

/**
 * Cached image result
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class CachedImage(public val path: String, public val size: android.util.Size?)
