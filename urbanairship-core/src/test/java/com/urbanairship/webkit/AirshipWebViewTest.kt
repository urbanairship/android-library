/* Copyright Airship and Contributors */
package com.urbanairship.webkit

import android.content.Context
import android.webkit.ValueCallback
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.urbanairship.util.ManifestUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
public class AirshipWebViewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    public fun setUp() {
        mockkStatic(WebViewFeature::class, WebSettingsCompat::class, WebViewCompat::class)
        mockkObject(ManifestUtils)
        every { WebViewFeature.isFeatureSupported(any()) } returns true
        every { WebSettingsCompat.getSafeBrowsingEnabled(any()) } returns true
        every { ManifestUtils.isWebViewSafeBrowsingEnabled(any()) } returns true
    }

    @After
    public fun tearDown() {
        unmockkAll()
    }

    @Test
    public fun testLoadsWhenStartSafeBrowsingThrows() {
        every { WebViewCompat.startSafeBrowsing(any(), any()) } throws NullPointerException("statics")

        val webView = AirshipWebView(context)
        webView.loadUrl("https://example.com/first")
        assertEquals("https://example.com/first", shadowOf(webView).lastLoadedUrl)

        // A failed attempt counts as attempted, so later loads don't call into the provider again.
        webView.loadUrl("https://example.com/second")
        assertEquals("https://example.com/second", shadowOf(webView).lastLoadedUrl)
        verify(exactly = 1) { WebViewCompat.startSafeBrowsing(any(), any()) }
    }

    @Test
    public fun testLoadsWhenStartSafeBrowsingSucceeds() {
        every { WebViewCompat.startSafeBrowsing(any(), any()) } answers {
            secondArg<ValueCallback<Boolean>>().onReceiveValue(true)
        }

        val webView = AirshipWebView(context)
        webView.loadUrl("https://example.com")

        assertEquals("https://example.com", shadowOf(webView).lastLoadedUrl)
        verify(exactly = 1) { WebViewCompat.startSafeBrowsing(any(), any()) }
    }
}
