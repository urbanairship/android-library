/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.R
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.doOnPreDraw
import com.urbanairship.Airship
import com.urbanairship.iam.info.InAppMessageMediaInfo
import com.urbanairship.images.ImageRequestOptions
import com.urbanairship.util.ManifestUtils
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Media View
 */
internal class MediaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defResStyle: Int = 0
): FrameLayout(context, attrs, defStyle, defResStyle) {

    private var webView: WebView? = null
    private var chromeClient: WebChromeClient? = null

    /**
     * Sets the chrome client when loading videos.
     *
     * @param chromeClient The web chrome client.
     */
    fun setChromeClient(chromeClient: WebChromeClient?) {
        this.chromeClient = chromeClient
        webView?.let { it.webChromeClient = chromeClient }
    }

    /**
     * Call during activity pause to pause the media.
     */
    fun onPause() {
        webView?.onPause()
    }

    /**
     * Call during activity resume to resume the media.
     */
    fun onResume() {
        webView?.onResume()
    }

    /**
     * Sets the media info.
     *
     * @param mediaInfo The media info.
     * @param cachedMediaUrl The cached media URL.
     */
    fun setMediaInfo(mediaInfo: InAppMessageMediaInfo, cachedMediaUrl: String?) {
        removeAllViewsInLayout()

        // If we had a web view previously clear it
        webView?.let {
            it.stopLoading()
            it.webChromeClient = null
            it.destroy()
        }
        webView = null

        when (mediaInfo.type) {
            InAppMessageMediaInfo.MediaType.YOUTUBE,
            InAppMessageMediaInfo.MediaType.VIMEO,
            InAppMessageMediaInfo.MediaType.VIDEO -> loadWebView(mediaInfo)
            InAppMessageMediaInfo.MediaType.IMAGE -> {
                val imageView = ImageView(context)
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                imageView.adjustViewBounds = true
                imageView.contentDescription = mediaInfo.description
                val url = cachedMediaUrl ?: mediaInfo.url
                imageView.doOnPreDraw {
                    Airship.shared().getImageLoader().load(
                        context,
                        imageView,
                        ImageRequestOptions.newBuilder(url).build()
                    )
                }

                addView(imageView)
            }
        }
    }

    /**
     * Helper method to load video in the webview.
     *
     * @param mediaInfo The media info.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun loadWebView(mediaInfo: InAppMessageMediaInfo) {
        val webView = WebView(context)
        val frameLayout = FrameLayout(context)
        val webViewLayoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        webViewLayoutParams.gravity = Gravity.CENTER
        frameLayout.addView(webView, webViewLayoutParams)
        val progressBar = ProgressBar(context)
        progressBar.isIndeterminate = true
        progressBar.id = R.id.progress
        val progressBarLayoutParams = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        progressBarLayoutParams.gravity = Gravity.CENTER
        frameLayout.addView(progressBar, progressBarLayoutParams)
        val settings = webView.settings
        settings.mediaPlaybackRequiresUserGesture = true
        settings.javaScriptEnabled = true
        if (ManifestUtils.shouldEnableLocalStorage()) {
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
        }
        settings.apply {
            // Disallow all file and content access, which could pose a security risk if enabled.
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            allowContentAccess = false
        }

        val webViewWeakReference = WeakReference(webView)
        val load = Runnable {
            val local = webViewWeakReference.get() ?: return@Runnable
            if (InAppMessageMediaInfo.MediaType.VIDEO == mediaInfo.type) {
                local.loadData(
                    String.format(Locale.ROOT, VIDEO_HTML_FORMAT, mediaInfo.url),
                    "text/html",
                    "UTF-8"
                )
            } else {
                local.loadUrl(mediaInfo.url)
            }
        }
        webView.webChromeClient = chromeClient
        webView.contentDescription = mediaInfo.description
        webView.visibility = INVISIBLE
        webView.webViewClient = object : MediaWebViewClient(load) {
            override fun onPageFinished(webView: WebView) {
                webView.visibility = VISIBLE
                progressBar.visibility = GONE
            }
        }
        addView(frameLayout)
        load.run()
        this.webView = webView
    }

    private abstract class MediaWebViewClient(private val onRetry: Runnable) : WebViewClient() {

        var error = false
        var retryDelay = START_RETRY_DELAY
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (error) {
                view.postDelayed(onRetry, retryDelay)
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
            super.onReceivedError(view, request, error)
            this.error = true
        }

        protected abstract fun onPageFinished(webView: WebView)

        companion object {
            const val START_RETRY_DELAY: Long = 1000
        }
    }

    companion object {
        private const val VIDEO_HTML_FORMAT =
            "<body style=\"margin:0\"><video playsinline controls height=\"100%%\" width=\"100%%\" src=\"%s\"></video></body>"
    }
}
