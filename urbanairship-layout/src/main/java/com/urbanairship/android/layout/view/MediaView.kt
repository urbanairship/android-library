/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.model.MediaModel
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.MediaFit
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.property.Video
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.ThomasImageSizeResolver
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.util.isActionUp
import com.urbanairship.android.layout.widget.CropImageView
import com.urbanairship.android.layout.widget.ShrinkableView
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.android.layout.widget.TouchAwareWebView
import com.urbanairship.app.FilteredActivityListener
import com.urbanairship.app.SimpleActivityListener
import com.urbanairship.images.ImageRequestOptions
import com.urbanairship.util.ManifestUtils
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.intellij.lang.annotations.Language

/**
 * Media view.
 *
 * @hide
 */
internal class MediaView(
    context: Context,
    model: MediaModel,
    private val viewEnvironment: ViewEnvironment,
    private val itemProperties: ItemProperties?,
) : FrameLayout(context, null), BaseView, TappableView, ShrinkableView {

    private val activityListener = object : SimpleActivityListener() {
        override fun onActivityPaused(activity: Activity) {
            model.sendEvent(MediaModel.PlaybackEvent.BackgroundPause)
            webView?.onPause()
        }
        override fun onActivityResumed(activity: Activity) {
            webView?.onResume()
            model.sendEvent(MediaModel.PlaybackEvent.BackgroundResume)
        }
    }

    internal class WebViewListener(private val model: MediaModel) {
        var isVideoReady: Boolean = false
            private set
        var errorHandler: ((errorCode: Int, description: String?) -> Unit)? = null

        fun onVideoReady(playing: Boolean, muted: Boolean) {
            isVideoReady = true
            model.sendEvent(MediaModel.PlaybackEvent.VideoReady(playing, muted))
        }
        fun onVideoPlay() = model.sendEvent(MediaModel.PlaybackEvent.JsPlay)
        fun onVideoPause() = model.sendEvent(MediaModel.PlaybackEvent.JsPause)
        fun onVideoEnded() = model.sendEvent(MediaModel.PlaybackEvent.VideoEnded)
        fun onVideoError(errorCode: Int, description: String?) {
            errorHandler?.invoke(errorCode, description)
        }
        fun onVisibilityChanged(isVisible: Boolean) = model.sendEvent(MediaModel.PlaybackEvent.VisibilityChanged(isVisible))
    }

    private val filteredActivityListener =
        FilteredActivityListener(activityListener, viewEnvironment.hostingActivityPredicate())

    private var visibilityChangeListener: BaseView.VisibilityChangeListener? = null
    private var webView: TouchAwareWebView? = null
    private var imageView: CropImageView? = null
    private var newBackground: Background? = null
    private var currentState: ThomasState? = null
    private var lastMediaUrl: String? = null
    private var webContentLoader: Runnable? = null
    private var imageContentLoader: ((String) -> Unit)? = null

    val isNonInteractiveVideo: Boolean =
        model.viewInfo.mediaType.isPlayable &&
                !model.viewInfo.eventHandlers.hasTapHandler() &&
                !(model.viewInfo.video ?: Video.defaultVideo()).showControls

    init {
        id = model.viewId

        clipToOutline = true

        when (model.viewInfo.mediaType) {
            MediaType.IMAGE -> configureImageView(model)
            MediaType.VIDEO,
            MediaType.VIMEO,
            MediaType.YOUTUBE -> {
                model.setMediaLoading(true)
                configureWebView(model)
            }
        }

        model.listener = object : MediaModel.Listener {
            private fun evalJs(script: String) {
                UALog.v { "MediaView[${model.videoId}] evaluateJavascript: $script" }
                webView?.evaluateJavascript(script) { result ->
                    UALog.v { "MediaView[${model.videoId}] evaluateJavascript '$script' result: $result" }
                }
            }

            override fun onPause() {
                val script = when (model.viewInfo.mediaType) {
                    MediaType.VIDEO -> "videoElement.pause();"
                    MediaType.YOUTUBE -> "if (player && player.pauseVideo) player.pauseVideo();"
                    MediaType.VIMEO -> "vimeoPlayer.pause();"
                    else -> null
                }
                script?.let { evalJs(it) }
            }

            override fun onResume() {
                val script = when (model.viewInfo.mediaType) {
                    MediaType.VIDEO -> "videoElement.play();"
                    MediaType.YOUTUBE -> "if (player && player.playVideo) player.playVideo();"
                    MediaType.VIMEO -> "vimeoPlayer.play();"
                    else -> null
                }
                script?.let { evalJs(it) }
            }

            override fun onMute() {
                val script = when (model.viewInfo.mediaType) {
                    MediaType.VIDEO -> "videoElement.muted = true;"
                    MediaType.YOUTUBE -> "if (player && player.mute) player.mute();"
                    MediaType.VIMEO -> "vimeoPlayer.setVolume(0);"
                    else -> null
                }
                script?.let { evalJs(it) }
            }

            override fun onUnmute() {
                val script = when (model.viewInfo.mediaType) {
                    MediaType.VIDEO -> "videoElement.muted = false;"
                    MediaType.YOUTUBE -> "if (player && player.unMute) player.unMute();"
                    MediaType.VIMEO -> "vimeoPlayer.setVolume(1);"
                    else -> null
                }
                script?.let { evalJs(it) }
            }

            override fun onSeekToBeginning() {
                val script = when (model.viewInfo.mediaType) {
                    MediaType.VIDEO -> "videoElement.currentTime = 0;"
                    MediaType.YOUTUBE -> "if (player && player.seekTo) player.seekTo(0, false);"
                    MediaType.VIMEO -> "vimeoPlayer.setCurrentTime(0);"
                    else -> null
                }
                script?.let { evalJs(it) }
            }

            override fun setVisibility(visible: Boolean) {
                this@MediaView.isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@MediaView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                webView?.let {
                    LayoutUtils.applyMediaVideoBorderAndBackground(this@MediaView, it.background, new.border, new.color)
                } ?: imageView?.let {
                    // Image views are inflated onAttach, so we aren't likely to hit this case.
                    LayoutUtils.applyMediaImageBorderAndBackground(it, it.background, new.border, new.color)
                } ?: run {
                    // If neither child is ready, store the background to be applied later.
                    newBackground = new
                }
            }

            override fun onStateUpdated(state: ThomasState) {
                currentState = state
                val isDarkMode = ResourceUtils.isUiModeNight(context)
                val newUrl = model.resolveUrl(state, isDarkMode)
                if (newUrl == lastMediaUrl) return
                lastMediaUrl = newUrl
                val cached = viewEnvironment.imageCache()?.get(newUrl)
                imageContentLoader?.invoke(cached?.path ?: newUrl)
                    ?: webContentLoader?.run()
            }
        }
    }

    override fun taps(): Flow<Unit> {
        return webView?.touchEvents()?.filter { it.isActionUp }?.map {}
            ?: imageView?.debouncedClicks()
            // Shouldn't get here, unless models are set up incorrectly and
            // attempting to collect too early.
            ?: emptyFlow<Unit>().also {
                UALog.d("MediaView.clicks() was collected before child views were ready!")
            }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        visibilityChangeListener?.onVisibilityChanged(visibility)
    }

    /** Media views are always shrinkable. */
    override fun isShrinkable(): Boolean = true

    private fun configureImageView(model: MediaModel) {
        val resolvedUrl = model.resolveUrl(currentState, ResourceUtils.isUiModeNight(context))
        lastMediaUrl = resolvedUrl
        val cached = viewEnvironment.imageCache()?.get(resolvedUrl)
        val url = cached?.path ?: resolvedUrl

        if (url.endsWith(".svg")) {
            // Load SVGs in a webview because they won't work in an ImageView
            // TODO: this won't work if the url lacks an extension or if someone
            // gets wacky and the ext doesn't match the actual media type -_-
            configureWebView(model)
            return
        }

        doOnAttach {
            val parentLayoutParams = layoutParams

            val iv = CropImageView(context).apply {
                id = model.mediaViewId
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                adjustViewBounds = true
                clipToOutline = true

                // Apply background and border, if available.
                newBackground?.let {
                    LayoutUtils.applyMediaImageBorderAndBackground(this, this.background, model.viewInfo.border, model.viewInfo.backgroundColor)
                }

                if (model.viewInfo.mediaFit == MediaFit.FIT_CROP) {
                    // Use parent size and a matrix to crop the image.
                    setParentLayoutParams(parentLayoutParams)
                    setImagePosition(model.viewInfo.position)
                } else {
                    // Use ImageView scaleType to fit the image.
                    scaleType = model.viewInfo.mediaFit.scaleType
                }

                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                model.contentDescription(context).ifNotEmpty {
                    contentDescription = it
                    if (model.viewInfo.accessibilityHidden != true) {
                        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                    }
                }
            }
            imageView = iv
            addView(iv)

            var isLoaded = false

            fun loadImage(url: String) {
                val options = ImageRequestOptions.newBuilder(url)
                    .setImageSizeResolver(ThomasImageSizeResolver(itemProperties?.size, cached?.size))
                    .setImageLoadedCallback { success ->
                        if (success) {
                            isLoaded = true
                        } else {
                            // Listen for visibility changes and load images for default GONE views
                            // once they are made visible (and have a measured size).
                            visibilityChangeListener = object : BaseView.VisibilityChangeListener {
                                override fun onVisibilityChanged(visibility: Int) {
                                    if (visibility == VISIBLE && !isLoaded) {
                                        loadImage(url)
                                    }
                                }
                            }
                        }
                    }
                    .build()

                Airship.imageLoader.load(context, iv, options)
            }

            imageContentLoader = { newUrl -> loadImage(newUrl) }
            loadImage(url)
        }
    }


    /**
     * Helper method to load a video (or SVG) in the WebView.
     *
     * @param model The media info.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(model: MediaModel) {
        viewEnvironment.activityMonitor().addActivityListener(filteredActivityListener)

        val webViewListener = WebViewListener(model)

        val wv = TouchAwareWebView(context, webViewListener).apply {
            id = model.mediaViewId
        }
        webView = wv

        wv.webChromeClient = viewEnvironment.webChromeClientFactory().create()
        wv.addJavascriptInterface(wv.getJavascriptInterface(), "VideoListenerInterface")
        val frameLayout = when (model.viewInfo.mediaType) {
            // Adjust the aspect ratio of the WebView if the media is video or youtube.
            MediaType.VIDEO -> FixedAspectRatioFrameLayout(context).apply {
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
                    gravity = Gravity.CENTER
                }

                doOnAttach {
                    val params = this@MediaView.layoutParams
                    val isWrapWidth = params.width == WRAP_CONTENT
                    val isWrapHeight = params.height == WRAP_CONTENT

                    if (isWrapWidth || isWrapHeight) {
                        // If either dimension is wrap_content, the aspect ratio will be adjusted
                        // based on the video's aspect ratio.
                        model.viewInfo.video?.aspectRatio?.let {
                            aspectRatio = it.toFloat()
                        }
                        shouldEnforceAspectRatio = true
                    } else {
                        // If the width and height are known, we don't need to fix to the aspect
                        // ratio of the video.
                        aspectRatio = model.viewInfo.video?.aspectRatio?.toFloat() ?: 1.77f
                        shouldEnforceAspectRatio = false
                    }
                }
            }
            MediaType.YOUTUBE,
            MediaType.VIMEO -> FixedAspectRatioFrameLayout(context).apply {
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
                    gravity = Gravity.CENTER
                }
                model.viewInfo.video?.aspectRatio?.let {
                    aspectRatio = it.toFloat()
                }
            }
            // SVG images fall back to loading in a WebView, where we don't want to adjust the
            // aspect ratio.
            MediaType.IMAGE -> FrameLayout(context).apply {
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
                    gravity = Gravity.CENTER
                }
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
        wv.setBackgroundColor(Color.TRANSPARENT)

        @Suppress("DEPRECATION")
        wv.settings.apply {
            mediaPlaybackRequiresUserGesture = false

            javaScriptEnabled = true

            if (ManifestUtils.shouldEnableLocalStorage(context)) {
                domStorageEnabled = true
                databaseEnabled = true
            }

            // Disallow all file and content access, which could pose a security risk if enabled.
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            allowContentAccess = false
        }

        val webViewWeakReference = WeakReference(wv)
        val load = Runnable {
            webViewWeakReference.get()?.let { weakWebView ->
                val resolvedUrl = model.resolveUrl(currentState, ResourceUtils.isUiModeNight(context))
                lastMediaUrl = resolvedUrl
                when (model.viewInfo.mediaType) {
                    MediaType.VIDEO -> {
                        val video = model.viewInfo.video ?: Video.defaultVideo()
                        val videoId = model.videoId
                        val videoSnapshot = model.videoState?.changes?.value
                        val currentVideoState = videoSnapshot?.videos?.get(videoId)
                            ?: model.groupPlaying?.let { playing ->
                                State.Video.VideoMediaState(
                                    playing = playing,
                                    muted = model.groupMuted ?: video.muted
                                )
                            }

                        val muted = currentVideoState?.muted ?: video.muted

                        weakWebView.loadData(
                            String.format(
                                VIDEO_HTML_FORMAT,
                                if (video.showControls) "controls" else "",
                                if (video.autoplay) "autoplay" else "",
                                if (muted) "muted" else "",
                                if (video.loop) "loop" else "",
                                resolvedUrl,
                                model.videoStyle
                            ),
                            "text/html",
                            "UTF-8"
                        )
                    }
                    MediaType.IMAGE -> weakWebView.loadData(
                        String.format(IMAGE_HTML_FORMAT, resolvedUrl),
                        "text/html",
                        "UTF-8"
                    )
                    MediaType.YOUTUBE -> {
                        val video = model.viewInfo.video ?: Video.defaultVideo()
                        val muted = model.groupMuted ?: video.muted
                        val ytVideoId = YOUTUBE_ID_RE.find(resolvedUrl)?.groupValues?.get(1)
                        ytVideoId?.let {
                            weakWebView.loadDataWithBaseURL(
                                "https://" + this.context.packageName,
                                String.format(YOUTUBE_HTML_FORMAT,
                                    it,
                                    if (video.showControls) "1" else "0",
                                    if (video.autoplay) "1" else "0",
                                    if (muted) "1" else "0",
                                    if (video.loop) {
                                        "1, \'playlist\': \'$it\'"
                                    } else {
                                        "0"
                                    }
                                ),
                                "text/html",
                                "UTF-8",
                                null
                            )
                        } ?: weakWebView.loadUrl(resolvedUrl)
                    }
                    MediaType.VIMEO -> {
                        weakWebView.loadData(
                            String.format(
                                VIMEO_HTML_FORMAT,
                                resolvedUrl
                            ), "text/html", "UTF-8"
                        )
                    }
                }
            }
        }

        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

        model.contentDescription(context).ifNotEmpty {
            wv.contentDescription = it
            if (model.viewInfo.accessibilityHidden != true) {
                wv.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            }
        }

        wv.visibility = INVISIBLE
        wv.webViewClient = object : MediaWebViewClient(load) {
            override fun onPageFinished(webView: WebView) {
                webView.visibility = VISIBLE
                progressBar.visibility = GONE
            }
        }

        // Retry failed video loads with backoff. Errors after playback became ready are
        // logged but ignored, so a transient mid-playback error can't tear down a
        // working player.
        var videoErrorRetryCount = 0
        webViewListener.errorHandler = { errorCode, description ->
            if (webViewListener.isVideoReady) {
                UALog.v { "MediaView[${model.videoId}] video error after ready, ignoring: code=$errorCode, description=$description" }
            } else if (videoErrorRetryCount < MAX_VIDEO_ERROR_RETRIES) {
                val delay = VIDEO_ERROR_RETRY_DELAY_MS shl videoErrorRetryCount
                videoErrorRetryCount++
                UALog.v { "MediaView[${model.videoId}] video failed to load (code=$errorCode, description=$description), retrying in ${delay}ms" }
                wv.postDelayed(load, delay)
            } else {
                UALog.w { "MediaView[${model.videoId}] video failed to load after $MAX_VIDEO_ERROR_RETRIES retries: code=$errorCode, description=$description" }
            }
        }

        addView(frameLayout)
        webContentLoader = load
        load.run()
    }


    private val MediaModel.videoStyle: String
        @Suppress("DEPRECATION")
        get() = when (viewInfo.mediaFit) {
            MediaFit.CENTER -> "object-fit: none;"
            MediaFit.CENTER_INSIDE -> "object-fit: contain;"
            MediaFit.CENTER_CROP -> "object-fit: cover;"
            MediaFit.FIT_CROP -> {
                val isRtl = LAYOUT_DIRECTION_RTL == layoutDirection
                val horizontal = when (viewInfo.position.horizontal) {
                    HorizontalPosition.START -> if (isRtl) "right" else "left"
                    HorizontalPosition.END -> if (isRtl) "left" else "right"
                    HorizontalPosition.CENTER -> "center"
                }
                val vertical = when (viewInfo.position.vertical) {
                    VerticalPosition.TOP -> "top"
                    VerticalPosition.BOTTOM -> "bottom"
                    VerticalPosition.CENTER -> "center"
                }
                "object-fit: cover; object-position: $horizontal $vertical;"
            }
        }

    private abstract class MediaWebViewClient(private val onRetry: Runnable) : WebViewClient() {

        var error = false
        var retryDelay = START_RETRY_DELAY

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (error) {
                UALog.v { "MediaWebViewClient onPageFinished with error, retrying in ${retryDelay}ms: url=$url" }
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
            UALog.v { "MediaWebViewClient onReceivedError: url=${request.url}, isForMainFrame=${request.isForMainFrame}, errorCode=${error.errorCode}, description=${error.description}" }
            // Chromium reports a spurious ERR_FAILED for the video sub-resource when the
            // renderer's fetch is handed off to the media pipeline, even though playback
            // succeeds. Only main frame failures should trigger a page reload.
            if (request.isForMainFrame) {
                this.error = true
            }
        }

        protected abstract fun onPageFinished(webView: WebView)

        companion object {
            private const val START_RETRY_DELAY: Long = 1000
        }
    }

    class FixedAspectRatioFrameLayout(context: Context) : FrameLayout(context) {

        // Default to a 16:9 aspect ratio
        var aspectRatio: Float? = 1.77f
        var shouldEnforceAspectRatio: Boolean = true

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
            val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
            val isWidthFixed = widthMode == MeasureSpec.EXACTLY
            val isHeightFixed = heightMode == MeasureSpec.EXACTLY

            // Fill the given space when the aspect ratio shouldn't be enforced
            // (both dimensions were fully specified by the layout).
            if (!shouldEnforceAspectRatio && isWidthFixed && isHeightFixed &&
                availableWidth > 0 && availableHeight > 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }

            val ratio = aspectRatio?.takeIf { it > 0 } ?: run {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }

            val width: Int
            val height: Int
            when {
                // Width is known: derive height from the ratio. This also covers the
                // both-fixed case, where the enforced ratio wins over the given height.
                isWidthFixed && availableWidth > 0 -> {
                    width = availableWidth
                    height = (availableWidth / ratio).toInt()
                }
                // Height is known: derive width from the ratio.
                isHeightFixed -> {
                    width = (availableHeight * ratio).toInt()
                    height = availableHeight
                }
                // Neither dimension is known (auto x auto). The WebView has no intrinsic
                // content size, so fit within the available space instead of collapsing
                // to zero: full width, unless the derived height would overflow.
                availableWidth > 0 -> {
                    val fullWidthHeight = (availableWidth / ratio).toInt()
                    val heightConstrained = heightMode == MeasureSpec.AT_MOST &&
                        availableHeight in 1 until fullWidthHeight
                    if (heightConstrained) {
                        width = (availableHeight * ratio).toInt()
                        height = availableHeight
                    } else {
                        width = availableWidth
                        height = fullWidthHeight
                    }
                }
                // No usable constraints; fall back to default measurement.
                else -> {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                    return
                }
            }

            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        }
    }

    companion object {
        private const val MAX_VIDEO_ERROR_RETRIES = 3
        private const val VIDEO_ERROR_RETRY_DELAY_MS = 1000L

        @Language("HTML")
        private val VIDEO_HTML_FORMAT = """
            <body style="margin:0">
                <video id="video" playsinline preload="auto" %s %s %s %s height="100%%" width="100%%" src="%s" style="%s"></video>
                <script>
                    let videoElement = document.getElementById("video");

                    document.addEventListener("visibilitychange", () => {
                        VideoListenerInterface.onVisibilityChanged(document.visibilityState === "visible");
                    });
                    VideoListenerInterface.onVisibilityChanged(document.visibilityState === "visible");

                    videoElement.addEventListener("canplay", (event) => {
                      VideoListenerInterface.onVideoReady(!videoElement.paused, videoElement.muted);
                    });
                    videoElement.addEventListener("play", () => {
                        VideoListenerInterface.onVideoPlay();
                    });
                    videoElement.addEventListener("pause", () => {
                        VideoListenerInterface.onVideoPause();
                    });
                    videoElement.addEventListener("ended", () => {
                        VideoListenerInterface.onVideoEnded();
                    });
                    videoElement.addEventListener("error", () => {
                        const mediaError = videoElement.error;
                        VideoListenerInterface.onVideoError(
                            mediaError ? mediaError.code : -1,
                            mediaError && mediaError.message ? mediaError.message : "");
                    });
                </script>
            </body>
            """.trimIndent()

        @Language("HTML")
        private val IMAGE_HTML_FORMAT = """
            <body style="margin:0">
                <img height="100%%" width="100%%" src="%s"/>
            </body>
            """.trimIndent()

        /**
         * @see <a href="https://developers.google.com/youtube/player_parameters#Manual_IFrame_Embeds">YouTube IFrame Player API docs.</a>
         */
        @Language("HTML")
        private val YOUTUBE_HTML_FORMAT = """
            <body style="margin:0">
                <div id="player"></div>

                <script>
                  var tag = document.createElement('script');

                  tag.src = "https://www.youtube.com/iframe_api";
                  var firstScriptTag = document.getElementsByTagName('script')[0];
                  firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                  var player;
                  function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                      height: '100%%',
                      width: '100%%',
                      videoId: '%s',
                      playerVars: {
                        'playsinline': 1,
                        'modestbranding': 1,
                        'controls': %s,
                        'autoplay': %s,
                        'mute': %s,
                        'loop': %s
                      },
                      events: {
                        'onReady': onPlayerReady,
                        'onStateChange': onPlayerStateChange
                      }
                    });
                  }

                  function onPlayerReady(event) {
                    var isPlaying = player.getPlayerState() === YT.PlayerState.PLAYING;
                    var isMuted = player.isMuted();
                    VideoListenerInterface.onVideoReady(isPlaying, isMuted);
                  }

                  function onPlayerStateChange(event) {
                    if (event.data === YT.PlayerState.PLAYING) {
                      VideoListenerInterface.onVideoPlay();
                    } else if (event.data === YT.PlayerState.PAUSED) {
                      VideoListenerInterface.onVideoPause();
                    } else if (event.data === YT.PlayerState.ENDED) {
                      VideoListenerInterface.onVideoEnded();
                    }
                  }

                  document.addEventListener("visibilitychange", function() {
                    VideoListenerInterface.onVisibilityChanged(document.visibilityState === "visible");
                  });
                  VideoListenerInterface.onVisibilityChanged(document.visibilityState === "visible");

                </script>
            </body>
            """.trimIndent()

        private val YOUTUBE_ID_RE = Regex("""embed/([a-zA-Z0-9_-]+).*""")

        @Language("HTML")
        private val VIMEO_HTML_FORMAT = """
            <body style="margin:0">

              <iframe id="vimeoIframe" src="%s&playsinline=1"
                width="100%%" height="100%%" frameborder="0"
                webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>

              <script src="https://player.vimeo.com/api/player.js"></script>
              <script>
                const vimeoIframe = document.querySelector('%%23vimeoIframe');
                const vimeoPlayer = new Vimeo.Player(vimeoIframe);

                vimeoPlayer.ready().then(function() {
                    return Promise.all([vimeoPlayer.getPaused(), vimeoPlayer.getVolume()]);
                }).then(function(values) {
                    var isPlaying = !values[0];
                    var isMuted = values[1] === 0;
                    VideoListenerInterface.onVideoReady(isPlaying, isMuted);
                });

                vimeoPlayer.on('play', function() {
                    VideoListenerInterface.onVideoPlay();
                });

                vimeoPlayer.on('pause', function() {
                    VideoListenerInterface.onVideoPause();
                });

                vimeoPlayer.on('ended', function() {
                    VideoListenerInterface.onVideoEnded();
                });

                document.addEventListener("visibilitychange", function() {
                    VideoListenerInterface.onVisibilityChanged(document.visibilityState === "visible");
                });
                VideoListenerInterface.onVisibilityChanged(document.visibilityState === "visible");

              </script>
            </body>
        """.trimIndent()

    }
}
