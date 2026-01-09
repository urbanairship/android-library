/* Copyright Airship and Contributors */
package com.urbanairship.webkit

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
import androidx.annotation.RestrictTo
import com.urbanairship.Cancelable
import com.urbanairship.R
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.UrlAllowList
import com.urbanairship.actions.ActionCompletionCallback
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionRunRequestExtender
import com.urbanairship.javascript.JavaScriptEnvironment
import com.urbanairship.javascript.NativeBridge
import com.urbanairship.javascript.NativeBridge.CommandDelegate
import java.io.BufferedInputStream
import java.util.Locale
import java.util.WeakHashMap

/**
 * A web view client that enables the Airship Native Bridge on allowed URLs.
 */
public open class AirshipWebViewClient

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal constructor(
    private val nativeBridge: NativeBridge,
    private val allowListProvider: () -> UrlAllowList,
    private val javaScriptExtender: (JavaScriptEnvironment.Builder) -> JavaScriptEnvironment.Builder
) : WebViewClient() {

    public constructor(nativeBridge: NativeBridge) : this(
        nativeBridge,
        allowListProvider = { Airship.urlAllowList },
        javaScriptExtender = { builder ->
            builder.addGetter("getDeviceModel", Build.MODEL)
                .addGetter("getChannelId", Airship.channel.id)
                .addGetter("getAppKey", Airship.airshipConfigOptions.appKey)
                .addGetter("getNamedUser", Airship.contact.namedUserId)
        }
    )


    private val authRequestCredentials = mutableMapOf<String, Credentials>()
    private val pendingNativeBridgeLoads: MutableMap<WebView, Cancelable> = WeakHashMap()

    private var faviconEnabled = false

    /**
     * WebView client listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface Listener {
        public fun onPageFinished(view: WebView, url: String?)
        public fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError)
        public fun onClose(view: WebView): Boolean
    }

    private val listeners = mutableListOf<Listener>()

    /**
     * Default constructor.
     */
    public constructor() : this(NativeBridge())

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)

        if (view == null || request == null || error == null) {
            return
        }

        // onReceivedError is called for any resource on the page,
        // but we only want to forward main frame errors to listeners.
        // This matches the behavior of the deprecated onReceivedError
        // method that we were previously overriding.
        if (request.isForMainFrame) {
            for (listener in listeners) {
                listener.onReceivedError(view, request, error)
            }
        }
    }

    /**
     * Called to extend the action request for actions run through the native bridge.
     *
     * @param request The request
     * @param webView The web view.
     * @return The action run request.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected open fun extendActionRequest(request: ActionRunRequest, webView: WebView): ActionRunRequest {
        return request
    }

    /**
     * Called to extend the JavaScript environment.
     *
     * @param builder The environment builder.
     * @param webView The web view.
     * @return The builder.
     * @hide
     */
    @CallSuper
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected open fun extendJavascriptEnvironment(builder: JavaScriptEnvironment.Builder, webView: WebView): JavaScriptEnvironment.Builder {
        return javaScriptExtender(builder)
    }

    /**
     * Called when a custom uairship:// command is intercepted.
     *
     * @param webView The web view.
     * @param command The command (or host).
     * @param uri The full uri in the shape of `uairship://<COMMAND>*`.
     * @hide
    </COMMAND> */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open fun onAirshipCommand(webView: WebView, command: String, uri: Uri) { }

    /**
     * Called when Airship.close() is triggered from the Airship Javascript interface.
     *
     * The default behavior simulates a back key press.
     *
     * @param webView The web view.
     */
    public open fun onClose(webView: WebView) {
        var handled = false
        for (listener in listeners) {
            handled = handled || listener.onClose(webView)
        }

        if (!handled) {
            webView.rootView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            webView.rootView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
    }

    /**
     * Sets the action completion callback to be invoked whenever an [com.urbanairship.actions.Action]
     * is finished running from the web view.
     *
     * @param actionCompletionCallback The completion callback.
     */
    public fun setActionCompletionCallback(actionCompletionCallback: ActionCompletionCallback?) {
        nativeBridge.setActionCompletionCallback(actionCompletionCallback)
    }

    // TODO: Switch to the non-deprecated version when min SDK is 24+
    //  shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
    @Suppress("OVERRIDE_DEPRECATION")
    @CallSuper
    override fun shouldOverrideUrlLoading(webView: WebView, url: String?): Boolean {
        if (interceptUrl(webView, url)) {
            return true
        }

        @Suppress("DEPRECATION")
        return super.shouldOverrideUrlLoading(webView, url)
    }

    /**
     * Sets favicon enabled flag.
     *
     * @param enabled `true` to enable favicon, `false` to disable and intercept favicon request.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun setFaviconEnabled(enabled: Boolean) {
        faviconEnabled = enabled
    }

    /**
     * Intercepts the favicon request and returns a blank favicon
     *
     * @param webView The web view.
     * @param url The url being loaded.
     * @return The blank favicon image embedded in a WebResourceResponse or null if the url does not contain a favicon.
     */
    @Deprecated("Deprecated in Java")
    @CallSuper
    override fun shouldInterceptRequest(webView: WebView, url: String): WebResourceResponse? {
        if (faviconEnabled) {
            return null
        }

        if (url.lowercase(Locale.getDefault()).endsWith("/favicon.ico")) {
            return generateEmptyFaviconResponse(webView)
        }

        return null
    }

    /**
     * Intercepts the favicon request and returns blank favicon
     *
     * @param webView The web view.
     * @param request The WebResourceRequest being loaded.
     * @return The blank favicon image embedded in a WebResourceResponse or null if the url does not contain a favicon.
     */
    @CallSuper
    @SuppressLint("NewApi")
    override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (faviconEnabled) {
            return super.shouldInterceptRequest(webView, request)
        }

        if (!request.isForMainFrame) {
            val path = request.url.path
            if (path != null && path.endsWith("/favicon.ico")) {
                return generateEmptyFaviconResponse(webView)
            }
        }

        return super.shouldInterceptRequest(webView, request)
    }

    @CallSuper
    override fun onLoadResource(webView: WebView, url: String?) {/*
         * Sometimes shouldOverrideUrlLoading is not called when the uairship library is ready for whatever reasons,
         * but once shouldOverrideUrlLoading is called and returns true it will prevent onLoadResource from
         * being called with the url.
         */
        interceptUrl(webView, url)
    }

    /**
     * Intercepts a url for our JS bridge.
     *
     * @param webView The web view.
     * @param url The url being loaded.
     * @return `true` if the url was loaded, otherwise `false`.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    private fun interceptUrl(webView: WebView, url: String?): Boolean {
        if (!isAllowed(webView.url)) {
            return false
        }

        val javaScriptExecutor = WebViewJavaScriptExecutor(webView)

        val extender = ActionRunRequestExtender { request ->
            this@AirshipWebViewClient.extendActionRequest(request, webView)
        }

        val delegate = object : CommandDelegate {
            override fun onClose() {
                this@AirshipWebViewClient.onClose(webView)
            }

            override fun onAirshipCommand(command: String, uri: Uri) {
                this@AirshipWebViewClient.onAirshipCommand(webView, command, uri)
            }
        }

        return nativeBridge.onHandleCommand(url, javaScriptExecutor, extender, delegate)
    }

    private fun generateEmptyFaviconResponse(webView: WebView): WebResourceResponse? {
        try {
            return WebResourceResponse(
                "image/png", null, BufferedInputStream(
                    webView.context.resources.openRawResource(
                        R.raw.ua_blank_favicon
                    )
                )
            )
        } catch (e: Exception) {
            UALog.e(e, "Failed to read blank favicon with IOException.")
        }

        return null
    }

    @CallSuper
    override fun onPageFinished(view: WebView?, url: String?) {
        if (view == null) {
            return
        }

        for (listener in listeners) {
            listener.onPageFinished(view, url)
        }

        if (!isAllowed(url)) {
            UALog.d("%s is not an allowed URL. Airship Javascript interface will not be accessible.", url)
            return
        }

        val environment = extendJavascriptEnvironment(JavaScriptEnvironment.newBuilder(), view)

        val cancelable = nativeBridge.loadJavaScriptEnvironment(
            view.context, environment.build(), WebViewJavaScriptExecutor(view)
        )

        pendingNativeBridgeLoads[view] = cancelable
    }

    @CallSuper
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        val cancelable = pendingNativeBridgeLoads[view]
        cancelable?.cancel()
    }

    /**
     * Checks if the URL is allowed.
     *
     * @param url The URL being loaded.
     * @return `true` if the URL is allowed, otherwise `false`.
     */
    protected fun isAllowed(url: String?): Boolean {
        if (url == null) { return false }
        return allowListProvider().isAllowed(url, UrlAllowList.Scope.JAVASCRIPT_INTERFACE)
    }

    @CallSuper
    override fun onReceivedHttpAuthRequest(
        view: WebView, handler: HttpAuthHandler, host: String?, realm: String?
    ) {
        val credentials = authRequestCredentials[host] ?: return
        handler.proceed(credentials.username, credentials.password)
    }

    /**
     * Adds auth request credentials for a host.
     *
     * @param expectedAuthHost The expected host.
     * @param username The auth user.
     * @param password The auth password.
     */
    public fun addAuthRequestCredentials(
        expectedAuthHost: String, username: String?, password: String?
    ) {
        authRequestCredentials[expectedAuthHost] = Credentials(username, password)
    }

    /**
     * Removes auth request credentials for a host.
     *
     * @param expectedAuthHost The expected host.
     */
    public fun removeAuthRequestCredentials(expectedAuthHost: String) {
        authRequestCredentials.remove(expectedAuthHost)
    }

    /**
     * Adds a listener.
     * @param listener The listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    /**
     * Removes a listener.
     * @param listener The listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Credentials model class.
     */
    private data class Credentials(val username: String?, val password: String?)
}
