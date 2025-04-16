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
        fun onVideoReady() {
            webViewListener?.onVideoReady()
        }
    }
}

internal class TouchAwareAirshipWebView(context: Context) : AirshipWebView(context) {

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
