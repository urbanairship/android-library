/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.ButtonModel
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.model.LabelButtonModel
import com.urbanairship.android.layout.property.TapEffect
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.LayoutUtils.dpToPx
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.findTargetDescendant
import com.urbanairship.android.layout.widget.ShrinkableView
import com.urbanairship.android.layout.widget.TappableView
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal class LabelButtonView(
    context: Context,
    val model: LabelButtonModel,
    viewEnvironment: ViewEnvironment,
    itemProperties: ItemProperties?
) : FrameLayout(context), BaseView, TappableView, ShrinkableView {

    private val view: View = model.label.createView(context, viewEnvironment, itemProperties)
    private val rippleAnimationDuration =
        resources.getInteger(android.R.integer.config_shortAnimTime).milliseconds

    init {
        isClickable = true
        isFocusable = true

        val params = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }

        addView(view, params)

        val horizontalPaddingPx = if (itemProperties?.size?.width?.isAuto == true) {
            dpToPx(context, 12)
        } else {
            0
        }

        val verticalPaddingPx = if (itemProperties?.size?.height?.isAuto == true) {
            dpToPx(context, 12)
        } else {
            0
        }
        setPaddingRelative(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)

        view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        val border = model.viewInfo.border
        var radii = border?.radii { dp: Int -> ResourceUtils.dpToPx(context, dp) }

        if (border != null && radii != null) {
            LayoutUtils.applyRippleEffect(this, radii)
        }
        updateContentDescription(null)

        val baseBackground = this.background
        model.listener = object : ButtonModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@LabelButtonView.isVisible = visible
            }
            override fun setEnabled(enabled: Boolean) {
                this@LabelButtonView.isEnabled = enabled
            }
            override fun dismissSoftKeyboard() {
                LayoutUtils.dismissSoftKeyboard(this@LabelButtonView)
            }
            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@LabelButtonView, baseBackground, old, new)
            }

            override fun onStateUpdated(state: ThomasState) {
                super.onStateUpdated(state)
                updateContentDescription(state)
            }
        }
    }

    private fun updateContentDescription(state: ThomasState?) {
        contentDescription = model.contentDescription(context) ?: model.label.contentDescription(context)
                ?: model.label.resolveState(context, state).text
    }

    /**
     * Listen for touch events and perform a click on this view if the touch up event isn't within
     * a clickable descendant of this view.
     */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (context.isTouchExplorationEnabled) {
            return false
        }

        if (event.action == MotionEvent.ACTION_UP && !event.isWithinClickableDescendantOf(view)) {
            when (model.viewInfo.tapEffect) {
                TapEffect.Default -> triggerDefaultAnimation()
                TapEffect.None -> Unit
            }

            performClick()
        }

        // We're just snooping, so always let the event pass through.
        return false
    }

    override fun taps(): Flow<Unit> = debouncedClicks()

    /** Tell talkback that we're a button. **/
    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    /** Button layouts may be shrunk if they contain a media view. */
    override fun isShrinkable(): Boolean = model.isShrinkable

    private fun MotionEvent.isWithinClickableDescendantOf(view: View): Boolean {
        return findTargetDescendant(view) { it.isClickable && it.isEnabled } != null
    }

    private fun triggerDefaultAnimation(event: MotionEvent? = null) {
        model.viewScope.launch {
            showRipple(event)
            delay(rippleAnimationDuration)
            clearRipple()
        }
    }

    private fun showRipple(event: MotionEvent? = null) {
        val ripple = this.foreground ?: return
        if (ripple is RippleDrawable) {
            event?.let { ripple.setHotspot(it.x, it.y) }
        }
        this.isPressed = true
    }

    private fun clearRipple() {
        this.isPressed = false
    }
}
