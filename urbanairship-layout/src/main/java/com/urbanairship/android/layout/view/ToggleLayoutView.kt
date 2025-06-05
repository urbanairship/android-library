package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Checkable
import android.widget.FrameLayout
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.BaseToggleLayoutModel
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.findTargetDescendant
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.widget.TappableView
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal class ToggleLayoutView<T: BaseToggleLayoutModel<*, *>>(
    context: Context,
    val model: T,
    viewEnvironment: ViewEnvironment,
    val itemProperties: ItemProperties?
) : FrameLayout(context), BaseView, TappableView, Checkable {

    private val view = model.view.createView(context, viewEnvironment, itemProperties)

    private val rippleAnimationDuration =
        resources.getInteger(android.R.integer.config_shortAnimTime).milliseconds

    init {
        isClickable = true
        isFocusable = true

        LayoutUtils.applyToggleLayoutRippleEffect(this, model.viewInfo)

        addView(view, MATCH_PARENT, MATCH_PARENT)

        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        model.contentDescription(context)?.ifNotEmpty { contentDescription = it }

        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)

                info.className = android.widget.ToggleButton::class.java.name
                info.isCheckable = host.isEnabled

                if (host.isEnabled) {
                    info.isChecked = model.isOn.value
                }
            }
        })

        model.viewScope.launch {
            model.isOn.collect { isChecked ->
                updateToggleVisualState(isChecked)
            }
        }

        val baseBackground = this.background
        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@ToggleLayoutView.isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@ToggleLayoutView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@ToggleLayoutView, baseBackground, old, new)
            }
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && !event.isWithinClickableDescendantOf(view)) {
            triggerDefaultAnimation(event)
            performClick()
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        model.toggle()
        return true
    }

    override fun taps(): Flow<Unit> = debouncedClicks()

    private fun updateToggleVisualState(isOn: Boolean) {
        isActivated = isOn
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

    override fun getAccessibilityClassName(): CharSequence {
        return android.widget.ToggleButton::class.java.name
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

    override fun setChecked(checked: Boolean) {
        model.setChecked(checked)
    }

    override fun isChecked(): Boolean {
        return model.isOn.value
    }

    override fun toggle() {
        model.toggle()
    }
}
