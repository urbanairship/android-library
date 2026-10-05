/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.view.ViewGroup
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.property.EnvironmentConditionsContext
import com.urbanairship.android.layout.property.BannerPlacement
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
import com.urbanairship.android.layout.environment.EnvironmentFrameTracker
import com.urbanairship.android.layout.BannerPresentation
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.BannerTransition
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
     * The banner's configured transition, defaulting to a slide when the payload omits one.
     */
    private val bannerTransition: BannerTransition
        get() = resolvedPlacement().transition ?: BannerTransition.default()

    /**
     * The edge the banner slides from/to.
     */
    private val bannerPosition: VerticalPosition
        get() = resolvedPlacement().position.vertical

    /**
     * Listener notified when the banner frame's bounds (relative to this view) change. Used by
     * the host to size and position animations and swipe-to-dismiss gestures relative to the
     * banner content.
     */
    public var frameBoundsChangedListener: ((Rect) -> Unit)? = null

    /** Reports this view's bounds as `$environment.frame`: the space the host gives the scene. */
    private val environmentFrame = EnvironmentFrameTracker { frame, isFirst ->
        model.publishEnvironment(this, frame, immediate = isFirst)
    }

    /** What the placement's colors resolve against, so their selectors see the layout's environment conditions. */
    private val conditionsContext = EnvironmentConditionsContext.wrap(context) { model.environmentConditionValues }

    init {
        id = model.viewId
        model.seedEnvironment(this)
        configureBanner()
        EnvironmentConditionsWatcher(this, model) { onEnvironmentConditionsChanged() }
    }

    /** Returns the placement the window and the layout's environment conditions select now. */
    internal fun resolvedPlacement(): BannerPlacement =
        presentation.getResolvedPlacement(context, model.environmentConditionValues)

    /** The scene, created once and moved into a new frame when the placement changes. */
    private lateinit var contentView: View

    /** The placement the frame was built for. */
    private lateinit var shownPlacement: BannerPlacement

    /**
     * Moves the scene into a frame for the placement the new values select, or resolves the
     * frame's colors again in place; the scene's own views refresh themselves.
     */
    private fun onEnvironmentConditionsChanged() {
        val resolved = resolvedPlacement()
        if (resolved !== shownPlacement) {
            bannerFrame?.let(::removeView)
            buildFrame(resolved)
            applySizeConstraints()
            // The pinned edge, and so its inset padding, can move with the placement.
            applyWindowInsets()
            return
        }
        val frame = bannerFrame ?: return
        if (model.viewsSelectOnEnvironmentConditions) {
            val background = Background(resolved.backgroundColor, resolved.border)
            LayoutUtils.updateBackground(frame, background, background)
            applyShadow(frame, resolved.shadow)
        }
        applySizeConstraints()
    }

    /** Builds a frame sized, colored and shadowed for [placement], and moves the scene into it. */
    private fun buildFrame(placement: BannerPlacement): ConstrainedFrameLayout {
        val frame = makeFrame(placement.size)
        (contentView.parent as? ViewGroup)?.removeView(contentView)
        frame.addView(contentView)
        addView(frame)
        LayoutUtils.applyBorderAndBackground(frame, null, placement.border, placement.backgroundColor)
        applyShadow(frame, placement.shadow)
        shownPlacement = placement
        return frame
    }

    private fun configureBanner() {
        contentView = model.createView(context, environment, null)
        val frame = buildFrame(resolvedPlacement())

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
            val transition = TransitionFactory.bannerTransition(bannerTransition.enter, frame, bannerPosition)
            TransitionManager.beginDelayedTransition(this, transition)
            frame.visibility = VISIBLE
        }
    }

    private fun makeFrame(size: ConstrainedSize) =
        ConstrainedFrameLayout(conditionsContext, size).apply {
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
                val color = androidShadow.color.resolve(conditionsContext)
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
     * [configureBanner] and [refitIfWindowChanged] — the latter matters when the host activity
     * handles its own `configChanges` (no recreate), so the banner view persists and `init` never
     * re-runs; without re-applying here a portrait-fitted px box would stay stale in landscape.
     */
    private fun applySizeConstraints() {
        val frame = bannerFrame ?: return
        val placement = resolvedPlacement()
        val viewId = frame.id
        val size = placement.size
        val margin = placement.margin

        val ignoreSafeArea = placement.shouldIgnoreSafeArea()

        // Percent base for the overflow/fitting math: the full window. Note that
        // ConstraintLayout's constrainPercent* sizing is parent-relative, so this base only
        // matches the frame's actual percent sizing when the banner host fills the window.
        val window = ResourceUtils.getWindowPixels(context, this)
        val windowWidthPx = window.width(ignoreSafeArea)
        val windowHeightPx = window.height(ignoreSafeArea)

        // Overflow bound / fitted-size target: window minus the frame's outer margins.
        val horizontalMargin = (margin?.start ?: 0) + (margin?.end ?: 0)
        val verticalMargin = (margin?.top ?: 0) + (margin?.bottom ?: 0)
        val horizontalMarginPx = ResourceUtils.dpToPx(context, horizontalMargin).toInt()
        val verticalMarginPx = ResourceUtils.dpToPx(context, verticalMargin).toInt()

        val availableWidthPx = (windowWidthPx - horizontalMarginPx).coerceAtLeast(0)
        val availableHeightPx = (windowHeightPx - verticalMarginPx).coerceAtLeast(0)

        lastWindowWidth = windowWidthPx
        lastWindowHeight = windowHeightPx

        ConstraintSetBuilder.newBuilder(context, this)
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        environmentFrame.onMeasure(widthMeasureSpec, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        environmentFrame.onLayout(right - left, bottom - top)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Below API 30 the window is only readable once laid out, after init's constraints.
        post { refitIfWindowChanged() }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        environmentFrame.onConfigurationChanged()
        requestLayout()
        refitIfWindowChanged()
    }

    /** Re-applies the size constraints if the window differs from the one they were built for. */
    private fun refitIfWindowChanged() {
        val ignoreSafeArea = resolvedPlacement().shouldIgnoreSafeArea()
        val window = ResourceUtils.getWindowPixels(context, this)
        if (window.width(ignoreSafeArea) != lastWindowWidth || window.height(ignoreSafeArea) != lastWindowHeight) {
            applySizeConstraints()
        }
    }

    private companion object {
        private const val DEFAULT_ELEVATION_DP = 16
    }
}
