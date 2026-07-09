/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.content.Context
import android.os.Build
import android.view.View
import androidx.annotation.RestrictTo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import com.urbanairship.android.layout.BannerPresentation
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.Shadow
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout

/**
 * Positions and sizes banner content within the bounds of a banner host.
 *
 * Enter/exit animations, swipe to dismiss, and the auto-dismiss timer are handled by the
 * hosting view.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ThomasBannerView internal constructor(
    context: Context,
    private val model: AnyModel,
    private val presentation: BannerPresentation,
    private val environment: ViewEnvironment
) : ConstraintLayout(context) {

    private var bannerFrame: ConstrainedFrameLayout? = null

    /**
     * Listener notified when the banner frame's size changes. Used by the host to size
     * animations and swipe-to-dismiss gestures relative to the banner content.
     */
    public var frameSizeChangedListener: ((width: Int, height: Int) -> Unit)? = null

    init {
        id = model.viewId
        configureBanner()
    }

    private fun configureBanner() {
        val placement = presentation.getResolvedPlacement(context)
        val size = placement.size
        val frame = makeFrame(size)
        val containerView = model.createView(context, environment, null)
        frame.addView(containerView)
        addView(frame)
        LayoutUtils.applyBorderAndBackground(frame, null, placement.border, placement.backgroundColor)
        applyShadow(frame, placement.shadow)

        applySizeConstraints()
    }

    private fun makeFrame(size: ConstrainedSize) =
        ConstrainedFrameLayout(context, size).apply {
            id = generateViewId()
            layoutParams = LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT)
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val width = right - left
                val height = bottom - top
                if (width != oldRight - oldLeft || height != oldBottom - oldTop) {
                    frameSizeChangedListener?.invoke(width, height)
                }
            }
        }.also {
            bannerFrame = it
        }

    private fun applyShadow(view: View, shadow: Shadow?) {
        val androidShadow = shadow?.androidShadow
        if (androidShadow != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val color = androidShadow.color.resolve(context)
                view.outlineAmbientShadowColor = color
                view.outlineSpotShadowColor = color
            }
            view.elevation = ResourceUtils.dpToPx(context, androidShadow.elevation.toInt())
        } else {
            view.elevation = ResourceUtils.dpToPx(context, DEFAULT_ELEVATION_DP)
        }
    }

    /** Window bounds the size constraints were last applied for (loop/churn guard). */
    private var lastWindowWidth = 0
    private var lastWindowHeight = 0

    /**
     * Builds and applies the frame's size/position constraints, fitting any overflowing
     * `aspect_ratio` within the window (see [ConstraintSetBuilder.aspectRatioWithinBounds]).
     *
     * Re-resolves the placement because orientation may select a different one. Called from
     * [configureBanner] and [onConfigurationChanged] — the latter matters when the host activity
     * handles its own `configChanges` (no recreate), so the banner view persists and `init` never
     * re-runs; without re-applying here a portrait-fitted px box would stay stale in landscape.
     */
    private fun applySizeConstraints() {
        val frame = bannerFrame ?: return
        val placement = presentation.getResolvedPlacement(context)
        val viewId = frame.id
        val size = placement.size
        val margin = placement.margin

        val ignoreSafeArea = false

        // Percent base: the full window (matches ConstraintLayout's constrainPercent*).
        val windowWidthPx = ResourceUtils.getWindowWidthPixels(context, ignoreSafeArea)
        val windowHeightPx = ResourceUtils.getWindowHeightPixels(context, ignoreSafeArea)

        // Overflow bound / fitted-size target: window minus the frame's outer margins.
        val horizontalMargin = (margin?.start ?: 0) + (margin?.end ?: 0)
        val verticalMargin = (margin?.top ?: 0) + (margin?.bottom ?: 0)
        val horizontalMarginPx = ResourceUtils.dpToPx(context, horizontalMargin).toInt()
        val verticalMarginPx = ResourceUtils.dpToPx(context, verticalMargin).toInt()

        val availableWidthPx = (windowWidthPx - horizontalMarginPx).coerceAtLeast(0)
        val availableHeightPx = (windowHeightPx - verticalMarginPx).coerceAtLeast(0)

        lastWindowWidth = windowWidthPx
        lastWindowHeight = windowHeightPx

        ConstraintSetBuilder.newBuilder(context)
            .position(placement.position, viewId)
            .width(size, ignoreSafeArea, viewId)
            .height(size, ignoreSafeArea, viewId)
            .aspectRatioWithinBounds(
                size = size,
                viewId = viewId,
                windowWidthPx = windowWidthPx,
                windowHeightPx = windowHeightPx,
                availableWidthPx = availableWidthPx,
                availableHeightPx = availableHeightPx,
                ignoreSafeArea = ignoreSafeArea
            )
            .margin(margin, viewId)
            .build()
            .applyTo(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        // Re-fit only when the window bounds actually changed, to avoid layout churn.
        val windowWidth = ResourceUtils.getWindowWidthPixels(context, false)
        val windowHeight = ResourceUtils.getWindowHeightPixels(context, false)
        if (windowWidth != lastWindowWidth || windowHeight != lastWindowHeight) {
            applySizeConstraints()
        }
    }

    private companion object {
        private const val DEFAULT_ELEVATION_DP = 16
    }
}
