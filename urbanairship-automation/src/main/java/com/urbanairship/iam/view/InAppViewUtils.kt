/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ImageSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.RestrictTo
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import com.urbanairship.Fonts
import com.urbanairship.UALog.d
import com.urbanairship.UALog.e
import com.urbanairship.automation.R
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageMediaInfo
import com.urbanairship.iam.info.InAppMessageTextInfo
import com.urbanairship.util.UAStringUtil
import java.lang.ref.WeakReference
import kotlin.math.max

/**
 * In-app view utils.
 */
internal object InAppViewUtils {

    private const val PRESSED_ALPHA_PERCENT = .2f
    private const val DEFAULT_BORDER_RADIUS = 0f

    /**
     * Applies button info to a button.
     *
     * @param button The button view.
     * @param buttonInfo The button info.
     * @param borderRadiusFlag The border radius flag.
     * @param strokeWidthInDps The stroke width in dps.
     */
    fun applyButtonInfo(
        button: Button,
        buttonInfo: InAppMessageButtonInfo,
        @BorderRadius.BorderRadiusFlag borderRadiusFlag: Int,
        strokeWidthInDps: Int
    ) {
        applyButtonTextInfo(button, buttonInfo.label)
        val textColor = buttonInfo.label.color?.color ?: button.currentTextColor
        val backgroundColor = buttonInfo.backgroundColor?.color ?: Color.TRANSPARENT
        val pressedColor = ColorUtils.setAlphaComponent(
            textColor,
            Math.round(Color.alpha(textColor) * PRESSED_ALPHA_PERCENT)
        )
        val strokeColor = buttonInfo.borderColor?.color ?: backgroundColor
        val borderRadius = buttonInfo.borderRadius ?: DEFAULT_BORDER_RADIUS
        val background =
            BackgroundDrawableBuilder.newBuilder(button.context)
                .setBackgroundColor(backgroundColor)
                .setBorderRadius(borderRadius, borderRadiusFlag)
                .setPressedColor(pressedColor)
                .setStrokeColor(strokeColor)
                .setStrokeWidth(strokeWidthInDps)
                .build()
        ViewCompat.setBackground(button, background)
    }

    /**
     * Applies button info to a button.
     *
     * @param button The button view.
     * @param buttonInfo The button info.
     * @param borderRadiusFlag The border radius flag.
     */
    fun applyButtonInfo(
        button: Button,
        buttonInfo: InAppMessageButtonInfo,
        @BorderRadius.BorderRadiusFlag borderRadiusFlag: Int
    ) {
        val strokeWidth = try {
            button.context.resources.getInteger(R.integer.ua_iam_button_stroke_width_dps)
        } catch (e: Resources.NotFoundException) {
            2
        }
        applyButtonInfo(button, buttonInfo, borderRadiusFlag, strokeWidth)
    }

    /**
     * Applies text info to a text view with a center gravity.
     *
     * @param textView The text view.
     * @param textInfo The text info.
     */
    fun applyButtonTextInfo(textView: TextView, textInfo: InAppMessageTextInfo) {
        applyTextInfo(textView, textInfo, Gravity.CENTER)
    }

    /**
     * Applies text info to a text view with a horizontal center gravity.
     *
     * @param textView The text view.
     * @param textInfo The text info.
     */
    fun applyTextInfo(textView: TextView, textInfo: InAppMessageTextInfo) {
        applyTextInfo(textView, textInfo, Gravity.CENTER_HORIZONTAL)
    }

    /**
     * Applies text info to a text view.
     *
     * @param textView The text view.
     * @param textInfo The text info.
     * @param centerGravity The gravity center to use for center alignment.
     */
    private fun applyTextInfo(textView: TextView, textInfo: InAppMessageTextInfo, centerGravity: Int) {
        textInfo.size?.let(textView::setTextSize)
        textInfo.color?.color?.let(textView::setTextColor)

        var drawable: Drawable? = null
        @DrawableRes val drawableId = textInfo.getDrawable(textView.context)
        if (drawableId != 0) {
            try {
                drawable = ContextCompat.getDrawable(textView.context, drawableId)
            } catch (e: Resources.NotFoundException) {
                d("Drawable $drawableId no longer exists.")
            }
        }
        if (drawable != null) {
            val size = Math.round(textView.textSize)
            val color = textView.currentTextColor
            try {
                val wrappedDrawable = DrawableCompat.wrap(drawable).mutate()
                wrappedDrawable.setBounds(0, 0, size, size)
                wrappedDrawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
                val imageSpan = CenteredImageSpan(wrappedDrawable)
                textView.text = SpannableString("  " + textInfo.text).apply {
                    setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(RemoveUnderlineSpan(), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } catch (e: Resources.NotFoundException) {
                e(e, "Unable to find button drawable.")
                textView.text = textInfo.text
            }
        } else {
            textView.text = textInfo.text
        }
        var typefaceFlags = textView.typeface.style
        var paintFlags = textView.paintFlags or Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        for (style in textInfo.style ?: emptyList()) {
            when (style) {
                InAppMessageTextInfo.Style.BOLD -> typefaceFlags = typefaceFlags or Typeface.BOLD
                InAppMessageTextInfo.Style.ITALIC -> typefaceFlags = typefaceFlags or Typeface.ITALIC
                InAppMessageTextInfo.Style.UNDERLINE -> paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
        textInfo.alignment?.let {
            val gravity = when(it) {
                InAppMessageTextInfo.Alignment.LEFT -> Gravity.START
                InAppMessageTextInfo.Alignment.CENTER -> centerGravity
                InAppMessageTextInfo.Alignment.RIGHT -> Gravity.END
            }
            textView.gravity = gravity
        }
        val typeface = getTypeFace(textView.context, textInfo.fontFamilies) ?: textView.typeface
        textView.setTypeface(typeface, typefaceFlags)
        textView.paintFlags = paintFlags
    }

    /**
     * Finds the first available font in the list.
     *
     * @param context The application context.
     * @param fontFamilies The list of font families.
     * @return The typeface with a specified font, or null if the font was not found.
     */
    private fun getTypeFace(context: Context, fontFamilies: List<String>?): Typeface? {
        if (fontFamilies == null) { return null }

        for (fontFamily in fontFamilies) {
            if (UAStringUtil.isEmpty(fontFamily)) {
                continue
            }
            val typeface = Fonts.shared(context).getFontFamily(fontFamily)
            if (typeface != null) {
                return typeface
            }
        }
        return null
    }

    /**
     * Loads the media info into the media view and scales the media's views height to match the
     * aspect ratio of the media. If the aspect ratio is unavailable in the cache, 16:9 will be used.
     *
     * @param mediaView The media view.
     * @param mediaInfo The media info.
     * @param assets The cached assets.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun loadMediaInfo(mediaView: MediaView, mediaInfo: InAppMessageMediaInfo, assets: AirshipCachedAssets?) {
        if (mediaView.width == 0) {
            val weakReference = WeakReference(mediaView)

            mediaView.doOnPreDraw {
                val local = weakReference.get() ?: return@doOnPreDraw
                loadMediaInfo(local, mediaInfo, assets)
            }
        } else {
            // Default to a 16:9 aspect ratio
            var width = 16
            var height = 9
            val cachedLocation = assets?.let {
                val remote = mediaInfo.url
                val result = it.cacheUri(remote)?.toString() ?: return@let null

                val size = it.getMediaSize(remote)
                width = maxOf(size.width, width)
                height = maxOf(size.height, height)

                result
            }
            val params = mediaView.layoutParams

            // Check if we can grow the image horizontally to fit the width
            if (params.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                val scale = mediaView.width.toFloat() / width.toFloat()
                params.height = Math.round(scale * height)
            } else {
                val imageRatio = width.toFloat() / height.toFloat()
                val viewRatio = mediaView.width.toFloat() / mediaView.height
                if (imageRatio >= viewRatio) {
                    // Image is wider than the view
                    params.height = Math.round(mediaView.width / imageRatio)
                } else {
                    // View is wider than the image
                    params.width = Math.round(mediaView.height * imageRatio)
                }
            }
            mediaView.layoutParams = params
            mediaView.setMediaInfo(mediaInfo, cachedLocation)
        }
    }

    /**
     * Returns the largest child Z value in the view group.
     *
     * @param group The view group.
     * @return The largest child Z value in the view group.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun getLargestChildZValue(group: ViewGroup): Float {
        var z = 0f
        for (i in 0 until group.childCount) {
            z = max(group.getChildAt(0).z, z)
        }
        return z
    }

    /**
     * Span that removes underline.
     */
    private class RemoveUnderlineSpan : CharacterStyle() {

        override fun updateDrawState(textPaint: TextPaint) {
            textPaint.isUnderlineText = false
        }
    }

    /**
     * Centered image span.
     */
    private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            canvas.save()
            val drawable = drawable
            val dy = bottom - drawable.bounds.bottom - paint.fontMetricsInt.descent / 2
            canvas.translate(x, dy.toFloat())
            drawable.draw(canvas)
            canvas.restore()
        }
    }
}
