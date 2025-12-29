package com.urbanairship.messagecenter.ui.widget

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.webkit.WebView
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.javascript.JavaScriptEnvironment
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.messageCenter
import com.urbanairship.webkit.AirshipWebViewClient
import com.urbanairship.webkit.NestedScrollAirshipWebView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.runBlocking

/**
 * Base WebView configured for Airship Message Center content.
 */
public open class MessageWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defResStyle: Int = 0
): NestedScrollAirshipWebView(context, attrs, defStyle, defResStyle) {

    /**
     * Loads the web view with the [Message].
     *
     * @param message The message that will be displayed.
     */
    public open fun loadMessage(message: Message) {
        UALog.v { "Loading message: ${message.id}" }
        val user = Airship.messageCenter.user

        // Send authorization in the headers if the web view supports it
        val headers = HashMap<String, String>()

        // Set the auth
        val (userId, password) = user.id to user.password
        if (userId != null && password != null) {
            setClientAuthRequest(message.bodyUrl, userId, password)
            headers["Authorization"] = createBasicAuth(userId, password)
        }
        UALog.v { "Load URL: ${message.bodyUrl}" }
        loadUrl(message.bodyUrl, headers)
    }
}

/**
 * A `WebViewClient` that enables the Airship Native Bridge for Message Center.
 */
public open class MessageWebViewClient : AirshipWebViewClient() {

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

    @CallSuper
    override fun extendJavascriptEnvironment(
        builder: JavaScriptEnvironment.Builder,
        webView: WebView
    ): JavaScriptEnvironment.Builder {
        val message = getMessage(webView)
        val extras = message?.extras?.let { JsonValue.wrapOpt(it).optMap() } ?: JsonMap.EMPTY_MAP
        val formattedSentDate = message?.sentDate?.let { DATE_FORMATTER.format(it) }

        return super.extendJavascriptEnvironment(builder, webView)
            .addGetter("getMessageSentDateMS", message?.sentDate?.time ?: -1)
            .addGetter("getMessageId", message?.id)
            .addGetter("getMessageTitle", message?.title)
            .addGetter("getMessageSentDate", formattedSentDate)
            .addGetter("getUserId", Airship.messageCenter.user.id)
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
        Airship.messageCenter.inbox.getMessageByUrl(url)
    }

    private companion object {
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
