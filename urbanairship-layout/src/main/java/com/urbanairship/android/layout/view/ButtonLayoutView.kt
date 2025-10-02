/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ButtonLayoutInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.ButtonLayoutModel
import com.urbanairship.android.layout.model.ButtonModel
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.property.TapEffect
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.findTargetDescendant
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.widget.ShrinkableView
import com.urbanairship.android.layout.widget.TappableView
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Custom `FrameLayout` that allows for a "button" to be created from arbitrary content. */
internal class ButtonLayoutView(
    context: Context,
    val model: ButtonLayoutModel,
    viewEnvironment: ViewEnvironment,
    val itemProperties: ItemProperties?
) : FrameLayout(context), BaseView, TappableView, ShrinkableView {

    private val isButtonForAccessibility: Boolean
        get() = when (model.viewInfo.accessibilityRole) {
            ButtonLayoutInfo.AccessibilityRole.Button -> true
            ButtonLayoutInfo.AccessibilityRole.Container -> false
            null -> true /// defaults to button
        }

    private val view = model.view.createView(context, viewEnvironment, itemProperties)

    private val rippleAnimationDuration =
        resources.getInteger(android.R.integer.config_shortAnimTime).milliseconds

    init {
        LayoutUtils.applyButtonLayoutModel(this, model)

        addView(view, MATCH_PARENT, MATCH_PARENT)

        if (isButtonForAccessibility) {
            this.isClickable = true
            this.isFocusable = true
            model.contentDescription(context)?.ifNotEmpty {
                this.contentDescription = it
            }
            // To make it a single unit, the child view MUST be hidden from accessibility.
            view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else if (model.viewInfo.contentDescription != null || model.viewInfo.localizedContentDescription != null) {
            // We are a container with a set content description instead of generated, expose it
            this.isClickable = true
            this.isFocusable = true
            model.contentDescription(context)?.ifNotEmpty {
                this.contentDescription = it
            }

            // As a group, the children inside can still be individually focusable.
            // We leave the child view's accessibility as default (AUTO).
            view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO
        } else {
            this.isClickable = false
            this.isFocusable = false

            // Hide the parent and allow focus to pass through to its children.
            this.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val baseBackground = this.background
        model.listener = object : ButtonModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@ButtonLayoutView.isVisible = visible
            }
            override fun setEnabled(enabled: Boolean) {
                this@ButtonLayoutView.isEnabled = enabled
            }
            override fun dismissSoftKeyboard() {
                LayoutUtils.dismissSoftKeyboard(this@ButtonLayoutView)
            }
            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@ButtonLayoutView, baseBackground, old, new)
            }
        }
    }

    /**
     * Listen for touch events and perform a click on this view if the touch up event isn't within
     * a clickable descendant of this view.
     */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!isButtonForAccessibility && context.isTouchExplorationEnabled) {
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

    override fun getAccessibilityClassName(): CharSequence {
        return if (isButtonForAccessibility) {
            Button::class.java.name
        } else {
            FrameLayout::class.java.name
        }
    }

    /** Button layouts may be shrunk if they contain a media view. */
    override fun isShrinkable(): Boolean = model.isShrinkable

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (!isButtonForAccessibility) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_CLICK && !isButtonForAccessibility) {
            // Replicate the behavior from onInterceptTouchEvent for a11y clicks.
            when (model.viewInfo.tapEffect) {
                TapEffect.Default -> triggerDefaultAnimation()
                TapEffect.None -> Unit
            }
            performClick()
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

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

internal val Context.isTouchExplorationEnabled: Boolean
    get() {
        return ContextCompat.getSystemService(this, AccessibilityManager::class.java)?.isTouchExplorationEnabled
            ?: false
    }
