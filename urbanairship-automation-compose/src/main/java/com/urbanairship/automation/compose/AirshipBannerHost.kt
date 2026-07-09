/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import android.os.SystemClock
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.urbanairship.UALog
import com.urbanairship.android.layout.property.BannerAnimation
import com.urbanairship.android.layout.property.BannerPlacement
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.ui.BannerLayout
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A host container that displays Thomas banners.
 *
 * Add a single banner host to the app's layout, after applying any window insets, and above any
 * content that banners should overlay. Banners are positioned within the host's bounds, per the
 * banner's placement, so the host should generally be sized to fill the area that banners may
 * be displayed in.
 *
 * Pending banners are queued until a host is mounted.
 *
 * @param modifier the modifier to apply to this layout.
 */
@Composable
public fun AirshipBannerHost(
    modifier: Modifier = Modifier
) {
    AirshipBannerHost(
        state = rememberAirshipBannerHostState(),
        modifier = modifier
    )
}

/**
 * A host container that displays Thomas banners, using the provided [AirshipBannerHostState].
 *
 * This composable may be useful when access to the banner host state is needed outside of the
 * `AirshipBannerHost` composable, and for advanced custom logic that depends on the
 * availability of banner content.
 *
 * @param state the [AirshipBannerHostState] to be used by this banner host.
 * @param modifier the modifier to apply to this layout.
 */
@Composable
public fun AirshipBannerHost(
    state: AirshipBannerHostState,
    modifier: Modifier = Modifier
) {
    var hostSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { hostSize = it }
    ) {
        state.currentLayout?.let { layout ->
            key(layout.viewInstanceId) {
                BannerContent(
                    layout = layout,
                    hostSizeProvider = { hostSize },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** The reason a banner is being animated out of the host. */
private sealed class BannerDismissal {
    data object TimedOut : BannerDismissal()
    data class Swiped(val velocity: Float) : BannerDismissal()
}

/** Displays a single banner layout, handling animations, swipe to dismiss, and auto-dismiss. */
@Composable
private fun BannerContent(
    layout: BannerLayout,
    hostSizeProvider: () -> IntSize,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()

    val placement = remember(layout, configuration) { layout.getPlacement() }
    val durationMs = remember(layout) { layout.getPresentation()?.durationMs }
    val animation = placement?.animation ?: BannerAnimation.DEFAULT

    // The size of the banner frame within the host, reported by the banner view once laid out.
    var frameSize by remember { mutableStateOf(IntSize.Zero) }

    // Remember the view, so we only create it once per banner instance.
    val view = remember(layout.viewInstanceId) {
        layout.makeView(
            frameSizeChangedListener = { width, height ->
                frameSize = IntSize(width, height)
            }
        )?.apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
    }
    if (view == null) {
        UALog.e { "Failed to create banner view for instance: \"${layout.viewInstanceId}\"" }
        // Dismiss the failed banner, so that it doesn't block other pending banners.
        LaunchedEffect(layout.viewInstanceId) {
            layout.dismissFromViewFailure()
        }
        return
    }

    val isFrameReady = frameSize != IntSize.Zero

    // Enter/exit transition progress: 1 = fully hidden (off-screen or transparent), 0 = settled.
    val transition = remember { Animatable(1f) }
    // Swipe to dismiss drag offset, in pixels, along the swipe axis.
    val dragOffset = remember { Animatable(0f) }

    var isDragging by remember { mutableStateOf(false) }
    var pendingDismissal by remember { mutableStateOf<BannerDismissal?>(null) }
    val isDismissing = pendingDismissal != null

    val swipeAxis = placement.swipeAxis()
    val dismissDirection = placement.dismissDirection(isRtl)

    fun frameDistance(): Float = when (swipeAxis) {
        Orientation.Vertical ->
            (frameSize.height.takeIf { it > 0 } ?: hostSizeProvider().height).toFloat()
        Orientation.Horizontal ->
            (frameSize.width.takeIf { it > 0 } ?: hostSizeProvider().width).toFloat()
    }

    // Animate the banner in once the frame has been measured.
    LaunchedEffect(isFrameReady) {
        if (isFrameReady) {
            transition.animateTo(0f, tween(animation.animateInMs.toInt(), easing = LinearEasing))
        }
    }

    // Animate the banner out and dismiss it, when timed out or swiped away.
    LaunchedEffect(pendingDismissal) {
        when (val dismissal = pendingDismissal) {
            null -> {}
            is BannerDismissal.TimedOut -> {
                transition.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(animation.animateOutMs.toInt(), easing = LinearEasing)
                )
                layout.dismissFromTimeout()
            }
            is BannerDismissal.Swiped -> {
                val hostSize = hostSizeProvider()
                val range = when (swipeAxis) {
                    Orientation.Vertical -> hostSize.height
                    Orientation.Horizontal -> hostSize.width
                }
                val target = (range + frameDistance()) * dismissDirection
                dragOffset.animateTo(target, initialVelocity = dismissal.velocity)
                layout.dismissFromUser()
            }
        }
    }

    // Auto-dismiss timer, paused while the app is backgrounded or the banner is being dragged.
    if (durationMs != null) {
        val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val isTimerRunning = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
                !isDragging && !isDismissing

        var remainingMs by remember { mutableLongStateOf(durationMs) }

        LaunchedEffect(isTimerRunning) {
            if (!isTimerRunning) return@LaunchedEffect

            if (remainingMs > 0) {
                val startTime = SystemClock.elapsedRealtime()
                try {
                    delay(remainingMs)
                } finally {
                    remainingMs = (remainingMs - (SystemClock.elapsedRealtime() - startTime))
                        .coerceAtLeast(0)
                }
            }

            pendingDismissal = BannerDismissal.TimedOut
        }
    }

    val minFlingVelocity = remember(context) {
        ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    }
    val overDragPx = with(LocalDensity.current) { OVER_DRAG.toPx() }

    AndroidView(
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }.also {
                UALog.v { "Create banner layout for instance: \"${layout.viewInstanceId}\"" }
            }
        },
        update = { frame ->
            if (view.parent != frame) {
                // If the frame has children, remove them before adding the new view.
                if (frame.childCount > 0) {
                    frame.removeAllViews()
                }
                frame.addView(view)
            }
            UALog.v { "Update banner layout for instance: \"${layout.viewInstanceId}\"" }
        },
        onReset = { frame ->
            frame.removeAllViews()
            UALog.v { "Reset banner layout for instance: \"${layout.viewInstanceId}\"" }
        },
        modifier = modifier
            .graphicsLayer {
                when (animation) {
                    is BannerAnimation.Fade -> {
                        alpha = 1f - transition.value
                    }
                    is BannerAnimation.Slide -> {
                        // Slide in from (and out to) the banner's placement edge.
                        val slideOffset = transition.value * frameDistance() * dismissDirection
                        when (swipeAxis) {
                            Orientation.Vertical -> translationY = slideOffset
                            Orientation.Horizontal -> translationX = slideOffset
                        }
                        // Hide the banner until the frame has been measured, to avoid
                        // a flash of un-animated content on the first frame.
                        alpha = if (isFrameReady) 1f else 0f
                    }
                }

                when (swipeAxis) {
                    Orientation.Vertical -> translationY += dragOffset.value
                    Orientation.Horizontal -> translationX += dragOffset.value
                }
            }
            .draggable(
                state = rememberDraggableState { delta ->
                    val newOffset = if (dismissDirection > 0) {
                        (dragOffset.value + delta).coerceAtLeast(-overDragPx)
                    } else {
                        (dragOffset.value + delta).coerceAtMost(overDragPx)
                    }
                    scope.launch { dragOffset.snapTo(newOffset) }
                },
                orientation = swipeAxis,
                enabled = (placement?.swipeToDismiss ?: true) && !isDismissing,
                onDragStarted = { isDragging = true },
                onDragStopped = { velocity ->
                    isDragging = false

                    val bannerExtent = frameDistance()
                    val offset = dragOffset.value
                    val dragPercent = if (bannerExtent > 0) abs(offset) / bannerExtent else 0f
                    val movedTowardDismiss = offset * dismissDirection > 0
                    // Only treat flings toward the dismiss edge as dismiss flings.
                    val isDismissFling = velocity * dismissDirection >= minFlingVelocity

                    val shouldDismiss = movedTowardDismiss && (
                        dragPercent >= IDLE_MIN_DRAG_PERCENT ||
                        (isDismissFling && dragPercent > FLING_MIN_DRAG_PERCENT)
                    )

                    if (shouldDismiss) {
                        pendingDismissal = BannerDismissal.Swiped(velocity)
                    } else {
                        dragOffset.animateTo(0f, initialVelocity = velocity)
                    }
                }
            )
    )
}

/** Returns the axis that this placement can be swiped along to dismiss. */
private fun BannerPlacement?.swipeAxis(): Orientation =
    when (this?.position?.vertical) {
        null, VerticalPosition.TOP, VerticalPosition.BOTTOM -> Orientation.Vertical
        VerticalPosition.CENTER -> Orientation.Horizontal
    }

/**
 * Returns the direction, along the swipe axis, that this placement animates in from and can be
 * swiped toward to dismiss: `-1` for up/left and `1` for down/right.
 */
private fun BannerPlacement?.dismissDirection(isRtl: Boolean): Float =
    when (this?.position?.vertical) {
        null, VerticalPosition.BOTTOM -> 1f
        VerticalPosition.TOP -> -1f
        VerticalPosition.CENTER -> when (this.position.horizontal) {
            HorizontalPosition.START -> if (isRtl) 1f else -1f
            else -> if (isRtl) -1f else 1f
        }
    }

/**
 * The percent of the banner frame's height (or width, for horizontal swipes) that a banner must
 * be dragged before it is dismissed when released with a velocity below the minimum fling
 * velocity.
 */
private const val IDLE_MIN_DRAG_PERCENT = .4f

/**
 * The percent of the banner frame's height (or width, for horizontal swipes) that a banner must
 * be dragged before it is dismissed when released with a fling velocity toward the dismiss edge.
 */
private const val FLING_MIN_DRAG_PERCENT = .1f

/** The amount a banner may be dragged past its resting position, away from the dismiss edge. */
private val OVER_DRAG = 24.dp
