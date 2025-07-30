/* Copyright Airship and Contributors */
package com.urbanairship.webkit

import android.webkit.WebView
import androidx.annotation.RestrictTo
import com.urbanairship.javascript.JavaScriptExecutor
import java.lang.ref.WeakReference

/**
 * @hide
 */
internal class WebViewJavaScriptExecutor(webView: WebView): JavaScriptExecutor {

    private val weakReference = WeakReference(webView)

    override fun executeJavaScript(javaScript: String) {
        weakReference.get()?.evaluateJavascript(javaScript, null)
    }
}
