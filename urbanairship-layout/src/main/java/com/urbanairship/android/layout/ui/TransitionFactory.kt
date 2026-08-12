/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.transition.Fade
import android.transition.Transition
import android.transition.TransitionSet
import android.transition.TransitionValues
import android.transition.Visibility
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import com.urbanairship.android.layout.property.BannerAnimation
import com.urbanairship.android.layout.property.CornerPosition
import com.urbanairship.android.layout.property.EdgePosition
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.ModalAnimation
import com.urbanairship.android.layout.property.VerticalPosition
import kotlin.apply
import kotlin.time.Duration

internal object TransitionFactory {

    /**
     * Builds the enter transition: the content ([frame]) slides/explodes/fades while the
     * [shade] fades independently, so the scrim never moves with the modal content.
     */
    fun enterTransition(animation: ModalAnimation, frame: View, shade: View): Transition = when (animation) {
        is ModalAnimation.Fade ->
            fade(animation.animateIn).apply {
                addTarget(frame)
                addTarget(shade)
            }
        is ModalAnimation.Slide ->
            compose(slide(animation.origin, animation.animateIn), frame, shade)
        is ModalAnimation.Explode ->
            compose(corner(animation.enter, animation.animateIn), frame, shade)
    }

    /**
     * Builds the exit transition: the content ([frame]) slides/explodes/fades while the
     * [shade] fades independently, so the scrim never moves with the modal content.
     */
    fun exitTransition(animation: ModalAnimation, frame: View, shade: View): Transition = when (animation) {
        is ModalAnimation.Fade ->
            fade(animation.animateOut).apply {
                addTarget(frame)
                addTarget(shade)
            }
        is ModalAnimation.Slide ->
            compose(slide(animation.origin, animation.animateOut), frame, shade)
        is ModalAnimation.Explode ->
            compose(corner(animation.exit, animation.animateOut), frame, shade)
    }

    /**
     * Builds the banner enter transition for the content ([frame]). Banners have no scrim, so the
     * transition only targets the frame. Slides originate from the banner's [position] edge.
     */
    fun enterTransition(
        animation: BannerAnimation,
        frame: View,
        position: VerticalPosition
    ): Transition = when (animation) {
        is BannerAnimation.Fade ->
            fade(animation.animateIn).apply { addTarget(frame) }
        is BannerAnimation.Slide ->
            bannerSlide(position, animation.animateIn).apply { addTarget(frame) }
    }

    /**
     * Builds the banner exit transition for the content ([frame]). Banners have no scrim, so the
     * transition only targets the frame. Slides exit toward the banner's [position] edge.
     */
    fun exitTransition(
        animation: BannerAnimation,
        frame: View,
        position: VerticalPosition
    ): Transition = when (animation) {
        is BannerAnimation.Fade ->
            fade(animation.animateOut).apply { addTarget(frame) }
        is BannerAnimation.Slide ->
            bannerSlide(position, animation.animateOut).apply { addTarget(frame) }
    }

    /** Targets [transition] with the frame transition and fades the [shade] alongside it. */
    private fun compose(
        transition: Transition,
        frame: View,
        shade: View,
    ): Transition = TransitionSet().apply {
        ordering = TransitionSet.ORDERING_TOGETHER
        addTransition(transition.apply { addTarget(frame) })
        addTransition(Fade().apply { addTarget(shade) })
    }

    private fun fade(duration: Duration?) = Fade().applyDuration(duration)

    private fun slide(origin: EdgePosition, duration: Duration?) =
        directional(origin.horizontal, origin.vertical, duration)

    private fun bannerSlide(position: VerticalPosition, duration: Duration?) =
        directional(null, position, duration)

    private fun corner(corner: CornerPosition, duration: Duration?) =
        directional(corner.horizontal.baseType, corner.vertical.baseType, duration)

    private fun directional(
        h: HorizontalPosition?,
        v: VerticalPosition?,
        duration: Duration?
    ): Transition {
        val horizontalGravity = when (h) {
            HorizontalPosition.START -> Gravity.START
            HorizontalPosition.END   -> Gravity.END
            else -> Gravity.NO_GRAVITY
        }
        val verticalGravity = when (v) {
            VerticalPosition.TOP    -> Gravity.TOP
            VerticalPosition.BOTTOM -> Gravity.BOTTOM
            else -> Gravity.NO_GRAVITY
        }
        return Slide(horizontalGravity, verticalGravity).applyDuration(duration)
    }

    private fun <T : Transition> T.applyDuration(duration: Duration?): T = apply {
        duration?.let { this.duration = it.inWholeMilliseconds }
    }
}

internal class Slide(
    private val horizontalGravity: Int,
    private val verticalGravity: Int
) : Visibility() {

    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        val endX = view.translationX
        val endY = view.translationY
        return animate(view, offsetX(sceneRoot, endX), offsetY(sceneRoot, endY), endX, endY)
    }

    override fun onDisappear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        val startX = view.translationX
        val startY = view.translationY
        return animate(view, startX, startY, offsetX(sceneRoot, startX), offsetY(sceneRoot, startY))
    }

    private fun offsetX(sceneRoot: ViewGroup, base: Float): Float {
        val absolute = Gravity.getAbsoluteGravity(horizontalGravity, sceneRoot.layoutDirection)
        return when (absolute and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.LEFT  -> base - sceneRoot.width
            Gravity.RIGHT -> base + sceneRoot.width
            else -> base
        }
    }

    private fun offsetY(sceneRoot: ViewGroup, base: Float): Float = when (verticalGravity) {
        Gravity.TOP    -> base - sceneRoot.height
        Gravity.BOTTOM -> base + sceneRoot.height
        else -> base
    }

    private fun animate(
        view: View,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): Animator {
        view.translationX = startX
        view.translationY = startY
        return ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, startX, endX),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, startY, endY)
        )
    }
}
