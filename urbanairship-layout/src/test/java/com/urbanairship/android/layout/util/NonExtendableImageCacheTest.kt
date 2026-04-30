/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

public class NonExtendableImageCacheTest {

    private val sampleA = CachedImage("/cache/a.png", null)

    @Test
    public fun get_delegatesToLambda() {
        val cache = NonExtendableImageCache { url ->
            if (url == "x") sampleA else null
        }
        assertEquals(sampleA, cache.get("x"))
        assertNull(cache.get("y"))
    }

    @Test
    public fun tryAddChild_returnsNull() {
        val cache = NonExtendableImageCache { null }
        assertNull(cache.tryAddChild(ExtendableImageCache()))
    }

    @Test
    public fun removeChild_isNoOp() {
        val cache = NonExtendableImageCache { sampleA }
        cache.removeChild("any-id")
        assertEquals(sampleA, cache.get("z"))
    }
}
