/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.R
import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.urbanairship.Logger
import com.urbanairship.UAirship
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.MediaModel
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.images.ImageRequestOptions
import com.urbanairship.js.UrlAllowList
import com.urbanairship.util.ManifestUtils
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Media view.
 *
 * @hide
 */
internal class MediaView(
    context: Context,
    private val model: MediaModel,
    private val viewEnvironment: ViewEnvironment
) : FrameLayout(context, null), BaseView {

    private val lifecycleListener: LifecycleObserver = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) {
            webView?.onPause()
        }
        override fun onResume(owner: LifecycleOwner) {
            webView?.onResume()
        }
    }

    private var webView: WebView? = null

    init {
        id = model.viewId
        configure()
    }

    private fun configure() {
        removeAllViewsInLayout()
        LayoutUtils.applyBorderAndBackground(this, model)

        // If we had a web view previously clear it
        webView?.let { wv ->
            wv.stopLoading()
            wv.webChromeClient = null
            wv.destroy()
            webView = null
        }

        when (model.mediaType) {
            MediaType.IMAGE -> configureImage(model)
            MediaType.VIDEO,
            MediaType.YOUTUBE -> configureVideo(model)
        }
    }

    private fun configureImage(model: MediaModel) {
        var url = model.url
        viewEnvironment.imageCache()[url]?.let { cachedImage ->
            url = cachedImage
        }

        if (url.endsWith(".svg")) {
            // Load SVGs in a webview because they won't work in an ImageView
            // TODO: this won't work if the url lacks an extension or if someone
            // gets wacky and the ext doesn't match the actual media type -_-
            configureVideo(model)
            return
        }

        val imageView = ImageView(context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            adjustViewBounds = true
            scaleType = model.scaleType
            model.contentDescription.ifNotEmpty { contentDescription = it }
        }
        addView(imageView)

        // Falling back to the screen dimensions keeps the image as large as possible,
        // while still allowing for sampling to occur.
        val fallbackWidth = ResourceUtils.getDisplayWidthPixels(context)
        val fallbackHeight = ResourceUtils.getDisplayHeightPixels(context)
        val options = ImageRequestOptions.newBuilder(url)
            .setFallbackDimensions(fallbackWidth, fallbackHeight)
            .build()
        UAirship.shared().imageLoader
            .load(context, imageView, options)
    }

    /**
     * Helper method to load video in the webview.
     *
     * @param model The media info.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureVideo(model: MediaModel) {
        // Default to a 16:9 aspect ratio
        val width = 16
        val height = 9

        viewTreeObserver.addOnGlobalLayoutListener {
            val params = layoutParams
            // Check if we can grow the image horizontally to fit the width
            if (params.height == WRAP_CONTENT) {
                val scale = getWidth().toFloat() / width.toFloat()
                params.height = (scale * height).roundToInt()
            } else {
                val imageRatio = width.toFloat() / height.toFloat()
                val viewRatio = getWidth().toFloat() / getHeight()
                if (imageRatio >= viewRatio) {
                    // Image is wider than the view
                    params.height = (getWidth() / imageRatio).roundToInt()
                } else {
                    // View is wider than the image
                    val w = (getHeight() * imageRatio).roundToInt()
                    params.width = if (w > 0) w else MATCH_PARENT
                }
            }
            layoutParams = params
        }

        viewEnvironment.lifecycle().addObserver(lifecycleListener)

        val wv = WebView(context)
        webView = wv

        wv.webChromeClient = viewEnvironment.webChromeClientFactory().create()

        val frameLayout = FrameLayout(context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        val webViewLayoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        }
        frameLayout.addView(webView, webViewLayoutParams)

        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            id = R.id.progress
        }
        val progressBarLayoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
        frameLayout.addView(progressBar, progressBarLayoutParams)

        wv.settings.apply {
            if (model.mediaType == MediaType.VIDEO) {
                mediaPlaybackRequiresUserGesture = true
            }

            javaScriptEnabled = true

            if (ManifestUtils.shouldEnableLocalStorage()) {
                domStorageEnabled = true
                databaseEnabled = true
            }
        }

        val webViewWeakReference = WeakReference(wv)
        val load = Runnable {
           webViewWeakReference.get()?.let {
               when (model.mediaType) {
                   MediaType.VIDEO -> it.loadData(
                       String.format(VIDEO_HTML_FORMAT, model.url), "text/html", "UTF-8"
                   )
                   MediaType.IMAGE -> it.loadData(
                       String.format(IMAGE_HTML_FORMAT, model.url), "text/html", "UTF-8"
                   )
                   else -> it.loadUrl(model.url)
               }
           }
        }

        model.contentDescription.ifNotEmpty { wv.contentDescription = it }

        wv.visibility = INVISIBLE
        wv.webViewClient = object : MediaWebViewClient(load) {
            override fun onPageFinished(webView: WebView) {
                webView.visibility = VISIBLE
                progressBar.visibility = GONE
            }
        }

        addView(frameLayout)

        if (!UAirship.shared().urlAllowList.isAllowed(model.url, UrlAllowList.SCOPE_OPEN_URL)) {
            Logger.error("URL not allowed. Unable to load: $model.url")
            return
        }

        load.run()
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
            private const val START_RETRY_DELAY: Long = 1000
        }
    }

    companion object {
        private const val VIDEO_HTML_FORMAT = "<body style=\"margin:0\">" +
                "<video playsinline controls height=\"100%%\" width=\"100%%\" src=\"%s\"></video>" +
                "</body>"
        private const val IMAGE_HTML_FORMAT = "<body style=\"margin:0\">" +
                "<img height=\"100%%\" width=\"100%%\" src=\"%s\"/>" +
                "</body>"
    }
}
