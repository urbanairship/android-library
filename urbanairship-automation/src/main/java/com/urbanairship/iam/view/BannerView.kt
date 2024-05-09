/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AnimatorRes
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.customview.widget.ViewDragHelper
import com.urbanairship.android.layout.util.Timer
import com.urbanairship.automation.R
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.info.InAppMessageButtonInfo
import kotlin.math.roundToInt

/**
 * Banner view.
 */
internal open class BannerView(
    context: Context,
    private val displayContent: Banner,
    private val assets: AirshipCachedAssets?
) : FrameLayout(context), InAppButtonLayout.ButtonClickListener, View.OnClickListener,
    BannerDismissLayout.Listener {

    /** In-app message display timer. */
    internal val timer: Timer = object : Timer(displayContent.duration) {
        override fun onFinish() {
            dismiss(true)
            val listener = listener
            listener?.onTimedOut(this@BannerView)
        }
    }

    @AnimatorRes
    private var animationIn = 0
    @AnimatorRes
    private var animationOut = 0

    private var isDismissed = false
    private var isResumed = false
    private var applyLegacyWindowInsetFix = false
    private var subView: View? = null
    private var listener: Listener? = null

    /**
     * Banner view listener.
     */
    interface Listener {

        /**
         * Called when a button is clicked.
         *
         * @param view The banner view.
         * @param buttonInfo The button info.
         */
        @MainThread
        fun onButtonClicked(view: BannerView, buttonInfo: InAppMessageButtonInfo)

        /**
         * Called when the banner is clicked.
         *
         * @param view The banner view.
         */
        @MainThread
        fun onBannerClicked(view: BannerView)

        /**
         * Called when the banner times out.
         *
         * @param view The banner view.
         */
        @MainThread
        fun onTimedOut(view: BannerView)

        /**
         * Called when the banner is dismissed by the user.
         *
         * @param view The banner view.
         */
        @MainThread
        fun onUserDismissed(view: BannerView)
    }

    /**
     * Sets the banner listener.
     *
     * @param listener The banner listener.
     */
    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    /**
     * Called to inflate and attach the in-app message view.
     *
     * @param inflater The inflater.
     * @return The view.
     */
    @MainThread
    protected fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        // Main view
        val view = inflater.inflate(layout, container, false) as BannerDismissLayout
        view.placement = displayContent.placement
        view.listener = this

        // Inflate the banner content
        val bannerContent = view.findViewById<ViewStub>(R.id.banner_content)
        bannerContent.layoutResource = contentLayout
        bannerContent.inflate()

        // Banner View
        val bannerView = view.findViewById<LinearLayout>(R.id.banner)
        ViewCompat.setBackground(bannerView, createBannerBackground())
        if (displayContent.borderRadius > 0) {

            BorderRadius.applyBorderRadiusPadding(
                bannerView, displayContent.borderRadius, displayContent.placement.toBorderRadius()
            )
        }

        // Banner actions
        if (displayContent.actions.isNotEmpty()) {
            bannerView.isClickable = true
            bannerView.setOnClickListener(this)
        }

        // Heading
        val heading = view.findViewById<TextView>(R.id.heading)
        if (displayContent.heading != null) {
            InAppViewUtils.applyTextInfo(heading, displayContent.heading)
        } else {
            heading.visibility = GONE
        }

        // Body
        val body = view.findViewById<TextView>(R.id.body)
        if (displayContent.body != null) {
            InAppViewUtils.applyTextInfo(body, displayContent.body)
        } else {
            body.visibility = GONE
        }

        // Media
        val mediaView = view.findViewById<MediaView>(R.id.media)
        if (displayContent.media != null) {
            InAppViewUtils.loadMediaInfo(mediaView, displayContent.media, assets)
        } else {
            mediaView.visibility = GONE
        }

        // Button Layout
        val buttonLayout = view.findViewById<InAppButtonLayout>(R.id.buttons)
        if (displayContent.buttons.isEmpty()) {
            buttonLayout.visibility = GONE
        } else {
            buttonLayout.setButtons(displayContent.buttonLayoutType, displayContent.buttons)
            buttonLayout.setButtonClickListener(this)
        }

        // Banner dismiss pull
        val bannerPull = view.findViewById<View>(R.id.banner_pull)
        val drawable = DrawableCompat.wrap(bannerPull.background).mutate()
        DrawableCompat.setTint(drawable, displayContent.dismissButtonColor.color)
        ViewCompat.setBackground(bannerPull, drawable)

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            for (i in 0 until childCount) {
                ViewCompat.dispatchApplyWindowInsets(getChildAt(i), WindowInsetsCompat(insets))
            }
            insets
        }

        return view
    }

    /**
     * Resumes the banner's timer.
     */
    @MainThread
    @CallSuper
    internal fun onResume() {
        isResumed = true
        if (!isDismissed) {
            timer.start()
        }
    }

    /**
     * Pauses the banner's timer.
     */
    @MainThread
    @CallSuper
    internal fun onPause() {
        isResumed = false
        timer.stop()
    }

    /**
     * Used to dismiss the message.
     *
     * @param animate `true` to animate the view out, otherwise `false`.
     */
    @MainThread
    internal fun dismiss(animate: Boolean) {
        isDismissed = true
        timer.stop()
        if (animate && subView != null && animationOut != 0) {
            clearAnimation()
            val animator = AnimatorInflater.loadAnimator(context, animationOut)
            animator.setTarget(subView)
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeSelf()
                }
            })
            animator.start()
        } else {
            removeSelf()
        }
    }

    /**
     * Helper method to remove the view from the parent.
     */
    @MainThread
    private fun removeSelf() {
        val group = this.parent as? ViewGroup ?: return
        group.removeView(this)
        subView = null
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (subView != null) { return }

        if (visibility == VISIBLE && !isDismissed) {
            val view = onCreateView(LayoutInflater.from(context), this)
            if (applyLegacyWindowInsetFix) {
                applyLegacyWindowInsetFix(view)
            }
            addView(view)
            if (animationIn != 0) {
                val animator = AnimatorInflater.loadAnimator(context, animationIn)
                animator.setTarget(view)
                animator.start()
            }
            subView = view
            onResume()
        }
    }

    /**
     * Sets the animation.
     *
     * @param inAnimation The animation in.
     * @param outAnimation The animation out.
     */
    fun setAnimations(@AnimatorRes inAnimation: Int, @AnimatorRes outAnimation: Int) {
        animationIn = inAnimation
        animationOut = outAnimation
    }

    override fun onButtonClicked(view: View, buttonInfo: InAppMessageButtonInfo) {
        listener?.onButtonClicked(this, buttonInfo)
        dismiss(true)
    }

    override fun onDismissed(view: View) {
        listener?.onUserDismissed(this)
        dismiss(false)
    }

    override fun onDragStateChanged(view: View, state: Int) {
        when (state) {
            ViewDragHelper.STATE_DRAGGING -> timer.stop()
            ViewDragHelper.STATE_IDLE -> if (isResumed) { timer.start() }
        }
    }

    override fun onClick(view: View) {
        listener?.onBannerClicked(this)
        dismiss(true)
    }

    @get:LayoutRes
    private val layout: Int
        /**
         * Gets the banner layout for the banner's placement.
         *
         * @return The banner layout.
         */
        get() = when (displayContent.placement) {
            Banner.Placement.TOP -> R.layout.ua_iam_banner_top
            Banner.Placement.BOTTOM -> R.layout.ua_iam_banner_bottom
        }

    /**
     * Creates the banner's background drawable.
     *
     * @return The banner's background drawable.
     */
    private fun createBannerBackground(): Drawable {
        val pressedColor = ColorUtils.setAlphaComponent(
            displayContent.dismissButtonColor.color,
            (Color.alpha(displayContent.dismissButtonColor.color) * PRESSED_ALPHA_PERCENT).roundToInt()
        )

        return BackgroundDrawableBuilder.newBuilder(context)
            .setBackgroundColor(displayContent.backgroundColor.color)
            .setPressedColor(pressedColor)
            .setBorderRadius(displayContent.borderRadius, displayContent.placement.toBorderRadius())
            .build()
    }

    @get:LayoutRes
    private val contentLayout: Int
        /**
         * Gets the banner content layout for the banner's template.
         *
         * @return The banner template layout.
         */
        get() = when (displayContent.template) {
            Banner.Template.MEDIA_RIGHT -> R.layout.ua_iam_banner_content_right_media
            Banner.Template.MEDIA_LEFT -> R.layout.ua_iam_banner_content_left_media
        }

    private fun applyLegacyWindowInsetFix(view: View) {
        // Avoid double insets if no other view is consuming the insets
        val subview = subView ?: return
        subview.fitsSystemWindows = false

        val isNavigationTranslucent: Boolean
        val isStatusTranslucent: Boolean
        val a = context.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.windowTranslucentNavigation,
                android.R.attr.windowTranslucentStatus
            )
        )
        isNavigationTranslucent = a.getBoolean(0, false)
        isStatusTranslucent = a.getBoolean(1, false)
        a.recycle()
        var top = 0
        if (isStatusTranslucent) {
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                top = resources.getDimensionPixelSize(resourceId)
            }
        }
        var bottom = 0
        if (isNavigationTranslucent) {
            val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) {
                bottom = resources.getDimensionPixelSize(resourceId)
            }
        }
        ViewCompat.setPaddingRelative(subview, 0, top, 0, bottom)
    }

    companion object {
        private const val PRESSED_ALPHA_PERCENT = .2f
    }
}

private fun Banner.Placement.toBorderRadius(): @BorderRadius.BorderRadiusFlag Int {
    return when(this) {
        Banner.Placement.TOP -> BorderRadius.BOTTOM
        Banner.Placement.BOTTOM -> BorderRadius.TOP
    }
}
