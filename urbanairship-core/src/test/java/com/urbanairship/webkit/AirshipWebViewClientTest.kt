/* Copyright Airship and Contributors */
package com.urbanairship.webkit

import android.annotation.SuppressLint
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import com.urbanairship.Airship
import com.urbanairship.contacts.Contact
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AirshipWebViewClientTest {

    private val rootView: View = mockk(relaxed = true)
    private var webViewUrl: String? = "http://test-client"
    private val webView: WebView = mockk(relaxed = true) {
        every { rootView } answers { this@AirshipWebViewClientTest.rootView }
        every { url } answers { this@AirshipWebViewClientTest.webViewUrl }
        every { context } answers { TestApplication.getApplication() }
    }
    private val nativeBridge: NativeBridge = mockk()
    private val mockContact: Contact = mockk(relaxed = true)

    private lateinit var client: AirshipWebViewClient

    @Before
    fun setup() {
        TestApplication.getApplication().setContact(mockContact)
        Airship.shared().urlAllowList.addEntry("http://test-client")
        client = AirshipWebViewClient(nativeBridge)
    }

    /**
     * Test any uairship scheme does not get intercepted when the webview's url is not allowed.
     */
    @Test
    fun testHandleCommandNotAllowed() {
        webViewUrl = "http://not-allowed"
        val url = "uairship://run-actions?action"
        Assert.assertFalse(client.shouldOverrideUrlLoading(webView, url))

        webViewUrl = null
        Assert.assertFalse(client.shouldOverrideUrlLoading(webView, url))
        verify { nativeBridge wasNot Called }
    }

    /**
     * Test onPageFinished loads the js bridge
     */
    @Test
    @SuppressLint("NewApi")
    fun testOnPageFinished() {
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
    fun testOnPageFinishedNotAllowed() {
        webViewUrl = "http://notallowed"
        client.onPageFinished(webView, webViewUrl)
        verify { nativeBridge wasNot Called }
    }

    /**
     * Test close command calls onClose
     */
    @Test
    fun testOnClose() {
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
    fun testOnAirshipCommand() {
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
    fun testHandleCommandJavaScriptExecutor() {
        val url = "uairship://whatever"

        val jsExecutor = slot<JavaScriptExecutor>()

        every { nativeBridge.onHandleCommand(url, capture(jsExecutor), any(), any()) } returns true

        client.shouldOverrideUrlLoading(webView, url)

        // Call close
        jsExecutor.captured.executeJavaScript("cool")
        verify { webView.evaluateJavascript("cool", null) }
    }
}
