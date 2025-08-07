package com.urbanairship.push.hms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.push.hms.AirshipHmsIntegration.processNewToken
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class AirshipHmsIntegrationTest {

    @Test
    fun testProcessNewToken() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val cache = HmsTokenCache.shared()

        Assert.assertNull(cache[context])

        processNewToken(context, "token")
        Assert.assertEquals("token", cache[context])
    }
}
