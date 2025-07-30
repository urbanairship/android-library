/* Copyright Airship and Contributors */
package com.urbanairship.webkit

import android.R
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.urbanairship.UALog
import com.urbanairship.util.ManifestUtils

/**
 * A web view that sets settings appropriate for Airship content.
 */
public open class AirshipWebView : WebView {

    /**
     * @property webViewClientCompat The web view client.
     */
    public var webViewClientCompat: WebViewClient? = null
        private set

    private var currentClientAuthRequestUrl: String? = null

    /** Flag indicating whether starting safe browsing has been attempted.  */
    private var isStartSafeBrowsingAttempted = false

    /**
     * AirshipWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    @JvmOverloads
    public constructor(
        context: Context, attrs: AttributeSet? = null, defStyle: Int = R.attr.webViewStyle
    ) : super(context, attrs, defStyle) {
        if (!isInEditMode) {
            init(context, attrs, defStyle, 0)
        }
    }

    /**
     * AirshipWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public constructor(
        context: Context, attrs: AttributeSet?, defStyle: Int, defResStyle: Int
    ) : super(context, attrs, defStyle, defResStyle) {
        if (!isInEditMode) {
            init(context, attrs, defStyle, defResStyle)
        }
    }

    /**
     * Helper method that sets the default web view settings for Airship
     * and calls through to initializeView and populateCustomJavascriptInterfaces.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */
    @SuppressLint("NewApi", "SetJavaScriptEnabled")
    private fun init(context: Context, attrs: AttributeSet?, defStyle: Int, defResStyle: Int) {
        val settings = settings

        settings.domStorageEnabled = true

        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(
                attrs, com.urbanairship.R.styleable.AirshipWebView, defStyle, defResStyle
            )
            try {
                val mixedContentMode = a.getInteger(
                    com.urbanairship.R.styleable.AirshipWebView_mixed_content_mode,
                    WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                )
                settings.mixedContentMode = mixedContentMode
            } finally {
                a.recycle()
            }
        }

        // Disallow all file and content access, which could pose a security risk if enabled.
        settings.allowFileAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.allowContentAccess = false

        settings.javaScriptEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        if (ManifestUtils.shouldEnableLocalStorage()) {
            UALog.v("Application contains metadata to enable local storage")
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
        }

        initializeView()
        populateCustomJavascriptInterfaces()
    }

    /**
     * Initializes the web view with any default settings.
     *
     * Called during create.
     */
    protected fun initializeView() { }

    /**
     * Populate any custom javascript interfaces by calling
     * addJavascriptInterface(Object interface, String identifier) for each
     * custom interface.
     *
     * Called after initializeView.
     */
    protected fun populateCustomJavascriptInterfaces() { }

    override fun loadData(data: String, mimeType: String?, encoding: String?) {
        onPreLoad { super@AirshipWebView.loadData(data, mimeType, encoding) }
    }

    override fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?
    ) {
        onPreLoad {
            super@AirshipWebView.loadDataWithBaseURL(
                baseUrl, data, mimeType, encoding, historyUrl
            )
        }
    }

    /**
     * Loads the given URL.
     *
     * @param url The URL of the resource to load.
     */
    override fun loadUrl(url: String) {
        onPreLoad { super@AirshipWebView.loadUrl(url) }
    }

    /**
     * Loads the given URL with the specified additional HTTP headers.
     *
     * @param url The URL to load.
     * @param additionalHttpHeaders The additional headers to be used in the HTTP request for
     * this URL.
     */
    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        onPreLoad { super@AirshipWebView.loadUrl(url, additionalHttpHeaders) }
    }

    override fun setWebViewClient(webViewClient: WebViewClient) {
        if (webViewClient !is AirshipWebViewClient) {
            UALog.w("The web view client should extend AirshipWebViewClient to support Airship url overrides and triggering actions from.")
        }

        this.webViewClientCompat = webViewClient
        super.setWebViewClient(webViewClient)
    }

    /**
     * Called right before data or a URL is passed to the web view to be loaded.
     */
    protected fun onPreLoad(onReadyCallback: Runnable) {
        if (webViewClientCompat == null) {
            UALog.d("No web view client set, setting a default AirshipWebViewClient for landing page view.")
            webViewClient = AirshipWebViewClient()
        }

        // Clear the last set auth request
        val authUrl = currentClientAuthRequestUrl
        if (authUrl != null && webViewClientCompat != null && webViewClientCompat is AirshipWebViewClient) {
            val webViewClient = webViewClientCompat as AirshipWebViewClient?
            webViewClient?.removeAuthRequestCredentials(authUrl)
            currentClientAuthRequestUrl = null
        }

        // Try to start Safe Browsing
        if (!isStartSafeBrowsingAttempted && shouldStartSafeBrowsing()) {
            WebViewCompat.startSafeBrowsing(context.applicationContext) { started ->
                if (!started) {
                    UALog.d("Unable to start Safe Browsing. Feature is not supported or disabled in the manifest.")
                }
                isStartSafeBrowsingAttempted = true
                onReadyCallback.run()
            }
        } else {
            UALog.d("Unable to start Safe Browsing. Feature is not supported or disabled in the manifest.")
            // Safe browsing not supported or disabled, continue loading without it.
            isStartSafeBrowsingAttempted = true
            onReadyCallback.run()
        }
    }

    /**
     * Set the client authorization request.
     *
     * @param url The URL string.
     * @param username The auth user
     * @param password The auth password
     */
    protected fun setClientAuthRequest(url: String?, username: String, password: String) {
        currentClientAuthRequestUrl = url

        val webViewClient = webViewClientCompat as? AirshipWebViewClient ?: return

        Uri.parse(url).host?.let { webViewClient.addAuthRequestCredentials(it, username, password) }
    }

    /**
     * Creates a basic auth string.
     *
     * @param userName The user name.
     * @param password The password.
     * @return The basic auth string.
     */
    protected fun createBasicAuth(userName: String, password: String): String {
        val credentials = "$userName:$password"
        return "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
    }

    private fun shouldStartSafeBrowsing(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)
                && WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)
                && WebSettingsCompat.getSafeBrowsingEnabled(settings)
                && ManifestUtils.isWebViewSafeBrowsingEnabled()
    }
}
