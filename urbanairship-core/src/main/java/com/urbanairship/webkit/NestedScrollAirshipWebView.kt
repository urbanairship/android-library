/*
 * Based on the implementation of NestedScrollingWebView from the Android Open Source Project,
 * but adapted to WebView and implemented in Kotlin. Original copyright notice follows:
 *
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.urbanairship.webkit

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import androidx.annotation.CallSuper
import androidx.annotation.RestrictTo
import androidx.core.view.NestedScrollingChild
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class NestedScrollAirshipWebView public constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle,
    defStyleRes: Int = 0
) : AirshipWebView(context, attrs, defStyleAttr, defStyleRes), NestedScrollingChild {

    private val helper = NestedScrollingChildHelper(this)

    private val offset = IntArray(2)
    private val consumed = IntArray(2)

    private var nestedOffsetY = 0

    private var lastY = 0

    init {
        isNestedScrollingEnabled = true
    }

    @CallSuper
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        var returnValue = false

        val event = MotionEvent.obtain(ev)
        val action = event.action
        if (action == ACTION_DOWN) {
            nestedOffsetY = 0
        }

        val eventY = event.y.toInt()
        event.offsetLocation(0f, nestedOffsetY.toFloat())
        when (action) {
            ACTION_MOVE -> {
                var deltaY = lastY - eventY

                if (dispatchNestedPreScroll(0, deltaY, consumed, offset)) {
                    deltaY -= consumed[1]
                    lastY = eventY - offset[1]
                    event.offsetLocation(0f, -offset[1].toFloat())
                    nestedOffsetY += offset[1]
                }
                returnValue = super.onTouchEvent(event)

                if (dispatchNestedScroll(0, offset[1], 0, deltaY, offset)) {
                    event.offsetLocation(0f, offset[1].toFloat())
                    nestedOffsetY += offset[1]
                    lastY -= offset[1]
                }
            }

            ACTION_DOWN -> {
                returnValue = super.onTouchEvent(event)
                lastY = eventY
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
            }

            ACTION_UP, ACTION_CANCEL -> {
                returnValue = super.onTouchEvent(event)
                stopNestedScroll()
            }
        }
        return returnValue
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        helper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = helper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = helper.startNestedScroll(axes)

    override fun stopNestedScroll(): Unit = helper.stopNestedScroll()

    override fun hasNestedScrollingParent(): Boolean = helper.hasNestedScrollingParent()

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean = helper.dispatchNestedScroll(
        dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow
    )

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean = helper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean = helper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(
        velocityX: Float,
        velocityY: Float
    ): Boolean = helper.dispatchNestedPreFling(velocityX, velocityY)
}
