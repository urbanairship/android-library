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
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

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
        viewEnvironment.activityMonitor().addActivityListener(filteredActivityListener)

        val wv = TouchAwareWebView(context)
        webView = wv

        wv.webChromeClient = viewEnvironment.webChromeClientFactory().create()

        val frameLayout = FixedAspectRatioFrameLayout(context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            model.video?.aspectRatio?.let {
                aspectRatio = it.toFloat()
            }
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
                                model.video?.let { if (it.showControls) "controls" else "" },
                                model.video?.let { if (it.autoplay) "autoplay" else "" },
                                model.video?.let { if (it.muted) "muted" else "" },
                                model.video?.let { if (it.loop) "loop" else "" },
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

    class FixedAspectRatioFrameLayout(context: Context) : FrameLayout(context) {

        // Default to a 16:9 aspect ratio
        var aspectRatio = 1.77f

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            val receivedWidth = MeasureSpec.getSize(widthMeasureSpec)
            val receivedHeight = MeasureSpec.getSize(heightMeasureSpec)
            val measuredWidth: Int
            val measuredHeight: Int
            val widthDynamic: Boolean = if (heightMode == MeasureSpec.EXACTLY) {
                if (widthMode == MeasureSpec.EXACTLY) {
                    receivedWidth == 0
                } else {
                    true
                }
            } else if (widthMode == MeasureSpec.EXACTLY) {
                false
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }

            if (widthDynamic) {
                // Width is dynamic.
                val w = (receivedHeight * aspectRatio).toInt()
                measuredWidth = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
                measuredHeight = heightMeasureSpec
            } else {
                // Height is dynamic.
                measuredWidth = widthMeasureSpec
                val h = (receivedWidth / aspectRatio).toInt()
                measuredHeight = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            }

            super.onMeasure(measuredWidth, measuredHeight)
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
