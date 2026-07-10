package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Checkable
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.ToggleButton
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import com.urbanairship.R
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.BaseToggleLayoutModel
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.util.isActionDown
import com.urbanairship.android.layout.util.isActionUp
import com.urbanairship.android.layout.util.isWithinBounds
import com.urbanairship.android.layout.util.isWithinClickableDescendantOf
import com.urbanairship.android.layout.widget.TappableView
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal class ToggleLayoutView<T: BaseToggleLayoutModel<*, *>>(
    context: Context,
    val model: T,
    viewEnvironment: ViewEnvironment,
    val itemProperties: ItemProperties?,
    val type: ToggleLayoutType
) : FrameLayout(context), BaseView, TappableView, Checkable {

    enum class ToggleLayoutType {
        SCORE,
        RADIO,
        CHECKBOX,
        BASIC
    }

    private val view = model.view.createView(context, viewEnvironment, itemProperties)

    private val rippleAnimationDuration =
        resources.getInteger(android.R.integer.config_shortAnimTime).milliseconds

    init {
        isClickable = true
        isFocusable = true

        LayoutUtils.applyToggleLayoutRippleEffect(this, model.viewInfo)

        addView(view, MATCH_PARENT, MATCH_PARENT)
        view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)

                info.className = accessibilityClassName
                info.isCheckable = host.isEnabled
                model.contentDescription(host.context)?.ifNotEmpty { info.contentDescription = it }

                if (host.isEnabled) {
                    info.isChecked = model.isOn.value
                }

                var useSelected = when(type) {
                    ToggleLayoutType.SCORE -> true
                    ToggleLayoutType.RADIO -> true
                    ToggleLayoutType.CHECKBOX -> false
                    ToggleLayoutType.BASIC -> false
                }
                if (useSelected) {
                    info.stateDescription = if (isChecked) {
                        host.context.getString(R.string.ua_selected)
                    } else {
                        host.context.getString(R.string.ua_not_selected)
                    }
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

    /** True while a press that began within this view (and not on a clickable descendant) is in progress. */
    private var pressStartedWithinSelf = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when {
            event.isActionDown ->
                pressStartedWithinSelf = event.isWithinBounds(this) && !event.isWithinClickableDescendantOf(view)

            event.isActionUp && pressStartedWithinSelf &&
                    event.isWithinBounds(this) && !event.isWithinClickableDescendantOf(view) -> {
                triggerDefaultAnimation(event)
                performClick()
            }
        }
        return false
    }

    /**
     * The default clickable behavior fires a click for any ACTION_UP within our bounds — even when the
     * press started on us but the finger drifted onto a clickable child. Convert such an up into a
     * cancel before super so we don't fire our own click when the release lands on a clickable descendant.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.isActionUp && event.isWithinClickableDescendantOf(view)) {
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            return try {
                super.onTouchEvent(cancel)
            } finally {
                cancel.recycle()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        model.toggle()
        return true
    }

    override fun taps(): Flow<Unit> = debouncedClicks()

    private fun updateToggleVisualState(isOn: Boolean) {
        if (isActivated == isOn) {
            return
        }

        isActivated = isOn
    }

    private fun triggerDefaultAnimation(event: MotionEvent? = null) {
        model.viewScope.launch {
            showRipple(event)
            delay(rippleAnimationDuration)
            clearRipple()
        }
    }

    override fun getAccessibilityClassName(): CharSequence {
        return when (type) {
            ToggleLayoutType.SCORE -> RadioButton::class.java.name
            ToggleLayoutType.RADIO -> RadioButton::class.java.name
            ToggleLayoutType.CHECKBOX -> ToggleButton::class.java.name
            ToggleLayoutType.BASIC -> ToggleButton::class.java.name
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
