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
import com.urbanairship.android.layout.property.BannerAnimationEffect
import com.urbanairship.android.layout.property.CornerPosition
import com.urbanairship.android.layout.property.EdgePosition
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.ModalAnimationEffect
import com.urbanairship.android.layout.property.VerticalPosition
import kotlin.apply
import kotlin.time.Duration

internal object TransitionFactory {

    /**
     * Builds the transition for one direction of a modal's animation: the content ([frame])
     * slides/explodes/fades while the [shade] fades independently, so the scrim never moves with
     * the modal content. Used for both the enter and exit transition, each fed its own effect.
     */
    fun modalTransition(effect: ModalAnimationEffect, frame: View, shade: View): Transition = when (effect) {
        is ModalAnimationEffect.Fade ->
            fade(effect.duration).apply {
                addTarget(frame)
                addTarget(shade)
            }
        is ModalAnimationEffect.Slide ->
            compose(slide(effect.edge, effect.duration), frame, shade)
        is ModalAnimationEffect.Explode ->
            compose(corner(effect.corner, effect.duration), frame, shade)
    }

    /**
     * Builds the transition for one direction of a banner's animation, for the content
     * ([frame]). Banners have no scrim, so the transition only targets the frame. A slide always
     * plays toward/from the banner's own [position] edge. Used for both the enter and exit
     * transition, each fed its own effect.
     */
    fun bannerTransition(
        effect: BannerAnimationEffect,
        frame: View,
        position: VerticalPosition
    ): Transition = when (effect) {
        is BannerAnimationEffect.Fade ->
            fade(effect.duration).apply { addTarget(frame) }
        is BannerAnimationEffect.Slide ->
            bannerSlide(position, effect.duration).apply { addTarget(frame) }
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

    private fun slide(edge: EdgePosition, duration: Duration?) =
        directional(edge.horizontal, edge.vertical, duration)

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
