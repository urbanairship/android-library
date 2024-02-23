package com.urbanairship.android.layout.gestures

import android.view.GestureDetector
import android.view.MotionEvent
import com.urbanairship.android.layout.gestures.PagerGestureEvent.Hold
import com.urbanairship.android.layout.gestures.PagerGestureEvent.Hold.Action
import com.urbanairship.android.layout.gestures.PagerGestureEvent.Swipe
import com.urbanairship.android.layout.gestures.PagerGestureEvent.Tap
import com.urbanairship.android.layout.property.GestureDirection
import com.urbanairship.android.layout.property.GestureLocation
import com.urbanairship.android.layout.util.isActionUp
import com.urbanairship.android.layout.util.isLayoutRtl
import com.urbanairship.android.layout.util.localBounds
import com.urbanairship.android.layout.view.PagerView

/**
 * Gesture detection helper for [PagerView].
 *
 * Detects the following gestures:
 * - Tap on top, bottom, left, or right
 * - Long press (hold) anywhere
 * - Swipe up or down
 */
internal class PagerGestureDetector(
    view: PagerView,
    private val onGestureDetected: (PagerGestureEvent) -> Unit,
) : GestureDetector.SimpleOnGestureListener() {
    private var gestureMapper = PagerGestureMapper(view.localBounds, view.isLayoutRtl)
    private var isLongPressing: Boolean = false

    private val gestureDetector = GestureDetector(view.context, this)

    init {
        // Update mapper bounds when view size changes.
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            gestureMapper.onLayoutChanged(v.localBounds, v.isLayoutRtl)
        }
    }

    override fun onDown(e: MotionEvent): Boolean = true

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        gestureMapper.mapTap(e.x, e.y)?.let { taps ->
            taps.map { Tap(it) }
                .forEach { onGestureDetected(it) }
        }
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        isLongPressing = true
        onGestureDetected(Hold(Action.PRESS))
    }

    override fun onFling(
        e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
    ): Boolean {
        gestureMapper.mapSwipe(e1, e2, velocityX, velocityY)?.let {
            onGestureDetected(Swipe(it))
        }
        return true
    }

    fun onTouchEvent(event: MotionEvent) {
        gestureDetector.onTouchEvent(event)

        // Detect long press up. This is a workaround for the GestureDetector
        // listener only reporting long press ACTION_DOWN events.
        if (isLongPressing && event.isActionUp) {
            isLongPressing = false
            onGestureDetected(Hold(Action.RELEASE))
        }
    }
}

internal sealed class PagerGestureEvent {
    data class Tap(val location: GestureLocation) : PagerGestureEvent()
    data class Hold(val action: Action) : PagerGestureEvent() {
        enum class Action { PRESS, RELEASE }
    }
    data class Swipe(val direction: GestureDirection) : PagerGestureEvent()
}
