/* Copyright Airship and Contributors */
package com.urbanairship.iam

import android.webkit.WebView
import androidx.annotation.CallSuper
import com.urbanairship.javascript.JavaScriptEnvironment
import com.urbanairship.javascript.NativeBridge
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.webkit.AirshipWebViewClient

/**
 * AirshipWebViewClient that injects the messages extras in the native bridge.
 */
internal open class InAppMessageWebViewClient: AirshipWebViewClient {

    private val messageExtras: JsonMap?

    constructor(messageExtras: JsonMap?) : super() {
        this.messageExtras= messageExtras
    }

    constructor(nativeBridge: NativeBridge, extras: JsonMap?) : super(nativeBridge) {
        messageExtras = extras
    }

    @CallSuper
    override fun extendJavascriptEnvironment(
        builder: JavaScriptEnvironment.Builder,
        webView: WebView
    ): JavaScriptEnvironment.Builder {
        return super.extendJavascriptEnvironment(builder, webView)
            .addGetter("getMessageExtras", messageExtras ?: JsonValue.NULL)
    }
}
