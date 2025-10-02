/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.accessibility.AccessibilityEvent
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils.spToPx
import com.urbanairship.android.layout.util.ifNotEmpty

internal class LabelView(
    context: Context,
    private val model: LabelModel
) : AppCompatTextView(context), BaseView {

    private var lastState: LabelModel.ResolvedState? = null

    init {
        // Initial setup from the model
        setupInitialState()

        // Set the listener to handle state updates
        model.listener = createModelListener()
    }

    private fun setupInitialState() {
        updateViewContent(null)
        model.contentDescription(context).ifNotEmpty { contentDescription = it }
        if (model.viewInfo.accessibilityHidden == true) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        if (model.viewInfo.isAccessibilityAlert == true) {
            this.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        }

        model.viewInfo.accessibilityRole?.let { role ->
            when(role) {
                is LabelInfo.AccessibilityRole.Heading -> {
                    ViewCompat.setAccessibilityHeading(this, true)
                }
            }
        }

        isClickable = false
        isFocusable = false
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)

        if (isVisible && model.viewInfo.isAccessibilityAlert == true) {
            // Manually send an event to notify the system of a content change.
            // This can give the live region the "nudge" it needs to make an announcement.
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    private fun createModelListener(): BaseModel.Listener {
        return object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@LabelView.isVisible = visible
            }

            override fun onStateUpdated(state: ThomasState) {
                updateViewContent(state)
            }

            override fun setEnabled(enabled: Boolean) {
                this@LabelView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@LabelView, old, new)
            }
        }
    }

    private fun updateViewContent(state: ThomasState?) {
        val resolvedState = model.resolveState(context, state)
        if (resolvedState == this.lastState) {
            return
        }

        this.text = resolvedState.text

        val size = resolvedState.textAppearance.fontSize
        val startDrawable = getSizedDrawable(resolvedState.iconStart, size, HorizontalPosition.START)
        val endDrawable = getSizedDrawable(resolvedState.iconEnd, size,HorizontalPosition.END)
        setCompoundDrawables(startDrawable, null, endDrawable, null)

        LayoutUtils.applyLabel(
            this,
            resolvedState.textAppearance,
            model.viewInfo.markdownOptions,
            resolvedState.text
        )

        this.lastState = resolvedState
    }

    private fun getSizedDrawable(
        iconInfo: LabelInfo.LabelIcon?,
        size: Int,
        position: HorizontalPosition
    ): Drawable? {
        val resolvedIcon = iconInfo as? LabelInfo.LabelIcon.Floating ?: return null

        val drawable = resolvedIcon.icon.getDrawable(context, isEnabled, position) ?: return null

        val size = spToPx(context, size).toInt()
        val space = spToPx(context, resolvedIcon.space).toInt()

        // Use an InsetDrawable to handle spacing between the icon and the text.
        val insetLeft = if (position == HorizontalPosition.END) space else 0
        val insetRight = if (position == HorizontalPosition.START) space else 0

        val finalDrawable = InsetDrawable(drawable, insetLeft, 0, insetRight, 0)
        finalDrawable.setBounds(0, 0, size + space, size)

        return finalDrawable
    }
}
