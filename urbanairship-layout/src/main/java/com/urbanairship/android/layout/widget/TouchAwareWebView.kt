package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.urbanairship.android.layout.view.ButtonLayoutView
import com.urbanairship.android.layout.view.MediaView
import com.urbanairship.webkit.AirshipWebView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal class TouchAwareWebView(context: Context, val webViewListener: MediaView.WebViewListener?) : WebView(context) {

    private val touchesChannel = Channel<MotionEvent>(UNLIMITED)

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // track touch events for flow consumers
        event?.let { touchesChannel.trySend(it) }

        // let WebView handle the event (for example for video controls)
        val handled = super.onTouchEvent(event)

        // after WebView processes the ACTION_UP event, also handle click propagation
        if (event?.action == MotionEvent.ACTION_UP) {
            findButtonLayoutParent()?.let { buttonLayout ->
                post { buttonLayout.performClick() }
            }
        }
        return handled
    }

    fun touchEvents(): Flow<MotionEvent> = touchesChannel.receiveAsFlow()

    fun getJavascriptInterface(): VideoListenerInterface {
        return VideoListenerInterface()
    }

    inner class VideoListenerInterface() {
        @JavascriptInterface
        fun onVideoReady(playing: Boolean, muted: Boolean) {
            post { webViewListener?.onVideoReady(playing, muted) }
        }
        @JavascriptInterface
        fun onVideoPlay() {
            post { webViewListener?.onVideoPlay() }
        }
        @JavascriptInterface
        fun onVideoPause() {
            post { webViewListener?.onVideoPause() }
        }
        @JavascriptInterface
        fun onVideoEnded() {
            post { webViewListener?.onVideoEnded() }
        }
        @JavascriptInterface
        fun onVideoError(errorCode: Int, description: String?) {
            post { webViewListener?.onVideoError(errorCode, description) }
        }
        @JavascriptInterface
        fun onVisibilityChanged(isVisible: Boolean) {
            post { webViewListener?.onVisibilityChanged(isVisible) }
        }
    }
}

internal class TouchAwareAirshipWebView(context: Context) : AirshipWebView(context) {

    private val touchesChannel = Channel<MotionEvent>(UNLIMITED)

    /**
     * Finds the view an arrow leaving the page moves to, falling back to the next view in focus
     * order when nothing lies that way.
     *
     * A web view is often the full size of its scene, with a close button drawn over it rather
     * than beyond its edge, so a directional search alone would leave focus stuck in the page.
     *
     * @param direction The direction focus is moving.
     * @return The view to focus, or `null` if there is none.
     */
    override fun focusSearch(direction: Int): View? =
        super.focusSearch(direction) ?: inOrderFallback(direction)?.let { super.focusSearch(it) }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // track touch events for flow consumers
        event?.let { touchesChannel.trySend(it) }

        // let WebView handle the event (for example for video controls)
        val handled = super.onTouchEvent(event)

        // after WebView processes the ACTION_UP event, also handle click propagation
        if (event?.action == MotionEvent.ACTION_UP) {
            findButtonLayoutParent()?.let { buttonLayout ->
                post { buttonLayout.performClick() }
            }
        }
        return handled
    }

    fun touchEvents(): Flow<MotionEvent> = touchesChannel.receiveAsFlow()
}

private fun View.findButtonLayoutParent(): ButtonLayoutView? {
    var parent: View? = this
    while (parent != null) {
        parent = parent.parent as? View
        if (parent is ButtonLayoutView) {
            return parent
        }
    }
    return null
}
