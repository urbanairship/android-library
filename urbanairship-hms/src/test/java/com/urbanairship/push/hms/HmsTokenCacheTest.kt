package com.urbanairship.push.hms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class HmsTokenCacheTest {

    @Test
    fun testTokenCache() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val cache = HmsTokenCache()

        Assert.assertNull(cache[context])

        cache[context] = "token"
        Assert.assertEquals("token", cache[context])

        cache[context] = "rad"
        Assert.assertEquals("rad", cache[context])

        cache[context] = null
        Assert.assertNull(cache[context])
    }

    @Test
    fun testTokenPersists() {
        val context: Context = ApplicationProvider.getApplicationContext()
        var cache = HmsTokenCache()

        cache[context] = "token"
        Assert.assertEquals("token", cache[context])

        cache = HmsTokenCache()
        Assert.assertEquals("token", cache[context])
    }
}
