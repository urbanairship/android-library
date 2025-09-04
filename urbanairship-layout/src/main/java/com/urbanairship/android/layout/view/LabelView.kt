/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.Image.CenteredImageSpan
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils.spToPx
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.util.isLayoutRtl
import com.urbanairship.util.UAStringUtil

internal class LabelView(
    context: Context,
    model: LabelModel
) : AppCompatTextView(context), BaseView {

    init {
        LayoutUtils.applyLabelModel(this, model, model.viewInfo.text)
        var lastText: String = model.viewInfo.text
        var lastStartDrawable: Drawable? = null

        model.contentDescription(context).ifNotEmpty { contentDescription = it }

        if (model.viewInfo.accessibilityHidden == true) {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        ViewCompat.setAccessibilityHeading(this, model.viewInfo.accessibilityRole?.type == LabelInfo.AccessibilityRoleType.HEADING)

        isClickable = false
        isFocusable = false

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@LabelView.isVisible = visible
            }

            override fun onStateUpdated(state: ThomasState) {
                val resolvedText = resolveText(state)
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
                        resolvedIconStart.icon.getDrawable(context, isEnabled, if (isLayoutRtl) HorizontalPosition.END else HorizontalPosition.START)
                    else -> null
                }

                if (drawableStart == null && lastStartDrawable != null) {
                    LayoutUtils.applyLabelModel(this@LabelView, model, resolvedText)
                }
                lastStartDrawable = drawableStart

                drawableStart?.let {
                    val size = spToPx(context, model.viewInfo.textAppearance.fontSize).toInt()
                    val space = resolvedIconStart?.let { icon -> spToPx(context, icon.space).toInt() } ?: 0
                    it.setBounds(
                        0,
                        0,
                        size + space,
                        size
                    )

                    val imageSpan = CenteredImageSpan(it)
                    if (isLayoutRtl) {
                        val spannableString = SpannableString("$resolvedText ").apply {
                            setSpan(imageSpan, length - 1, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        text = spannableString
                    } else {
                        val spannableString = SpannableString(" $resolvedText").apply {
                            setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        text = spannableString
                    }
                }
            }

            override fun setEnabled(enabled: Boolean) {
                this@LabelView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@LabelView, old, new)
            }

            private fun resolveText(state: ThomasState): String {
                val ref = state.resolveOptional(
                    overrides = model.viewInfo.viewOverrides?.ref,
                    default = model.viewInfo.ref
                )

                val text =  state.resolveRequired(
                    overrides = model.viewInfo.viewOverrides?.text,
                    default = model.viewInfo.text
                )

                return if (ref != null) {
                    UAStringUtil.namedStringResource(getContext(), ref, text)
                } else {
                    text
                }
            }
        }
    }
}
