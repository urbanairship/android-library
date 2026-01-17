/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import android.text.Html
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.TypefaceCompat
import com.urbanairship.Fonts
import com.urbanairship.android.layout.info.BaseToggleLayoutInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.ButtonLayoutModel
import com.urbanairship.android.layout.model.TextInputModel
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.MarkdownOptions
import com.urbanairship.android.layout.property.SwitchStyle
import com.urbanairship.android.layout.property.TapEffect
import com.urbanairship.android.layout.property.TextAlignment
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.android.layout.property.TextStyle
import com.urbanairship.android.layout.property.isEnabled
import com.urbanairship.android.layout.widget.Clippable
import java.util.Arrays
import kotlin.math.roundToInt
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Helpers for layout rendering.
 *
 * @hide
 */
internal object LayoutUtils {

    const val HOVERED_ALPHA_PERCENT: Float = 0.1f
    const val PRESSED_ALPHA_PERCENT: Float = 0.2f
    const val DEFAULT_BORDER_RADIUS: Int = 0

    private const val MATERIAL_ALPHA_LOW = 0.32f
    const val MATERIAL_ALPHA_DISABLED = 0.38f

    private const val NBSP = "\u00A0"
    private const val NARROW_NBSP = "\u202F"

    internal fun updateBackground(
        view: View,
        baseBackground: Drawable?,
        oldBackground: Background?,
        newBackground: Background
    ) {
        val borderPadding = oldBackground?.border?.strokeWidth?.let { dpToPx(view.context, it) }
        borderPadding?.let { removePadding(view, it) }

        applyBorderAndBackground(view, baseBackground, newBackground.border, newBackground.color)
    }

    fun updateBackground(view: View, oldBackground: Background?, newBackground: Background) {
        updateBackground(view, null, oldBackground, newBackground)
    }

    fun applyBorderAndBackground(
        view: View, baseBackground: Drawable?, border: Border?, backgroundColor: Color?
    ) {
        val context = view.context

        if (border == null) {
            if (backgroundColor != null) {
                applyBackgrounds(view, baseBackground, ColorDrawable(backgroundColor.resolve(context)))
            }
            return
        }

        val shapeModel = ShapeAppearanceModel.builder()
        border.applyToShape(shapeModel) { dpToPx(context, it) }

        val shapeDrawable = MaterialShapeDrawable(shapeModel.build())
        if (view is Clippable) {
            border.applyToClippable(view as Clippable) {
                ResourceUtils.dpToPx(context, it)
            }
        }

        val borderPadding = border.strokeWidth?.let { width ->
            val stroke = dpToPx(context, width)
            shapeDrawable.strokeWidth = stroke.toFloat()
            stroke
        }

        if (border.strokeColor != null) {
            @ColorInt val strokeColor = border.strokeColor.resolve(context)
            shapeDrawable.strokeColor = ColorStateListBuilder()
                .add(generateDisabledColor(strokeColor), -R.attr.state_enabled)
                .add(strokeColor)
                .build()
        }

        @ColorInt val fillColor = backgroundColor?.resolve(context) ?: Color.TRANSPARENT
        shapeDrawable.fillColor = ColorStateListBuilder()
            .add(generateDisabledColor(fillColor), -R.attr.state_enabled)
            .add(fillColor)
            .build()

        applyBackgrounds(view, baseBackground, shapeDrawable)

        borderPadding?.let { addPadding(view, it) }
    }

    fun applyMediaVideoBorderAndBackground(
        view: View, baseBackground: Drawable?, border: Border?, backgroundColor: Color?
    ) {
        val context = view.context

        if (border == null) {
            if (backgroundColor != null) {
                applyBackgrounds(view, baseBackground, ColorDrawable(backgroundColor.resolve(context)))
            }
            return
        }

        val shapeModel = ShapeAppearanceModel.builder()
        border.applyToShape(shapeModel) { dpToPx(context, it) }

        val backgroundDrawable = MaterialShapeDrawable(shapeModel.build())
        if (view is Clippable) {
            border.applyToClippable(view as Clippable) {
                ResourceUtils.dpToPx(context, it)
            }
        }

        val strokeDrawable = MaterialShapeDrawable(shapeModel.build())
        strokeDrawable.fillColor = ColorStateListBuilder().add(Color.TRANSPARENT).build()

        val borderPadding = border.strokeWidth?.let { width ->
            val stroke = dpToPx(context, width)
            strokeDrawable.strokeWidth = stroke.toFloat()
            stroke
        }

        if (border.strokeColor != null) {
            @ColorInt val strokeColor = border.strokeColor.resolve(context)
            strokeDrawable.strokeColor = ColorStateListBuilder()
                .add(generateDisabledColor(strokeColor), -R.attr.state_enabled)
                .add(strokeColor)
                .build()
        }

        @ColorInt val fillColor = backgroundColor?.resolve(context) ?: Color.TRANSPARENT
        backgroundDrawable.fillColor = ColorStateListBuilder()
            .add(generateDisabledColor(fillColor), -R.attr.state_enabled)
            .add(fillColor)
            .build()

        // Set the background color and apply the stroke as the foreground
        applyBackgrounds(view, baseBackground, backgroundDrawable)
        view.foreground = strokeDrawable

        borderPadding?.let { addPadding(view, it) }
    }

    fun applyMediaImageBorderAndBackground(
        view: ShapeableImageView,
        baseBackground: Drawable?,
        border: Border?,
        backgroundColor: Color?
    ) {
        val context = view.context

        if (border == null) {
            if (backgroundColor != null) {
                applyBackgrounds(view, baseBackground, ColorDrawable(backgroundColor.resolve(context)))
            }
            return
        }

        val shapeModel = ShapeAppearanceModel.builder()
        border.applyToShape(shapeModel) { dpToPx(context, it) }

        if (border.strokeWidth != null) {
            val strokeWidth = dpToPx(context, border.strokeWidth).toFloat()
            view.strokeWidth = strokeWidth
        }

        if (border.strokeColor != null) {
            @ColorInt val strokeColor = border.strokeColor.resolve(context)
            view.strokeColor = ColorStateListBuilder()
                .add(generateDisabledColor(strokeColor), -R.attr.state_enabled)
                .add(strokeColor)
                .build()
        }

        @ColorInt val fillColor = backgroundColor?.resolve(context) ?: Color.TRANSPARENT
        view.setBackgroundColor(fillColor)

        view.setShapeAppearanceModel(shapeModel.build())
    }

    private fun applyBackgrounds(view: View, base: Drawable?, drawable: Drawable) {
        view.background = base
            ?.let { LayerDrawable(arrayOf(it, drawable)) }
            ?: drawable
    }

    fun applyButtonLayoutModel(button: FrameLayout, model: ButtonLayoutModel) {
        when(model.viewInfo.tapEffect) {
            TapEffect.None -> button.foreground = null
            TapEffect.Default -> {
                val border = model.viewInfo.border ?: return
                val radii = border.radii { ResourceUtils.dpToPx(button.context, it) } ?: return
                applyRippleEffect(button, radii)
            }
        }
    }

    fun applyToggleLayoutRippleEffect(toggle: FrameLayout, info: BaseToggleLayoutInfo) {
        val border = info.border

        val radii = border
            ?.let { it.radii { value -> ResourceUtils.dpToPx(toggle.context, value) } }
            ?: run {
                val result = FloatArray(8)
                Arrays.fill(result, dpToPx(toggle.context, DEFAULT_BORDER_RADIUS).toFloat())
                result
            }

        applyRippleEffect(toggle, radii)
    }

    private fun generateRippleDrawable(context: Context, radii: FloatArray?): RippleDrawable {
        val mask = ShapeDrawable(RoundRectShape(radii, null, null))
        val colors = MaterialColors.getColorStateList(
            context,
            androidx.appcompat.R.attr.colorControlHighlight,
            ColorStateList.valueOf(Color.TRANSPARENT)
        )

        return RippleDrawable(colors, null, mask)
    }

    fun applyRippleEffect(frameLayout: FrameLayout, radii: FloatArray?) {
        val ripple = generateRippleDrawable(frameLayout.context, radii)
        frameLayout.foreground = ripple
    }

    /** Applies a ripple effect and tint list to handle various interactions with an ImageButton button.  */
    fun applyImageButtonRippleAndTint(view: ImageButton, borderRadius: Int?) {
        val radii = FloatArray(8)
        val converted = borderRadius
            ?.let { dpToPx(view.context, it) }?.toFloat()
            ?: 0f

        Arrays.fill(radii, converted)
        applyImageButtonRippleAndTint(view, radii)
    }

    /** Applies a ripple effect and tint list to handle various interactions with an ImageButton button.  */
    fun applyImageButtonRippleAndTint(view: ImageButton, radii: FloatArray?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            applyImageButtonRippleAndTintApi23(view, radii)
        } else {
            applyImageButtonRippleAndTintCompat(view, radii)
        }
    }

    /** Applies a ripple effect to the view's foreground and sets a disabled color for API 23 and above.  */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun applyImageButtonRippleAndTintApi23(view: ImageButton, radii: FloatArray?) {
        // Sets the view's foreground to a ripple drawable
        view.foreground = generateRippleDrawable(view.context, radii)

        // Discard source pixels that don't overlap the destination pixels
        view.imageTintMode = PorterDuff.Mode.SRC_ATOP

        // Using transparent as the normal color means no tint unless the image is disabled
        val normalColor = Color.TRANSPARENT
        val compatStateList = ColorStateListBuilder()
            .add(generateDisabledColor(normalColor), -R.attr.state_enabled)
            .add(normalColor)
            .build()

        view.imageTintList = compatStateList
    }

    /** Applies a compat tap effect that is similar to a ripple, and disabled/hover colors for API 22 and below.  */
    private fun applyImageButtonRippleAndTintCompat(view: ImageButton, radii: FloatArray?) {
        // Discard source pixels that don't overlap the destination pixels
        view.imageTintMode = PorterDuff.Mode.SRC_ATOP

        // Using transparent as the color means no tint unless the image is pressed or disabled
        val compatStateList = pressedColorStateList(Color.TRANSPARENT)

        view.imageTintList = compatStateList
    }

    fun applyLabel(
        textView: TextView,
        textAppearance: TextAppearance,
        markdownOptions: MarkdownOptions?,
        text: String
    ) {
        var text = text
        applyTextAppearance(textView, textAppearance)

        // Work around TextView rendering issues that cause ends of lines to be clipped off when using certain
        // fancy custom fonts that aren't measured properly. We use a full non-breaking space for italic text and a
        // narrow non-breaking space for non-italic text to minimize the impact on the overall layout. The issue
        // also occurs for end-justified multiline text, but that's a bit harder to address in a reasonable way, so
        // we'll consider it an edge-case for now.
        val fonts = Fonts.shared(textView.context)
        val isCustomFont = textAppearance.fontFamilies.any { !fonts.isSystemFont(it) }

        val isItalic = textAppearance.textStyles.contains(TextStyle.ITALIC)
        if (isCustomFont && isItalic) {
            text += NBSP
        } else if (isCustomFont || isItalic) {
            text += NARROW_NBSP
        }

        val context = textView.context
        val isMarkdownEnabled = markdownOptions.isEnabled

        if (isMarkdownEnabled) {
            val html = Html.fromHtml(text.markdownToHtml())
            textView.setHtml(context, html, markdownOptions)
        } else {
            textView.text = text
        }
    }

    fun applyTextInputModel(editText: AppCompatEditText, textInput: TextInputModel) {
        val isMultiline = textInput.viewInfo.inputType == FormInputType.TEXT_MULTILINE
        applyTextAppearance(editText, textInput.viewInfo.textAppearance)
        val padding = dpToPx(editText.context, 8)
        editText.setPadding(padding, padding, padding, padding)
        editText.inputType = textInput.viewInfo.inputType.typeMask
        editText.isSingleLine = !isMultiline
        editText.gravity = editText.gravity or  // Vertically center single line text inputs, or top align multiline text inputs
                (if (isMultiline) Gravity.TOP else Gravity.CENTER_VERTICAL)

        if (!textInput.viewInfo.hintText.isNullOrEmpty()) {
            editText.hint = textInput.viewInfo.hintText
            textInput.viewInfo.textAppearance.hintColor?.let {
                editText.setHintTextColor(it.resolve(editText.context))
            }
        }
    }

    @JvmStatic
    fun applyTextAppearance(textView: TextView, textAppearance: TextAppearance) {
        val context = textView.context

        textView.textSize = textAppearance.fontSize.toFloat()

        val textColor = textAppearance.color.resolve(context)
        val disabledTextColor = generateDisabledColor(Color.TRANSPARENT, textColor)

        textView.setTextColor(
            ColorStateListBuilder()
                .add(disabledTextColor, -R.attr.state_enabled)
                .add(textColor)
                .build()
        )

        var fontWeight = 0
        var italic = false
        var paintFlags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG

        for (style in textAppearance.textStyles) {
            when (style) {
                TextStyle.BOLD -> fontWeight = 700 // Set the font weight to Bold (700).
                TextStyle.ITALIC -> italic = true
                TextStyle.UNDERLINE -> paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

        // If font_weight is provided, fallback to it.
        if (textAppearance.fontWeight != 0) {
            fontWeight = roundFontWeight(textAppearance.fontWeight)
        }

        // If neither font_weight nor bold are provided, fallback to normal weight.
        if (fontWeight == 0) {
            // Default to Regular Weight (400).
            fontWeight = 400
        }

        when (textAppearance.alignment) {
            TextAlignment.CENTER -> textView.gravity = Gravity.CENTER
            TextAlignment.START -> textView.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            TextAlignment.END -> textView.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        val family = getTypeFace(context, textAppearance.fontFamilies)
        val typeface = TypefaceCompat.create(context, family, fontWeight, italic)

        textView.setTypeface(typeface)
        textView.paintFlags = paintFlags

        textView.setLineSpacing(0f, textAppearance.lineHeightMultiplier?.toFloat() ?: 1f)

        // Letter spacing is in EM units
        textView.letterSpacing = textAppearance.kerning?.toFloat()?.let { kerning ->
            val size = textAppearance.fontSize.toFloat()
            if (size > 0) kerning / size else 0f
        } ?: 0f
    }

    /**
     * Finds the first available font in the list.
     *
     * @param context The application context.
     * @param fontFamilies The list of font families.
     * @return The typeface with a specified font, or null if the font was not found.
     */
    private fun getTypeFace(context: Context, fontFamilies: List<String>): Typeface? {
        val fonts = Fonts.shared(context)

        return fontFamilies
            .filter { !it.isEmpty() }
            .firstNotNullOfOrNull { fonts.getFontFamily(it) }
    }

    /** @hide
     */
    fun applySwitchStyle(view: SwitchCompat, style: SwitchStyle) {
        val context = view.context

        val trackOn = style.onColor.resolve(context)
        val trackOff = style.offColor.resolve(context)

        val thumbOn = MaterialColors.layer(Color.WHITE, trackOn, MATERIAL_ALPHA_LOW)
        val thumbOff = MaterialColors.layer(Color.WHITE, trackOff, MATERIAL_ALPHA_LOW)

        view.setTrackTintList(checkedColorStateList(trackOn, trackOff))
        view.setThumbTintList(checkedColorStateList(thumbOn, thumbOff))

        view.setBackgroundResource(com.urbanairship.android.layout.R.drawable.ua_layout_imagebutton_ripple)

        view.gravity = Gravity.CENTER
    }

    private fun checkedColorStateList(
        @ColorInt checkedColor: Int, @ColorInt normalColor: Int
    ): ColorStateList {
        return ColorStateListBuilder()
            .add(
                color = generateDisabledColor(checkedColor),
                R.attr.state_checked, -R.attr.state_enabled)
            .add(
                color = generateDisabledColor(normalColor),
                -R.attr.state_checked, -R.attr.state_enabled)
            .add(checkedColor, R.attr.state_checked)
            .add(normalColor)
            .build()
    }

    fun pressedColorStateList(@ColorInt normalColor: Int): ColorStateList {
        return ColorStateListBuilder()
            .add(
                color = generatePressedColor(normalColor, Color.BLACK),
                R.attr.state_pressed)
            .add(
                color = generateHoveredColor(normalColor, Color.BLACK),
                R.attr.state_hovered)
            .add(
                color = generateDisabledColor(normalColor), -R.attr.state_enabled)
            .add(normalColor)
            .build()
    }

    fun dismissSoftKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            ?: return

        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    @JvmStatic
    fun doOnAttachToWindow(view: View, callback: Runnable) {
        view.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                callback.run()
            }

            override fun onViewDetachedFromWindow(v: View) { /* no-op */
            }
        })
    }

    fun addPadding(view: View, padding: Int) {
        addPadding(view, padding, padding, padding, padding)
    }

    fun removePadding(view: View, padding: Int) {
        addPadding(view, -padding, -padding, -padding, -padding)
    }

    fun addPadding(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        view.setPadding(
            view.paddingLeft + left,
            view.paddingTop + top,
            view.paddingRight + right,
            view.paddingBottom + bottom
        )
    }

    @ColorInt
    fun generatePressedColor(@ColorInt baseColor: Int): Int {
        return generatePressedColor(baseColor, Color.WHITE)
    }

    @ColorInt
    fun generateDisabledColor(@ColorInt baseColor: Int): Int {
        return generateDisabledColor(baseColor, Color.WHITE)
    }

    @ColorInt
    fun generateHoveredColor(@ColorInt baseColor: Int): Int {
        return generateHoveredColor(baseColor, Color.WHITE)
    }

    @ColorInt
    fun generatePressedColor(@ColorInt background: Int, @ColorInt foreground: Int): Int {
        return overlayColors(background, foreground, PRESSED_ALPHA_PERCENT)
    }

    @ColorInt
    fun generateDisabledColor(@ColorInt background: Int, @ColorInt foreground: Int): Int {
        return overlayColors(background, foreground, MATERIAL_ALPHA_DISABLED)
    }

    @ColorInt
    fun generateHoveredColor(@ColorInt background: Int, @ColorInt foreground: Int): Int {
        return overlayColors(background, foreground, HOVERED_ALPHA_PERCENT)
    }

    @ColorInt
    private fun overlayColors(
        @ColorInt backgroundColor: Int,
        @ColorInt overlayColor: Int,
        @FloatRange(from = 0.0, to = 1.0) overlayAlpha: Float
    ): Int {
        val alpha = (Color.alpha(overlayColor) * overlayAlpha).roundToInt()
        val overlay = ColorUtils.setAlphaComponent(overlayColor, alpha)
        return ColorUtils.compositeColors(overlay, backgroundColor)
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    @IntRange(from = 0, to = 900)
    private fun roundFontWeight(fontWeight: Int): Int {
        // Round to nearest hundred
        val rounded = (fontWeight / 100f).roundToInt() * 100

        // Clamp to valid range [100, 900]
        return rounded.coerceIn(100, 900)
    }
}
