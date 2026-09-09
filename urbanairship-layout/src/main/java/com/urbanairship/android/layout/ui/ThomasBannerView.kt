/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.transition.TransitionManager
import android.view.View
import androidx.annotation.RestrictTo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.urbanairship.android.layout.BannerPresentation
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.BannerAnimation
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.Shadow
import com.urbanairship.android.layout.property.VerticalPosition
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
     * The banner's configured animation, defaulting to a slide when the payload omits one.
     */
    private val bannerAnimation: BannerAnimation
        get() = presentation.getResolvedPlacement(context).animation ?: BannerAnimation.default()

    /**
     * The edge the banner slides from/to.
     */
    private val bannerPosition: VerticalPosition
        get() = presentation.getResolvedPlacement(context).position.vertical

    /**
     * Listener notified when the banner frame's bounds (relative to this view) change. Used by
     * the host to size and position animations and swipe-to-dismiss gestures relative to the
     * banner content.
     */
    public var frameBoundsChangedListener: ((Rect) -> Unit)? = null

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

        // A single listener drives inset handling for both branches. It recomputes from the root
        // window insets (see applyWindowInsets) rather than trusting the dispatched value, so the
        // result is identical across API levels (pre-30 real consumption vs 30+ no-op) and whether
        // the host app is edge-to-edge or not. The incoming insets are returned unchanged so we
        // never affect the host's own views.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            applyWindowInsets()
            insets
        }

        animateIn(frame)
    }

    /**
     * Applies window insets from the canonical, consumption-independent source
     * ([ViewCompat.getRootWindowInsets]), so behavior is consistent on every supported API and
     * regardless of the host's edge-to-edge state.
     *
     * - Safe-area banners: pad only the pinned edge (plus horizontal system-bar/cutout insets, plus
     *   the IME for a bottom banner). The subtree is intentionally not re-dispatched, since the
     *   whole banner is already inside the safe area.
     * - Ignore-safe-area banners: leave the frame edge-to-edge, but hand the Thomas subtree the same
     *   canonical insets so nested items that respect the safe area behave the same everywhere.
     */
    private fun applyWindowInsets() {
        val insets = ViewCompat.getRootWindowInsets(this) ?: return

        if (environment.isIgnoringSafeAreas) {
            bannerFrame?.let { ViewCompat.dispatchApplyWindowInsets(it, insets) }
            return
        }

        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        val isTop = bannerPosition == VerticalPosition.TOP
        val isBottom = bannerPosition == VerticalPosition.BOTTOM
        updatePadding(
            top = if (isBottom) 0 else bars.top,
            bottom = if (isTop) 0 else maxOf(bars.bottom, ime.bottom),
            left = bars.left,
            right = bars.right
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Apply immediately from the root insets so the first frame is correct even if no dispatch
        // is pending, then request a pass for later changes (rotation, IME).
        applyWindowInsets()
        ViewCompat.requestApplyInsets(this)
    }

    /** Runs the enter transition on the [frame] via the [TransitionFactory]. */
    private fun animateIn(frame: View) {
        frame.visibility = INVISIBLE
        post {
            val transition = TransitionFactory.bannerTransition(bannerAnimation.enter, frame, bannerPosition)
            TransitionManager.beginDelayedTransition(this, transition)
            frame.visibility = VISIBLE
        }
    }

    private fun makeFrame(size: ConstrainedSize) =
        ConstrainedFrameLayout(context, size).apply {
            id = generateViewId()
            layoutParams = LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT)
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                    frameBoundsChangedListener?.invoke(Rect(left, top, right, bottom))
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

        val ignoreSafeArea = placement.shouldIgnoreSafeArea()

        // Percent base for the overflow/fitting math: the full window. Note that
        // ConstraintLayout's constrainPercent* sizing is parent-relative, so this base only
        // matches the frame's actual percent sizing when the banner host fills the window.
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
            .position(placement.position.asPosition(), viewId)
            .width(size, ignoreSafeArea, viewId, margin = margin)
            .height(size, ignoreSafeArea, viewId, margin = margin)
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
        val ignoreSafeArea = presentation.getResolvedPlacement(context).shouldIgnoreSafeArea()
        val windowWidth = ResourceUtils.getWindowWidthPixels(context, ignoreSafeArea)
        val windowHeight = ResourceUtils.getWindowHeightPixels(context, ignoreSafeArea)
        if (windowWidth != lastWindowWidth || windowHeight != lastWindowHeight) {
            applySizeConstraints()
        }
    }

    private companion object {
        private const val DEFAULT_ELEVATION_DP = 16
    }
}
