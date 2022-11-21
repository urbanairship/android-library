package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView
import com.urbanairship.webkit.AirshipWebView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal class TouchAwareWebView(context: Context) : WebView(context) {

    private val touchesChannel = Channel<MotionEvent>(UNLIMITED)

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { touchesChannel.trySend(it) }
        return super.onTouchEvent(event)
    }

    fun touchEvents(): Flow<MotionEvent> = touchesChannel.receiveAsFlow()
}

internal class TouchAwareAirshipWebView(context: Context) : AirshipWebView(context) {

    private val touchesChannel = Channel<MotionEvent>(UNLIMITED)

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { touchesChannel.trySend(it) }
        return super.onTouchEvent(event)
    }

    fun touchEvents(): Flow<MotionEvent> = touchesChannel.receiveAsFlow()
}
