package com.urbanairship.messagecenter.ui.widget

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.webkit.WebView
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.javascript.JavaScriptEnvironment
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.webkit.AirshipWebView
import com.urbanairship.webkit.AirshipWebViewClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.runBlocking

/** Base WebView configured for Airship Message Center content. */
internal class MessageWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defResStyle: Int = 0
): AirshipWebView(context, attrs, defStyle, defResStyle) {

    /**
     * Loads the web view with the [Message].
     *
     * @param message The message that will be displayed.
     */
    fun loadMessage(message: Message) {
        UALog.v { "Loading message: ${message.messageId}" }
        val user = MessageCenter.shared().user

        // Send authorization in the headers if the web view supports it
        val headers = HashMap<String, String>()

        // Set the auth
        val (userId, password) = user.id to user.password
        if (userId != null && password != null) {
            setClientAuthRequest(message.messageBodyUrl, userId, password)
            headers["Authorization"] = createBasicAuth(userId, password)
        }
        UALog.v { "Load URL: ${message.messageBodyUrl}" }
        loadUrl(message.messageBodyUrl, headers)
    }
}

/** A `WebViewClient` that enables the Airship Native Bridge for Message Center. */
internal open class MessageWebViewClient : AirshipWebViewClient() {

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
            metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, message.messageId)
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
            extras = JsonValue.wrapOpt(message.extrasMap).optMap()
        }
        return super.extendJavascriptEnvironment(builder, webView)
            .addGetter("getMessageSentDateMS", message?.sentDateMS ?: -1)
            .addGetter("getMessageId", message?.messageId)
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
        // TODO(m3-inbox): Use LONG date format and maybe allow a format string to be set via theme attr?
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
