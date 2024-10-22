/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.webkit

import android.os.Bundle
import android.webkit.WebView
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.javascript.JavaScriptEnvironment
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.webkit.AirshipWebViewClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.runBlocking

/** A web view client that enables the Airship Native Bridge for Message Center. */
// TODO(m3-message-center): Delete me!
@Deprecated("Replaced with MessageWebViewClient in com.urbanairship.messagecenter.ui.widget.MessageWebView")
public open class MessageWebViewClient : AirshipWebViewClient() {

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun extendActionRequest(
        request: ActionRunRequest,
        webView: WebView
    ): ActionRunRequest {
        val metadata = Bundle()
        val message = getMessage(webView)
        if (message != null) {
            metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, message.id)
        }
        request.setMetadata(metadata)
        return request
    }

    /**
     * @hide
     */
    @CallSuper
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun extendJavascriptEnvironment(
        builder: JavaScriptEnvironment.Builder,
        webView: WebView
    ): JavaScriptEnvironment.Builder {
        val message = getMessage(webView)
        var extras = JsonMap.EMPTY_MAP
        if (message != null) {
            extras = JsonValue.wrapOpt(message.extras).optMap()
        }
        return super.extendJavascriptEnvironment(builder, webView)
            .addGetter("getMessageSentDateMS", message?.sentDate?.time ?: -1)
            .addGetter("getMessageId", message?.id)
            .addGetter("getMessageTitle", message?.title).addGetter(
                "getMessageSentDate",
                if (message != null) DATE_FORMATTER.format(message.sentDate) else null
            ).addGetter("getUserId", MessageCenter.shared().user.id)
            .addGetter("getMessageExtras", extras)
    }

    /**
     * Helper method to get the RichPushMessage from the web view.
     *
     * @param webView The web view.
     * @return The rich push message, or null if the web view does not have an associated message.
     * @note This method should only be called from the main thread.
     */
    @MainThread
    private fun getMessage(webView: WebView): Message? = runBlocking {
        val url = webView.url
        MessageCenter.shared().inbox.getMessageByUrl(url)
    }

    private companion object {

        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
