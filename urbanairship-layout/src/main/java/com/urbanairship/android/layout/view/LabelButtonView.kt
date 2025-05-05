/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.ButtonModel
import com.urbanairship.android.layout.model.LabelButtonModel
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.Image.CenteredImageSpan
import com.urbanairship.android.layout.property.TapEffect
import com.urbanairship.android.layout.util.ColorStateListBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.ResourceUtils.dpToPx
import com.urbanairship.android.layout.util.ResourceUtils.spToPx
import com.urbanairship.android.layout.util.debouncedClicks
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.util.isLayoutRtl
import com.urbanairship.android.layout.widget.TappableView
import com.urbanairship.util.UAStringUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.flow.Flow

internal class LabelButtonView(
    context: Context,
    private var model: LabelButtonModel
) : MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle), BaseView, TappableView {

    init {
        isAllCaps = false
        minHeight = 0
        minimumHeight = 0
        insetTop = 0
        insetBottom = 0

        LayoutUtils.applyLabelModel(this, model.label, model.viewInfo.label.text)
        var lastText: String = model.viewInfo.label.text
        var lastStartDrawable: Drawable? = null

        isSingleLine = false
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END

        model.contentDescription(context)?.ifNotEmpty {
            contentDescription = it
        }

        model.listener = object : ButtonModel.Listener {
            override fun setEnabled(enabled: Boolean) {
                this@LabelButtonView.isEnabled = enabled
            }

            override fun setVisibility(visible: Boolean) {
                this@LabelButtonView.isVisible = visible
            }

            override fun onStateUpdated(state: ThomasState) {
                val resolvedText = resolveText(state)

                if (resolvedText != lastText) {
                    LayoutUtils.applyLabelModel(this@LabelButtonView, model.label, resolvedText)
                    lastText = resolvedText
                }

                val resolvedIconStart = state.resolveOptional(
                    overrides = model.viewInfo.label.viewOverrides?.iconStart,
                    default = model.viewInfo.label.iconStart
                )

                val drawableStart = when (resolvedIconStart) {
                    is LabelInfo.IconStart.Floating ->
                        resolvedIconStart.icon.getDrawable(context, isEnabled, if (isLayoutRtl) HorizontalPosition.END else HorizontalPosition.START)
                    else -> null
                }

                if (drawableStart == null && lastStartDrawable != null) {
                    LayoutUtils.applyLabelModel(this@LabelButtonView, model.label, resolvedText)
                }
                lastStartDrawable = drawableStart

                drawableStart?.let {
                    val size = spToPx(context, model.viewInfo.label.textAppearance.fontSize).toInt()
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

            override fun dismissSoftKeyboard() =
                LayoutUtils.dismissSoftKeyboard(this@LabelButtonView)

            override fun setBackground(old: Background?, new: Background) {
                val border = new.border
                val color = new.color

                val textAppearance = model.label.viewInfo.textAppearance
                val textColor = textAppearance.color.resolve(context)
                val backgroundColor = color?.resolve(context) ?: Color.TRANSPARENT
                val pressedColor = if (model.viewInfo.tapEffect is TapEffect.None) {
                    Color.TRANSPARENT
                } else {
                    ColorUtils.setAlphaComponent(
                        textColor,
                        Math.round(Color.alpha(textColor) * LayoutUtils.PRESSED_ALPHA_PERCENT)
                    )
                }
                val disabledColor = LayoutUtils.generateDisabledColor(backgroundColor)
                val strokeWidth = border?.strokeWidth ?: LayoutUtils.DEFAULT_STROKE_WIDTH_DPS
                val strokeColor = border?.strokeColor?.resolve(context) ?: backgroundColor
                val disabledStrokeColor = LayoutUtils.generateDisabledColor(strokeColor)

                val borderShape = ShapeAppearanceModel.builder()
                if (border?.applyToShape(borderShape, { dpToPx(context, it).toInt() }) != true) {
                    borderShape.setAllCorners(CornerFamily.ROUNDED, dpToPx(context, LayoutUtils.DEFAULT_BORDER_RADIUS))
                }

                this@LabelButtonView.backgroundTintList =
                    ColorStateListBuilder().add(disabledColor, -R.attr.state_enabled)
                        .add(backgroundColor).build()

                this@LabelButtonView.rippleColor = ColorStateList.valueOf(pressedColor)
                this@LabelButtonView.strokeWidth =
                    ResourceUtils.dpToPx(context, strokeWidth).toInt()
                this@LabelButtonView.strokeColor =
                    ColorStateListBuilder().add(disabledStrokeColor, -R.attr.state_enabled)
                        .add(strokeColor).build()

                this@LabelButtonView.shapeAppearanceModel = borderShape.build()
                this@LabelButtonView.invalidate()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val strokeWidthPixels = strokeWidth

        val autoHeight = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY
        val autoWidth = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY
        if (autoHeight || autoWidth) {
            val twelveDp = ResourceUtils.dpToPx(context, 12).toInt()
            val horizontal = if (autoWidth) twelveDp else 0
            val vertical = if (autoHeight) twelveDp else 0
            setPadding(
                horizontal + strokeWidthPixels,
                vertical + strokeWidthPixels,
                horizontal + strokeWidthPixels,
                vertical + strokeWidthPixels
            )
        } else {
            setPadding(strokeWidthPixels, strokeWidthPixels, strokeWidthPixels, strokeWidthPixels)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun taps(): Flow<Unit> = debouncedClicks()

    private fun resolveText(state: ThomasState): String {
        val ref = state.resolveOptional(
            overrides = model.viewInfo.label.viewOverrides?.ref,
            default = model.viewInfo.label.ref
        )

        val text =  state.resolveRequired(
            overrides = model.viewInfo.label.viewOverrides?.text,
            default = model.viewInfo.label.text
        )

        return if (ref != null) {
            UAStringUtil.namedStringResource(context, ref, text)
        } else {
            text
        }
    }
}
