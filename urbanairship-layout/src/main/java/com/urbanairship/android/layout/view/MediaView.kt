/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.isGone
import com.urbanairship.Logger
import com.urbanairship.UAirship
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.MediaModel
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.util.isActionUp
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.android.layout.widget.TouchAwareWebView
import com.urbanairship.app.FilteredActivityListener
import com.urbanairship.app.SimpleActivityListener
import com.urbanairship.images.ImageRequestOptions
import com.urbanairship.js.UrlAllowList
import com.urbanairship.util.ManifestUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Media view.
 *
 * @hide
 */
internal class MediaView(
    context: Context,
    model: MediaModel,
    private val viewEnvironment: ViewEnvironment
) : FrameLayout(context, null), BaseView, TappableView {

    private val activityListener = object : SimpleActivityListener() {
        override fun onActivityPaused(activity: Activity) {
            webView?.onPause()
        }
        override fun onActivityResumed(activity: Activity) {
            webView?.onResume()
        }
    }

    private val filteredActivityListener =
        FilteredActivityListener(activityListener, viewEnvironment.hostingActivityPredicate())

    private var visibilityChangeListener: BaseView.VisibilityChangeListener? = null

    private var webView: TouchAwareWebView? = null
    private var imageView: ImageView? = null

    init {
        LayoutUtils.applyBorderAndBackground(this, model)

        when (model.mediaType) {
            MediaType.IMAGE -> configureImageView(model)
            MediaType.VIDEO,
            MediaType.YOUTUBE -> configureWebView(model)
        }

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@MediaView.isGone = visible
            }
        }
    }

    override fun taps(): Flow<Unit> {
        return webView?.touchEvents()?.filter { it.isActionUp }?.map {}
            ?: imageView?.debouncedClicks()
            // Shouldn't get here, unless models are set up incorrectly and
            // attempting to collect too early.
            ?: emptyFlow<Unit>().also {
                Logger.debug("MediaView.clicks() was collected before child views were ready!")
            }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        visibilityChangeListener?.onVisibilityChanged(visibility)
    }

    private fun configureImageView(model: MediaModel) {
        var url = model.url
        viewEnvironment.imageCache()[url]?.let { cachedImage ->
            url = cachedImage
        }

        if (url.endsWith(".svg")) {
            // Load SVGs in a webview because they won't work in an ImageView
            // TODO: this won't work if the url lacks an extension or if someone
            // gets wacky and the ext doesn't match the actual media type -_-
            configureWebView(model)
            return
        }

        val iv = ImageView(context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            adjustViewBounds = true
            scaleType = model.scaleType
            model.contentDescription.ifNotEmpty { contentDescription = it }
        }
        imageView = iv
        addView(iv)

        var isLoaded = false

        fun loadImage(url: String) {
            // Falling back to the screen dimensions keeps the image as large as possible,
            // while still allowing for sampling to occur.
            val fallbackWidth = ResourceUtils.getDisplayWidthPixels(context)
            val fallbackHeight = ResourceUtils.getDisplayHeightPixels(context)
            val options = ImageRequestOptions.newBuilder(url)
                .setFallbackDimensions(fallbackWidth, fallbackHeight)
                .setImageLoadedCallback { success ->
                    if (success) {
                        isLoaded = true
                    } else if (visibility == View.GONE) {
                        // Listen for visibility changes and load images for default GONE views
                        // once they are made visible (and have a measured size).
                        visibilityChangeListener = object : BaseView.VisibilityChangeListener {
                            override fun onVisibilityChanged(visibility: Int) {
                                if (visibility == View.VISIBLE && !isLoaded) {
                                    loadImage(url)
                                }
                            }
                        }
                    }
                }
                .build()

            UAirship.shared().imageLoader.load(context, iv, options)
        }

        loadImage(url)
    }

    /**
     * Helper method to load a video (or SVG) in the WebView.
     *
     * @param model The media info.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(model: MediaModel) {
        // Default to a 16:9 aspect ratio
        val width = if (model.videoWidth != 0) model.videoWidth else 16
        val height = if (model.videoHeight != 0) model.videoHeight else 9

        // Adjust view bounds if the WebView is displaying a video.
        // SVG images also fall back to loading in a WebView, where we don't want this behavior.
        if (model.mediaType == MediaType.VIDEO) {
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
        }

        viewEnvironment.activityMonitor().addActivityListener(filteredActivityListener)

        val wv = TouchAwareWebView(context)
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
            webViewWeakReference.get()?.let { weakWebView ->
                when (model.mediaType) {
                    MediaType.VIDEO ->
                        weakWebView.loadData(
                            String.format(VIDEO_HTML_FORMAT,
                                if (model.videoControls) "controls" else "",
                                if (model.videoAutoplay) "autoplay" else "",
                                if (model.videoMuted) "muted" else "",
                                if (model.videoLoop) "loop" else "",
                                model.url),
                            "text/html",
                            "UTF-8"
                        )
                    MediaType.IMAGE -> weakWebView.loadData(
                        String.format(IMAGE_HTML_FORMAT, model.url),
                        "text/html",
                        "UTF-8"
                    )
                    else -> weakWebView.loadUrl(model.url)
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
                "<video playsinline %s %s %s %s height=\"100%%\" width=\"100%%\" src=\"%s\"></video>" +
                "</body>"
        private const val IMAGE_HTML_FORMAT = "<body style=\"margin:0\">" +
                "<img height=\"100%%\" width=\"100%%\" src=\"%s\"/>" +
                "</body>"
    }
}
