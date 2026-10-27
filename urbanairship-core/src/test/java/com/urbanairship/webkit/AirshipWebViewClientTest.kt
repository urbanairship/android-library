/* Copyright Airship and Contributors */
package com.urbanairship.webkit

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.AirshipLogHandler
import com.urbanairship.UALog
import com.urbanairship.UrlAllowList
import com.urbanairship.javascript.JavaScriptEnvironment
import com.urbanairship.javascript.JavaScriptExecutor
import com.urbanairship.javascript.NativeBridge
import com.urbanairship.javascript.NativeBridge.CommandDelegate
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AirshipWebViewClientTest {

    private val rootView: View = mockk(relaxed = true)
    private var webViewUrl: String? = "http://test-client"
    private val webView: WebView = mockk(relaxed = true) {
        every { rootView } answers { this@AirshipWebViewClientTest.rootView }
        every { url } answers { this@AirshipWebViewClientTest.webViewUrl }
        every { context } answers { ApplicationProvider.getApplicationContext() }
    }
    private val nativeBridge: NativeBridge = mockk()

    private val allowList: UrlAllowList = mockk {
        every { isAllowed("http://test-client", UrlAllowList.Scope.JAVASCRIPT_INTERFACE) } returns true
    }

    private var javaScriptExtender: (JavaScriptEnvironment.Builder) -> JavaScriptEnvironment.Builder  = { builder ->
        builder
    }

    private var client: AirshipWebViewClient = AirshipWebViewClient(
        nativeBridge = nativeBridge,
        allowListProvider = { allowList },
        javaScriptExtender = javaScriptExtender
    )

    /**
     * Test any uairship scheme does not get intercepted when the webview's url is not allowed.
     */
    @Test
    public fun testHandleCommandNotAllowed() {
        every { allowList.isAllowed("http://not-allowed", any()) } returns false

        webViewUrl = "http://not-allowed"
        val url = "uairship://run-actions?action"
        Assert.assertFalse(client.shouldOverrideUrlLoading(webView, url))

        webViewUrl = null
        Assert.assertFalse(client.shouldOverrideUrlLoading(webView, url))
        verify { nativeBridge wasNot Called }
    }

    /**
     * Test a custom scheme is not handled and logs a warning pointing to the uairship deep link form.
     */
    @Test
    public fun testUnsupportedSchemeLogsWarning() {
        val url = "myapp://play/123"
        every { nativeBridge.onHandleCommand(url, any(), any(), any()) } returns false

        val warnings = captureWarnings {
            Assert.assertFalse(client.shouldOverrideUrlLoading(webView, url))
        }

        Assert.assertEquals(1, warnings.size)
        Assert.assertTrue(warnings[0].contains(url))
        Assert.assertTrue(warnings[0].contains("uairship://run-basic-actions?deep_link_action="))
        Assert.assertTrue(warnings[0].contains("urlAllowListScopeJavaScriptInterface"))
    }

    /**
     * Test schemes the web view can load, or that Airship handles, do not log a warning.
     */
    @Test
    public fun testSupportedSchemesDoNotLogWarning() {
        every { nativeBridge.onHandleCommand(any(), any(), any(), any()) } returns false

        val warnings = captureWarnings {
            listOf(
                "https://example.com", "HTTP://example.com", "uairship://close", "about:blank",
                "data:text/html,hi", "file:///android_asset/page.html"
            ).forEach { client.shouldOverrideUrlLoading(webView, it) }
        }

        Assert.assertTrue(warnings.isEmpty())
    }

    private fun captureWarnings(block: () -> Unit): List<String> {
        val warnings = mutableListOf<String>()
        val previousHandler = UALog.logHandler
        val previousLevel = UALog.logLevel
        UALog.logLevel = Log.WARN
        UALog.logHandler = AirshipLogHandler { _, logLevel, _, message ->
            if (logLevel == Log.WARN) {
                warnings.add(message())
            }
        }

        try {
            block()
        } finally {
            UALog.logHandler = previousHandler
            UALog.logLevel = previousLevel
        }
        return warnings
    }

    /**
     * Test onPageFinished loads the js bridge
     */
    @Test
    @SuppressLint("NewApi")
    public fun testOnPageFinished() {
        every { nativeBridge.loadJavaScriptEnvironment(any(), any(), any()) } answers {
            val argument = arg<JavaScriptExecutor>(2)
            argument.executeJavaScript("test")
            mockk()
        }

        client.onPageFinished(webView, webViewUrl)

        verify { webView.evaluateJavascript("test", null) }
    }

    /**
     * Test the js interface is not injected if the url is not allowed.
     */
    @Test
    @SuppressLint("NewApi")
    public fun testOnPageFinishedNotAllowed() {
        every { allowList.isAllowed("http://notallowed", any()) } returns false
        webViewUrl = "http://notallowed"
        client.onPageFinished(webView, webViewUrl)
        verify { nativeBridge wasNot Called }
    }

    /**
     * Test close command calls onClose
     */
    @Test
    public fun testOnClose() {
        val url = "uairship://close"

        val spy = spyk(client)
        val commandDelegate = slot<CommandDelegate>()

        every { nativeBridge.onHandleCommand(url, any(), any(), capture(commandDelegate)) } returns true

        every { rootView.dispatchKeyEvent(any()) } answers {
            val event: KeyEvent = firstArg()
            val isKeyDown = KeyEvent.ACTION_DOWN == event.action && KeyEvent.KEYCODE_BACK == event.keyCode
            val isKeyUp = KeyEvent.ACTION_UP == event.action && KeyEvent.KEYCODE_BACK == event.keyCode
            Assert.assertTrue(isKeyDown || isKeyUp)
            true
        }

        spy.shouldOverrideUrlLoading(webView, url)

        commandDelegate.captured.onClose()

        verify { spy.onClose(webView) }

        verify { rootView.dispatchKeyEvent(any()) }
    }

    /**
     * Test close command calls onClose
     */
    @Test
    public fun testOnAirshipCommand() {
        val url = "uairship://cool"

        val spy = spyk(client)
        val commandDelegate = slot<CommandDelegate>()

        every { nativeBridge.onHandleCommand(url, any(), any(), capture(commandDelegate)) } returns true

        spy.shouldOverrideUrlLoading(webView, url)

        // Call close
        commandDelegate.captured.onAirshipCommand("cool", Uri.parse(url))
        verify { spy.onAirshipCommand(webView, "cool", Uri.parse(url)) }
    }

    /**
     * Test JavaScriptExecutor executes on the right web view.
     */
    @Test
    public fun testHandleCommandJavaScriptExecutor() {
        val url = "uairship://whatever"

        val jsExecutor = slot<JavaScriptExecutor>()

        every { nativeBridge.onHandleCommand(url, capture(jsExecutor), any(), any()) } returns true

        client.shouldOverrideUrlLoading(webView, url)

        // Call close
        jsExecutor.captured.executeJavaScript("cool")
        verify { webView.evaluateJavascript("cool", null) }
    }
}
