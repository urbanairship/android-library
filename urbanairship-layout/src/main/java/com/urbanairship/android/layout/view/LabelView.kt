/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.PrecomputedText
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.ReplacementSpan
import android.text.TextUtils
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.accessibility.AccessibilityEvent
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.ItemProperties
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils.spToPx
import com.urbanairship.android.layout.util.ifNotEmpty

internal class LabelView(
    context: Context,
    private val model: LabelModel,
    itemProperties: ItemProperties?
) : AppCompatTextView(context), BaseView {

    private var lastState: LabelModel.ResolvedState? = null

    /** Whether the layout gave this label a height for its text to fit into. */
    private val truncatesToHeight = itemProperties?.size?.height?.isAuto == false

    /**
     * Whether the dropped text is marked with an ellipsis.
     *
     * This should work back to API 28, but it was tested/confirmed to not actually work until 29.
     */
    private val marksTruncatedText =
        truncatesToHeight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Drives redraws for the inline icons, which an animated drawable can't do for itself.
     *
     * A compound drawable gets this from `TextView`, which registers itself as the callback; a
     * span drawable has none, so `invalidateSelf` goes nowhere and an animation advances its
     * animator while the pixels stay on frame one. It can look like it works whenever
     * something *else* is redrawing the label — changing text, a sibling animating — which is
     * exactly the case that hides it.
     *
     * [View] can't be the callback directly: `invalidateDrawable` only invalidates a drawable
     * `TextView.verifyDrawable` recognises, and a span's isn't one.
     */
    private val spanDrawableCallback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) = invalidate()

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            // An AnimatedVectorDrawable drives itself off its own animator, but a frame-list
            // drawable schedules through here.
            handler?.postAtTime(what, who, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            handler?.removeCallbacks(what, who)
        }
    }

    init {
        // Initial setup from the model
        setupInitialState()

        // Set the listener to handle state updates
        model.listener = createModelListener()
    }

    private fun setupInitialState() {
        if (marksTruncatedText) {
            ellipsize = TextUtils.TruncateAt.END
        }

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // When truncating to height, we need to measure once first so that we can know how many
        // lines exist, then we can clamp and remeasure with the number of lines that can fit.
        if (truncatesToHeight && clampMaxLinesTo(heightMeasureSpec)) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /**
     * Limit the line count to what [heightMeasureSpec] has room for.
     *
     * @return a `Boolean` that indicates whether a remeasure is needed.
     */
    private fun clampMaxLinesTo(heightMeasureSpec: Int): Boolean {
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            return false
        }

        val laidOut = layout ?: return false

        val available =
            MeasureSpec.getSize(heightMeasureSpec) - compoundPaddingTop - compoundPaddingBottom

        // Count up to see how many lines fit, based on the measured line height
        var fits = 0
        while (fits < laidOut.lineCount && laidOut.getLineBottom(fits) <= available) {
            fits++
        }

        // Always clamp to at least one line, even if it can't fit the text we need to draw into it.
        val clamped = fits.coerceAtLeast(1)

        // Return if we aren't changing anything. Setting maxLines triggers a layout,
        // so we need to bail out here to avoid a measurement loop.
        if (clamped == maxLines) {
            return false
        }

        maxLines = clamped
        return true
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        // Swap in a PrecomputedText to ensure we're using StaticLayout and not DynamicLayout.
        // We need to be a StaticLayout because maxLines and ellipsize won't work otherwise.
        super.setText(precomputedOrNull(text) ?: text, type)
    }

    /**
     * The [text] as a `PrecomputedText`, or `null` to set it unchanged.
     *
     * This only works on API 29+ (even though PrecomputedText is supposed to work in API 28).
     */
    private fun precomputedOrNull(text: CharSequence?): CharSequence? {
        if (!marksTruncatedText || text !is Spanned) {
            return null
        }

        // We shouldn't get here below Q because of the marksTruncatedText check above.
        // This check avoids a NewApi violation, because lint can't trace the earlier check.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }

        return PrecomputedText.create(text, textMetricsParams)
    }

    override fun onDraw(canvas: Canvas) {
        val laidOut = layout
        if (!truncatesToHeight || laidOut == null || laidOut.lineCount <= maxLines) {
            super.onDraw(canvas)
            return
        }

        // If we're on API 26-28, we have a DynamicLayout, which ignores maxLines. The clip that
        // TextView derives from extendedPaddingBottom isn't line-aligned, so the first dropped
        // line doesn't get cut cleanly. This clips at the line boundary instead.
        canvas.save()
        canvas.clipRect(0, 0, width, extendedPaddingTop + laidOut.getLineTop(maxLines))
        super.onDraw(canvas)
        canvas.restore()
    }

    override fun scrollTo(x: Int, y: Int) {
        // If we're truncating to height, block the LinkMovementMethodCompat from scrolling the
        // TextView, because we don't want it to steal the drag gesture from a scrolling parent.
        super.scrollTo(x, if (truncatesToHeight) 0 else y)
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

        val size = resolvedState.textAppearance.fontSize

        LayoutUtils.applyLabel(
            this,
            resolvedState.textAppearance,
            model.viewInfo.markdownOptions,
            resolvedState.text
        )

        // Both icons ride in the text rather than in compound drawables. A compound drawable
        // sits against the view's content edge, which on a label wider than its text — a
        // centred button label, say — strands the icon away from the words it belongs to and
        // makes the authored `space` between them meaningless. Inline, the icons travel with
        // whatever alignment applies and `space` means the same thing at either end.
        val start = inlineIcon(resolvedState.iconStart, size, HorizontalPosition.START)
        val end = inlineIcon(resolvedState.iconEnd, size, HorizontalPosition.END)

        if (start != null || end != null) {
            val labelText = text
            text = SpannableStringBuilder().apply {
                start?.let { appendIcon(it) }
                append(labelText)
                end?.let { appendIcon(it) }
            }
        }

        this.lastState = resolvedState
    }

    /**
     * An icon to render inside the label's text, at one end of it.
     *
     * @param drawable The icon, sized to the text.
     * @param space The authored gap between icon and text, in px.
     * @param position Which end of the text it belongs to.
     */
    private class InlineIcon(
        val drawable: Drawable,
        val space: Int,
        val position: HorizontalPosition
    )

    /**
     * Returns [iconInfo] as an icon to render in the text, or null when there is no icon.
     *
     * The drawable carries no gap of its own — see [appendIcon] — so nothing here resolves a
     * direction.
     *
     * @param iconInfo The icon.
     * @param size The text size to match, in sp.
     * @param position Which end of the text the icon belongs to.
     * @return The icon, or null.
     */
    private fun inlineIcon(
        iconInfo: LabelInfo.LabelIcon?,
        size: Int,
        position: HorizontalPosition
    ): InlineIcon? {
        val resolvedIcon = iconInfo as? LabelInfo.LabelIcon.Floating ?: return null
        val drawable = resolvedIcon.icon.getDrawable(context, isEnabled, position) ?: return null

        val sizePx = spToPx(context, size).toInt()
        drawable.setBounds(0, 0, sizePx, sizePx)

        return InlineIcon(
            drawable = drawable,
            space = spToPx(context, resolvedIcon.space).toInt(),
            position = position
        )
    }

    /**
     * Appends [icon] and its gap as inline spans.
     *
     * The gap is a sized span over a space character rather than padding on the drawable, so
     * that it is part of the text run: two neutrals beside a directional run are reordered
     * together, which puts the gap between the icon and the words in either direction without
     * anything here resolving one. [InlineIcon.position] only decides their order in the
     * string — the bidi algorithm decides what that order means on screen.
     *
     * @param icon The icon to append.
     */
    private fun SpannableStringBuilder.appendIcon(icon: InlineIcon) {
        icon.drawable.callback = spanDrawableCallback

        fun appendSpan(placeholder: String, span: Any) {
            val start = length
            append(placeholder)
            setSpan(span, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val gap = GapSpan(icon.space)
        val image = CenteredImageSpan(icon.drawable)

        when (icon.position) {
            HorizontalPosition.START -> {
                appendSpan(ICON_PLACEHOLDER, image)
                appendSpan(GAP_PLACEHOLDER, gap)
            }
            else -> {
                appendSpan(GAP_PLACEHOLDER, gap)
                appendSpan(ICON_PLACEHOLDER, image)
            }
        }
    }

    private companion object {
        /** Stands in for an inline icon; `U+FFFC` is not spoken by screen readers. */
        const val ICON_PLACEHOLDER = "\uFFFC"

        /** Carries the gap span. A real space, so it reorders with the icon beside it. */
        const val GAP_PLACEHOLDER = " "
    }
}

/**
 * An [ImageSpan] centred on the text it sits in, taking only width and leaving the line's
 * metrics to the text.
 *
 * [ImageSpan.ALIGN_CENTER] does this from API 29 on; below that the base class can only stand
 * the drawable on the baseline, which reads as too high beside a square glyph.
 */
private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = drawable.bounds.width()

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val metrics = paint.fontMetricsInt
        val center = y + (metrics.ascent + metrics.descent) / 2
        canvas.withTranslation(x, center - drawable.bounds.height() / 2f) {
            drawable.draw(this)
        }
    }
}

/**
 * Occupies [width] px of a text run and draws nothing.
 *
 * Carries the gap between an inline icon and the text as part of the run, so the bidi
 * algorithm places it rather than anything having to resolve a direction.
 */
private class GapSpan(private val width: Int) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = width

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ): Unit = Unit
}
