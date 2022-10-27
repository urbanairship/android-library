/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.R
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.ProgressBar
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.urbanairship.Logger
import com.urbanairship.UAirship
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.WebViewModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.js.UrlAllowList
import com.urbanairship.util.ManifestUtils
import com.urbanairship.webkit.AirshipWebView
import com.urbanairship.webkit.AirshipWebViewClient
import java.lang.ref.WeakReference

/** Web view... view?  */
internal class WebViewView(
    context: Context,
    private val model: WebViewModel,
    private val viewEnvironment: ViewEnvironment
) : FrameLayout(context, null), BaseView {

    private val lifecycleListener: LifecycleObserver = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) {
            webView?.onPause()
        }

        override fun onResume(owner: LifecycleOwner) {
            webView?.onResume()
        }

        override fun onStop(owner: LifecycleOwner) {
            // WebView saved state is meant to work with Activity/Fragment APIs that use bundles.
            // Work around this by stashing state in the model instead. This won't survive process
            // restarts, but will at least restore scroll position for recreates.
            webView?.let {
                val bundle = Bundle()
                it.saveState(bundle)
                model.saveState(bundle)
            }
        }
    }

    private var webView: WebView? = null
    private var chromeClient: WebChromeClient? = null

    init {
        id = model.viewId
        configure()
    }

    private fun configure() {
        viewEnvironment.lifecycle().addObserver(lifecycleListener)
        setChromeClient(viewEnvironment.webChromeClientFactory().create())
        LayoutUtils.applyBorderAndBackground(this, model)
        loadWebView(model)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadWebView(model: WebViewModel) {
        val wv = AirshipWebView(context)
        webView = wv

        // Restore saved state from the model, if available.
        val savedState = model.savedState
        if (savedState != null) {
            wv.restoreState(savedState)
        }
        val frameLayout = FrameLayout(context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        val webViewLayoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        }
        frameLayout.addView(webView, webViewLayoutParams)

        val progressBar = ProgressBar(context).apply {
            id = R.id.progress
            isIndeterminate = true
        }

        val progressBarLayoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
        frameLayout.addView(progressBar, progressBarLayoutParams)

        wv.settings.run {
            javaScriptEnabled = true
            if (ManifestUtils.shouldEnableLocalStorage()) {
                domStorageEnabled = true
                databaseEnabled = true
            }
        }

        val client = viewEnvironment.webViewClientFactory().create().apply {
            addListener(object : ClientListener() {
                override fun onPageFinished(webView: WebView) {
                    webView.visibility = VISIBLE
                    progressBar.visibility = GONE
                }

                override fun onRetry(webView: WebView) {
                    webView.loadUrl(model.url)
                }

                override fun onClose(webView: WebView): Boolean {
                    model.onClose()
                    return true
                }
            })
        }

        wv.run {
            webChromeClient = chromeClient
            visibility = INVISIBLE
            webViewClient = client
        }

        addView(frameLayout)

        if (!UAirship.shared().urlAllowList.isAllowed(model.url, UrlAllowList.SCOPE_OPEN_URL)) {
            Logger.error("URL not allowed. Unable to load: %s", model.url)
            return
        }

        // Load the URL (if we didn't restore with saved state)
        if (savedState == null) {
            wv.loadUrl(model.url)
        }
    }

    /**
     * Sets the chrome client when loading videos.
     *
     * @param chromeClient The web chrome client.
     */
    private fun setChromeClient(chromeClient: WebChromeClient) {
        this.chromeClient = chromeClient
        webView?.webChromeClient = chromeClient
    }

    private abstract class ClientListener : AirshipWebViewClient.Listener {
        private var error = false
        private var retryDelay = START_RETRY_DELAY

        override fun onPageFinished(view: WebView, url: String?) {
            if (error) {
                val webViewWeakReference = WeakReference(view)
                view.postDelayed({
                    val webView = webViewWeakReference.get()
                    webView?.let { onRetry(it) }
                }, retryDelay)
                retryDelay *= 2
            } else {
                onPageFinished(view)
            }
            error = false
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Logger.error("Error loading web view! %d - %s", error.errorCode, error.description)
            } else {
                Logger.error("Error loading web view!")
            }
            this.error = true
        }

        protected abstract fun onPageFinished(webView: WebView)
        protected abstract fun onRetry(webView: WebView)

        companion object {
            private const val START_RETRY_DELAY: Long = 1000
        }
    }
}
