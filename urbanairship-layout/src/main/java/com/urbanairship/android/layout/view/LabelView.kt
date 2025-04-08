/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils.spToPx
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.util.isLayoutRtl

internal class LabelView(
    context: Context,
    model: LabelModel
) : AppCompatTextView(context), BaseView {

    init {
        LayoutUtils.applyLabelModel(this, model, model.viewInfo.text)
        var lastText: String = model.viewInfo.text

        model.contentDescription(context).ifNotEmpty { contentDescription = it }

        if (model.viewInfo.accessibilityHidden == true) {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        ViewCompat.setAccessibilityHeading(this, model.viewInfo.accessibilityRole?.type == LabelInfo.AccessibilityRoleType.HEADING)

        isClickable = false


        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@LabelView.isVisible = visible
            }

            override fun onStateUpdated(state: ThomasState) {
                val resolvedText = state.resolveRequired(
                    overrides = model.viewInfo.viewOverrides?.text,
                    default = model.viewInfo.text
                )

                if (resolvedText != lastText) {
                    LayoutUtils.applyLabelModel(this@LabelView, model, resolvedText)
                    lastText = resolvedText
                }

                val resolvedIconStart = state.resolveOptional(
                    overrides = model.viewInfo.viewOverrides?.iconStart,
                    default = model.viewInfo.iconStart
                )

                val drawableStart = when (resolvedIconStart) {
                    is LabelInfo.IconStart.Floating ->
                        resolvedIconStart.icon.getDrawable(context, isEnabled)
                    else -> null
                }

                drawableStart?.let {
                    val size = spToPx(context, model.viewInfo.textAppearance.fontSize).toInt()
                    it.setBounds(0, 0, size, size)
                }

                resolvedIconStart?.let { compoundDrawablePadding = it.space }

                if (isLayoutRtl) {
                    setCompoundDrawables(null, null, drawableStart, null)
                } else {
                    setCompoundDrawables(drawableStart, null, null, null)
                }
            }

            override fun setEnabled(enabled: Boolean) {
                this@LabelView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@LabelView, old, new)
            }
        }
    }
}
