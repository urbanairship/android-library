/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.urbanairship.UALog
import com.urbanairship.android.layout.property.BannerTransitionEffect
import com.urbanairship.android.layout.property.BannerPlacement
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.ui.BannerLayout
import com.urbanairship.util.Clock
import kotlin.math.abs
import kotlin.time.Duration
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A host container that displays Thomas banners.
 *
 * Add a single banner host to the app's layout, after applying any window insets, and above any
 * content that banners should overlay. Banners are positioned within the host's bounds, per the
 * banner's placement, so the host should generally be sized to fill the area that banners may
 * be displayed in.
 *
 * The host must be attached to an Activity that implements
 * [androidx.lifecycle.LifecycleOwner] (as all `ComponentActivity` subclasses do), or banner
 * views will fail to be created.
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
    val duration = remember(layout) { layout.getPresentation().duration }
    val bannerTransition = placement.transition

    // The bounds of the banner frame within the host, reported by the banner view once laid out.
    var frameBounds by remember { mutableStateOf<IntRect?>(null) }
    val frameSize = frameBounds?.let { IntSize(it.width, it.height) } ?: IntSize.Zero

    // Remember the view, so we only create it once per banner instance.
    val view = remember(layout.viewInstanceId) {
        layout.makeView(
            frameBoundsChangedListener = { bounds ->
                frameBounds = IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
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
            transition.animateTo(0f, tween((bannerTransition?.enter?.duration?.inWholeMilliseconds ?: 0L).toInt(), easing = LinearEasing))
        }
    }

    // Animate the banner out and dismiss it, when timed out or swiped away. The terminal
    // dismiss runs in a NonCancellable finally block, so that disposal mid-animation can't
    // cancel it and leave the banner un-dismissed.
    LaunchedEffect(pendingDismissal) {
        when (val dismissal = pendingDismissal) {
            null -> {}
            is BannerDismissal.TimedOut -> {
                try {
                    transition.animateTo(
                        targetValue = 1f,
                        animationSpec = tween((bannerTransition?.exit?.duration?.inWholeMilliseconds ?: 0L).toInt(), easing = LinearEasing)
                    )
                } finally {
                    withContext(NonCancellable) {
                        layout.dismissFromTimeout()
                    }
                }
            }
            is BannerDismissal.Swiped -> {
                try {
                    val hostSize = hostSizeProvider()
                    val range = when (swipeAxis) {
                        Orientation.Vertical -> hostSize.height
                        Orientation.Horizontal -> hostSize.width
                    }
                    val target = (range + frameDistance()) * dismissDirection
                    dragOffset.animateTo(target, initialVelocity = dismissal.velocity)
                } finally {
                    withContext(NonCancellable) {
                        layout.dismissFromUser()
                    }
                }
            }
        }
    }

    // Auto-dismiss timer, paused while the host lifecycle is below RESUMED or the banner is
    // being dragged or dismissed.
    if (duration != null) {
        val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val isTimerRunning = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
                !isDragging && !isDismissing

        val timer = remember { BannerAutoDismissTimer(duration) }

        LaunchedEffect(isTimerRunning) {
            if (!isTimerRunning) return@LaunchedEffect

            timer.start { pendingDismissal = BannerDismissal.TimedOut }
        }
    }

    val minFlingVelocity = remember(context) {
        ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    }
    val overDragPx = with(LocalDensity.current) { OVER_DRAG.toPx() }

    // Absolute alignment and offset are used below because the banner view reports its frame
    // bounds in absolute (physical) coordinates, which should not be mirrored in RTL layouts.
    Box(modifier = modifier, contentAlignment = AbsoluteAlignment.TopLeft) {
        Box(
            modifier = Modifier
                // Position and size this wrapper to the measured banner frame (or the full host,
                // before the frame is measured), so that gesture capture and the drag/animation
                // translation are scoped to the banner content. Drags (like taps) outside the
                // banner frame pass through to the app content beneath the host.
                .absoluteOffset { frameBounds?.let { IntOffset(it.left, it.top) } ?: IntOffset.Zero }
                .graphicsLayer {
                    // A swipe dismissal is driven entirely by dragOffset below, so `transition`
                    // never leaves 0 for it -- only entering and a timed-out exit reach here,
                    // which is exactly the phase `pendingDismissal` already distinguishes.
                    val activeEffect = if (pendingDismissal != null) bannerTransition?.exit else bannerTransition?.enter
                    when (activeEffect) {
                        is BannerTransitionEffect.Fade -> {
                            alpha = 1f - transition.value
                        }
                        is BannerTransitionEffect.Slide -> {
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
                        null -> {}
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
                    enabled = placement.swipeToDismiss && !isDismissing,
                    onDragStarted = { isDragging = true },
                    onDragStopped = { velocity ->
                        isDragging = false

                        val shouldDismiss = shouldDismissBanner(
                            dragOffset = dragOffset.value,
                            velocity = velocity,
                            frameExtent = frameDistance(),
                            dismissDirection = dismissDirection,
                            minFlingVelocity = minFlingVelocity
                        )

                        if (shouldDismiss) {
                            pendingDismissal = BannerDismissal.Swiped(velocity)
                        } else {
                            dragOffset.animateTo(0f, initialVelocity = velocity)
                        }
                    }
                )
                .layout { measurable, constraints ->
                    // Measure the banner content at the full host size (the banner view positions
                    // its frame internally), but report the frame's bounds as this node's size and
                    // offset the content to compensate, so the wrapper overlays the banner frame.
                    val placeable = measurable.measure(constraints)
                    val bounds = frameBounds
                    if (bounds == null) {
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    } else {
                        layout(bounds.width, bounds.height) {
                            placeable.place(-bounds.left, -bounds.top)
                        }
                    }
                }
        ) {
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
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Returns the axis that this placement can be swiped along to dismiss. */
internal fun BannerPlacement.swipeAxis(): Orientation =
    when (position.vertical) {
        VerticalPosition.TOP, VerticalPosition.BOTTOM -> Orientation.Vertical
        VerticalPosition.CENTER -> Orientation.Horizontal
    }

/**
 * Returns the direction, along the swipe axis, that this placement animates in from and can be
 * swiped toward to dismiss: `-1` for up/left and `1` for down/right.
 */
internal fun BannerPlacement.dismissDirection(isRtl: Boolean): Float =
    when (position.vertical) {
        VerticalPosition.BOTTOM -> 1f
        VerticalPosition.TOP -> -1f
        VerticalPosition.CENTER -> when (position.horizontal) {
            HorizontalPosition.START -> if (isRtl) 1f else -1f
            else -> if (isRtl) -1f else 1f
        }
    }

/**
 * Returns whether a banner should be dismissed when a drag gesture is released.
 *
 * @param dragOffset the drag offset, in pixels, along the swipe axis.
 * @param velocity the release velocity, in pixels per second, along the swipe axis.
 * @param frameExtent the extent of the banner frame along the swipe axis, in pixels. When the
 * frame is unmeasured (zero), the drag percent is treated as zero and the release never
 * dismisses.
 * @param dismissDirection the dismiss direction along the swipe axis: `-1` or `1`.
 * @param minFlingVelocity the minimum velocity, in pixels per second, for a release to be
 * treated as a fling.
 */
internal fun shouldDismissBanner(
    dragOffset: Float,
    velocity: Float,
    frameExtent: Float,
    dismissDirection: Float,
    minFlingVelocity: Float
): Boolean {
    val dragPercent = if (frameExtent > 0) abs(dragOffset) / frameExtent else 0f
    val movedTowardDismiss = dragOffset * dismissDirection > 0
    // Only treat flings toward the dismiss edge as dismiss flings.
    val isDismissFling = velocity * dismissDirection >= minFlingVelocity

    return movedTowardDismiss && (
        dragPercent >= IDLE_MIN_DRAG_PERCENT ||
        (isDismissFling && dragPercent > FLING_MIN_DRAG_PERCENT)
    )
}

/**
 * Auto-dismiss countdown state for a banner, tracking the remaining duration across pause
 * (cancellation) and resume cycles.
 *
 * [start] delays for the remaining duration and then invokes its callback. If the running
 * coroutine is cancelled (pausing the timer), the remaining duration is reduced by the elapsed
 * time, so a subsequent [start] resumes the countdown from where it left off. If the remaining
 * duration has already reached zero, the callback is invoked immediately.
 */
internal class BannerAutoDismissTimer(
    duration: Duration,
    private val elapsedTime: () -> Duration = { Clock.DEFAULT_CLOCK.elapsedRealtime() }
) {
    internal var remaining: Duration = duration
        private set

    internal suspend fun start(onTimedOut: () -> Unit) {
        if (remaining > Duration.ZERO) {
            val startTime = elapsedTime()
            try {
                delay(remaining)
            } finally {
                remaining = (remaining - (elapsedTime() - startTime))
                    .coerceAtLeast(Duration.ZERO)
            }
        }

        onTimedOut()
    }
}

/**
 * The percent of the banner frame's height (or width, for horizontal swipes) that a banner must
 * be dragged toward the dismiss edge before it is dismissed when released with a velocity below
 * the minimum fling velocity. Before the banner frame has been measured, the host's size is
 * used as the fallback base.
 */
private const val IDLE_MIN_DRAG_PERCENT = .4f

/**
 * The percent of the banner frame's height (or width, for horizontal swipes) that a banner must
 * be dragged toward the dismiss edge before it is dismissed when released with a fling velocity
 * toward the dismiss edge. Before the banner frame has been measured, the host's size is used
 * as the fallback base.
 */
private const val FLING_MIN_DRAG_PERCENT = .1f

/** The amount a banner may be dragged past its resting position, away from the dismiss edge. */
private val OVER_DRAG = 24.dp
