/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class ExtendableImageCacheTest {

    private val sampleA = CachedImage("/cache/a.png", null)
    private val sampleB = CachedImage("/cache/b.png", null)

    @Test
    public fun get_returnsNullWhenNoChildren() {
        val cache = ExtendableImageCache()
        assertNull(cache.get("https://example.com/x.png"))
    }

    @Test
    public fun parentLambda_resolvesUrls() {
        val cache = ExtendableImageCache { url ->
            if (url == "https://parent.only/img.png") sampleA else null
        }
        assertEquals(sampleA, cache.get("https://parent.only/img.png"))
        assertNull(cache.get("https://other.com/x.png"))
    }

    @Test
    public fun tryAddChild_returnsIdAndGetUsesChild() {
        val cache = ExtendableImageCache()
        val child = NonExtendableImageCache { url ->
            if (url == "https://child/img.png") sampleB else null
        }
        val id = cache.tryAddChild(child)
        assertTrue(id.isNotEmpty())
        assertEquals(sampleB, cache.get("https://child/img.png"))
        assertNull(cache.get("https://unknown/img.png"))
    }

    @Test
    public fun get_firstMatchingChildWins() {
        val cache = ExtendableImageCache()
        cache.tryAddChild(NonExtendableImageCache { sampleA })
        cache.tryAddChild(NonExtendableImageCache { sampleB })
        assertEquals(sampleA, cache.get("https://any.url/image.png"))
    }

    @Test
    public fun parentThenChild_childUsedWhenParentReturnsNull() {
        val cache = ExtendableImageCache { url ->
            if (url == "https://p/a") sampleA else null
        }
        cache.tryAddChild(NonExtendableImageCache { url ->
            if (url == "https://c/b") sampleB else null
        })
        assertEquals(sampleA, cache.get("https://p/a"))
        assertEquals(sampleB, cache.get("https://c/b"))
        assertNull(cache.get("https://none/n.png"))
    }

    @Test
    public fun removeChild_stopsResolvingFromThatChild() {
        val cache = ExtendableImageCache()
        val id = cache.tryAddChild(NonExtendableImageCache { sampleA })
        assertEquals(sampleA, cache.get("u"))
        cache.removeChild(id)
        assertNull(cache.get("u"))
    }
}
