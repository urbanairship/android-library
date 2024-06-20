/* Copyright Airship and Contributors */
package com.urbanairship.iam.adapter.html

import android.net.Uri
import android.webkit.WebView
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.iam.InAppMessageWebViewClient
import com.urbanairship.javascript.NativeBridge
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * A version of the [InAppMessageWebViewClient] for HTML in-app messages, which adds a command
 * for dismissing the message with resolution info represented as URL-encoded JSON.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal abstract class HtmlWebViewClient : InAppMessageWebViewClient {

    /**
     * Default constructor.
     */
    constructor(messageExtras: JsonMap?) : super(messageExtras)

    constructor(nativeBridge: NativeBridge, messageExtras: JsonMap?) : super(nativeBridge, messageExtras)

    /**
     * Called when the dismiss command is invoked from the native bridge. Override to
     * customize the handling of this event.
     *
     * @param argument The argument data passed in the dismiss call.
     */
    abstract fun onMessageDismissed(argument: JsonValue)

    override fun onAirshipCommand(webView: WebView, command: String, uri: Uri) {
        if (command != DISMISS_COMMAND) {
            return
        }

        val path = uri.encodedPath
        if (path == null) {
            UALog.e("Unable to decode message resolution, missing path")
            return
        }

        val components = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (components.size <= 1) {
            UALog.e("Unable to decode message resolution, invalid path")
            return
        }

        try {
            val value = JsonValue.parseString(Uri.decode(components[1]))
            onMessageDismissed(value)
        } catch (e: JsonException) {
            UALog.e("Unable to decode message resolution from JSON.", e)
        }
    }

    companion object {
        /**
         * Close command to handle close method in the Javascript Interface.
         */
        private const val DISMISS_COMMAND = "dismiss"
    }
}
