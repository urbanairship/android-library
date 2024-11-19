/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import android.view.Gravity
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.updateLayoutParams
import com.urbanairship.UALog
import com.urbanairship.android.layout.ui.EmbeddedLayout
import com.urbanairship.embedded.AirshipEmbeddedInfo

/** Default values used for [AirshipEmbeddedView] implementations. */
public object AirshipEmbeddedViewDefaults {
    /**
     * A cross-fade animation that can optionally be used for embedded content transitions.
     *
     * When placing an `AirshipEmbeddedView` inside a lazy composable, the embedded view should
     * not use the built-in animations. Instead, handle animation externally using the animations
     * made available in the lazy composable scope.
     */
    public val CrossfadeContentTransform: ContentTransform =
        fadeIn(animationSpec = tween(220, 90))
            .togetherWith(fadeOut(animationSpec = tween(90)))

    /** A content transform that doesn't animate. */
    public val NoContentTransform: ContentTransform =
        EnterTransition.None togetherWith ExitTransition.None

    /** Default content alignment. */
    public val ContentAlignment: Alignment = Alignment.Center
}

/**
 * A container that displays embedded content for the given `embeddedId`.
 *
 * When included inside of a lazy composable or scrolling list, prefer using the variant of this
 * composable that accepts an [AirshipEmbeddedViewState], which may be hoisted to avoid
 * unnecessary recompositions.
 *
 * @param embeddedId the embedded ID.
 * @param modifier the modifier to apply to this layout.
 * @param comparator optional `Comparator` used to sort available embedded contents.
 * @param contentAlignment optional alignment of the embedded content.
 * @param parentWidthProvider optional provider for the parent width.
 * @param parentHeightProvider optional provider for the parent height.
 * @param animatedContentTransform optional [ContentTransform] used to animate content changes.
 * @param placeholder optional placeholder composable to display when no content is available.
 */
@Composable
public fun AirshipEmbeddedView(
    embeddedId: String,
    modifier: Modifier = Modifier,
    comparator: Comparator<AirshipEmbeddedInfo>? = null,
    contentAlignment: Alignment = AirshipEmbeddedViewDefaults.ContentAlignment,
    parentWidthProvider: (() -> Int)? = null,
    parentHeightProvider: (() -> Int)? = null,
    animatedContentTransform: ContentTransform = AirshipEmbeddedViewDefaults.NoContentTransform,
    placeholder: (@Composable () -> Unit)? = null
) {
    EmbeddedViewContent(
        modifier = modifier,
        state = rememberAirshipEmbeddedViewState(embeddedId, comparator),
        contentAlignment = contentAlignment,
        parentWidthProvider = parentWidthProvider,
        parentHeightProvider = parentHeightProvider,
        animatedContentTransform = animatedContentTransform,
        placeholder = placeholder
    )
}

/**
 * A container that displays embedded content for the `embeddedId` defined on the given
 * [AirshipEmbeddedViewState] instance.
 *
 * This composable may be useful when access to the embedded view state is needed outside of the
 * `AirshipEmbeddedView` composable, embedded view state should be hoisted, and for advanced custom
 * logic that depends on the availability of embedded view content.
 *
 * When included inside of a lazy composable or scrolling list, prefer using the default value
 * for `animatedContentTransform` and use the animation support provided by the lazy composable.
 *
 * @param state the [AirshipEmbeddedViewState] to be used by this embedded view.
 * @param modifier the modifier to apply to this layout.
 * @param contentAlignment optional alignment of the embedded content.
 * @param animatedContentTransform optional [ContentTransform] used to animate content changes.
 * @param parentWidthProvider optional provider for the parent width.
 * @param parentHeightProvider optional provider for the parent height.
 * @param placeholder optional placeholder composable to display when no content is available.
 */
@Composable
public fun AirshipEmbeddedView(
    state: AirshipEmbeddedViewState,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = AirshipEmbeddedViewDefaults.ContentAlignment,
    animatedContentTransform: ContentTransform = AirshipEmbeddedViewDefaults.NoContentTransform,
    parentWidthProvider: (() -> Int)? = null,
    parentHeightProvider: (() -> Int)? = null,
    placeholder: (@Composable () -> Unit)? = null
) {
    EmbeddedViewContent(
        modifier = modifier,
        state = state,
        contentAlignment = contentAlignment,
        parentWidthProvider = parentWidthProvider,
        parentHeightProvider = parentHeightProvider,
        animatedContentTransform = animatedContentTransform,
        placeholder = placeholder
    )
}

@Composable
private fun EmbeddedViewContent(
    modifier: Modifier = Modifier,
    state: AirshipEmbeddedViewState,
    contentAlignment: Alignment,
    parentWidthProvider: (() -> Int)?,
    parentHeightProvider: (() -> Int)?,
    animatedContentTransform: ContentTransform,
    placeholder: (@Composable () -> Unit)?
) {
    Box(
        contentAlignment = contentAlignment,
        modifier = modifier
    ) {
        if (LocalInspectionMode.current) {
            if (placeholder != null) {
                // Use the provided placeholder for the preview
                placeholder()
            } else {
                // Default preview placeholder
                BasicText(
                    text = "Airship Embedded View (\"${state.embeddedId}\")",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            AnimatedContent(
                targetState = state.currentLayout,
                transitionSpec = { animatedContentTransform },
                contentKey = { it?.viewInstanceId },
                contentAlignment = contentAlignment,
                label = "Airship Embedded View (\"${state.embeddedId}\")",
                modifier = modifier,
            ){ layout ->
                EmbeddedViewWrapper(
                    embeddedId = state.embeddedId,
                    embeddedLayout = layout,
                    embeddedSize = state.placementSize
                        ?.toEmbeddedSize(parentWidthProvider, parentHeightProvider),
                    placeholder = placeholder,
                    modifier = Modifier.wrapContentSize(),
                )
            }
        }
    }
}

/** Shows the [embeddedLayout] if not null, otherwise shows the [placeholder]. */
@Composable
internal fun EmbeddedViewWrapper(
    embeddedId: String,
    embeddedLayout: EmbeddedLayout?,
    modifier: Modifier = Modifier,
    embeddedSize: EmbeddedSize?,
    placeholder: (@Composable () -> Unit)?
) {
    val layout = embeddedLayout ?: run {
        // Show the placeholder if we have one
        placeholder?.invoke()

        // Bail out if we don't have a layout
        return
    }

    // Only nullable because we're safe-casting the placement type farther down.
    // Placement should never be null here, in practice.
    val (width, height) = embeddedSize ?: run {
        UALog.w("Embedded size is null for embedded ID \"$embeddedId\"!")
        return
    }

    // Remember the view, updating it if the layout instance ID changes.
    val view = remember(embeddedId, layout.viewInstanceId) {
        embeddedLayout.makeView(width.fill, height.fill)!!.apply {
            layoutParams = LayoutParams(width.spec, height.spec).apply {
                gravity = Gravity.CENTER
            }
        }
    }

    AndroidView(
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                layoutParams = LayoutParams(width.spec, height.spec)
            }.also {
                UALog.v { "Create embedded layout for id: \"$embeddedId\", instance: \"${embeddedLayout.viewInstanceId}\"" }
            }
        },
        update = { frame ->
            view.apply {
                // Update the layout params to pass along size changes to the
                // child embedded view.
                updateLayoutParams {
                    LayoutParams(width.spec, height.spec).apply {
                        gravity = Gravity.CENTER
                    }
                }

                // If the frame has children, remove them before adding the new view.
                if (frame.childCount > 0) {
                    frame.removeAllViews()
                }

                frame.addView(this)

                UALog.v { "Update embedded layout for id: \"$embeddedId\", instance: \"${embeddedLayout.viewInstanceId}\"}" }
            }
         },
        onReset = { frame ->
            frame.removeAllViews()
            UALog.v { "Reset embedded layout for id: \"$embeddedId\", instance: \"${embeddedLayout.viewInstanceId}\"" }
        },
        modifier = modifier
    )
}
