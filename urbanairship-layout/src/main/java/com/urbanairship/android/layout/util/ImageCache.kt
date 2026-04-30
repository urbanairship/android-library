/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

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
    @MainThread
    public fun get(url: String): CachedImage?

    /**
     * Adds a child image cache. Used for dynamically add or remove cached content
     * @param cache The cache to add, null if the child wasn't added
     * @return The cache id
     */
    public fun tryAddChild(cache: ImageCache): String?

    /**
     * Removes a child image cache
     * @param id The cache id
     */
    public fun removeChild(id: String)
}

/**
 * Cached image result
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class CachedImage(public val path: String, public val size: android.util.Size?)

/**
 * Default extendable image cache
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExtendableImageCache(
    parent: ((String) -> CachedImage?)? = null
) : ImageCache {
    private val children = MutableStateFlow<Map<String, ImageCache>>(emptyMap())

    init {
        if (parent != null) {
            val cache = object : ImageCache {
                override fun get(url: String): CachedImage? {
                    return parent(url)
                }

                override fun tryAddChild(cache: ImageCache): String? = null
                override fun removeChild(id: String) {}
            }

            children.update { mapOf(UUID.randomUUID().toString() to cache) }
        }
    }

    override fun get(url: String): CachedImage? {
        if (children.value.isEmpty()) {
            return null
        }

        for (child in children.value.values) {
            val result = child.get(url) ?: continue
            return result
        }

        return null
    }

    override fun tryAddChild(cache: ImageCache): String {
        val id = UUID.randomUUID().toString()
        children.update { it + (id to cache) }
        return id
    }

    override fun removeChild(id: String) {
        children.update { it - id }
    }
}

internal class NonExtendableImageCache(
    private val getter: (String) -> CachedImage?
): ImageCache {

    override fun get(url: String): CachedImage? {
        return getter(url)
    }

    override fun tryAddChild(cache: ImageCache): String? = null

    override fun removeChild(id: String) { }
}
