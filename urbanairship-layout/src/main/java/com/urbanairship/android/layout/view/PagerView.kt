/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.descendants
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.gestures.PagerGestureDetector
import com.urbanairship.android.layout.gestures.PagerGestureEvent
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.isWithinClickableDescendant
import com.urbanairship.android.layout.widget.PagerRecyclerView

@RequiresApi(Build.VERSION_CODES.O)
internal class PagerView(
    context: Context,
    val model: PagerModel,
    viewEnvironment: ViewEnvironment
) : FrameLayout(context), BaseView, KeyEvent.Callback {

    fun interface OnScrollListener {
        fun onScrollTo(position: Int, isInternalScroll: Boolean)
    }

    interface OnPagerGestureListener {
        fun onGesture(event: PagerGestureEvent)
    }

    var scrollListener: OnScrollListener? = null
    var gestureListener: OnPagerGestureListener? = null
        set(value) {
            field = value

            // Create a gesture detector if a listener is being set, otherwise clear it.
            gestureDetector = when (value) {
                null -> null
                else -> gestureDetector ?: PagerGestureDetector(this) {
                    gestureListener?.onGesture(it)
                }
            }
        }

    private var gestureDetector: PagerGestureDetector? = null

    private val view: PagerRecyclerView = PagerRecyclerView(context, model, viewEnvironment)

    private val modelListener = object : PagerModel.Listener {
        override fun scrollTo(position: Int) {
            if (position != NO_POSITION) {
                view.scrollTo(position)
            }
        }

        override fun setVisibility(visible: Boolean) {
            this@PagerView.isGone = visible
        }

        override fun setEnabled(enabled: Boolean) {
            this@PagerView.isEnabled = enabled
        }
    }

    init {
        this@PagerView.isFocusable = true
        this@PagerView.isFocusedByDefault = true
        addView(view, MATCH_PARENT, MATCH_PARENT)
        LayoutUtils.applyBorderAndBackground(this, model)
        model.listener = modelListener

        // If Talkback is enabled, focus the first focusable view
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val accessibilityListener = AccessibilityManager.AccessibilityStateChangeListener { isEnabled ->
            if (isEnabled) {
                val accessibleView = view.descendants.first { it.isImportantForAccessibility }
                accessibleView.postDelayed({
                    accessibleView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                }, 800)
            }
        }
        accessibilityManager.addAccessibilityStateChangeListener(accessibilityListener)

        view.setPagerScrollListener { position, isInternalScroll ->
            scrollListener?.onScrollTo(position, isInternalScroll)
        }

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            // Recreate tap events to navigate pages with keyboard
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                gestureDetector?.let { detector ->
                    detector.onTouchEvent(generateMotionEvent(MotionEvent.ACTION_DOWN, 45f))
                    detector.onTouchEvent(generateMotionEvent(MotionEvent.ACTION_UP, 45f))

                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                gestureDetector?.let { detector ->
                    detector.onTouchEvent(generateMotionEvent(MotionEvent.ACTION_DOWN, 975f))
                    detector.onTouchEvent(generateMotionEvent(MotionEvent.ACTION_UP, 975f))
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // If a gesture detector is attached, check if the event should be intercepted.
        // We only want to intercept events that are not within a clickable descendant.
        gestureDetector?.let { detector ->
            if (!event.isWithinClickableDescendant(view)) {
                detector.onTouchEvent(event)
            }
        }

        // We're just snooping, so always let the event pass through.
        return super.onInterceptTouchEvent(event)
    }

    private fun generateMotionEvent(action: Int, xCoordinate: Float): MotionEvent {
        return MotionEvent.obtain(SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            action,
            xCoordinate,
            1500f,
            0)
    }
}
