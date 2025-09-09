/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.Airship
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AppStoreUtilsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val emptyConfig = AirshipConfigOptions.newBuilder().build()

    @Test
    public fun testAmazonIntent() {
        val intent = AppStoreUtils.getAppStoreIntent(context, Airship.Platform.AMAZON, emptyConfig)
        Assert.assertEquals("amzn://apps/android?p=com.urbanairship.test", intent.data.toString())
        Assert.assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    @Test
    public fun testAndroidIntent() {
        val intent = AppStoreUtils.getAppStoreIntent(context, Airship.Platform.ANDROID, emptyConfig)
        Assert.assertEquals(
            "https://play.google.com/store/apps/details?id=com.urbanairship.test",
            intent.data.toString()
        )
        Assert.assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    @Test
    public fun testOverrideUri() {
        val configOptions = AirshipConfigOptions.newBuilder().setAppStoreUri(Uri.parse("https://neat")).build()

        val intent = AppStoreUtils.getAppStoreIntent(context, Airship.Platform.ANDROID, configOptions)
        Assert.assertEquals("https://neat", intent.data.toString())
        Assert.assertEquals(Intent.ACTION_VIEW, intent.action)
    }
}
