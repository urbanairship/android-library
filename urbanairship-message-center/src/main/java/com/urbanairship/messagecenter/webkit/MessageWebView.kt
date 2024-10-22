package com.urbanairship.messagecenter.webkit

import android.content.Context
import android.util.AttributeSet
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.webkit.AirshipWebView

/** A web view that sets settings appropriate for Airship message center content. */
// TODO(m3-message-center): Delete me!
@Deprecated("Replaced with com.urbanairship.messagecenter.ui.widget.MessageWebView")
public open class MessageWebView @JvmOverloads constructor(
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
    public open fun loadMessage(message: Message) {
        val user = MessageCenter.shared().user

        // Send authorization in the headers if the web view supports it
        val headers = HashMap<String, String>()

        // Set the auth
        val (userId, password) = user.id to user.password
        if (userId != null && password != null) {
            setClientAuthRequest(message.bodyUrl, userId, password)
            headers["Authorization"] = createBasicAuth(userId, password)
        }
        loadUrl(message.bodyUrl, headers)
    }
}
