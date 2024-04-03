/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import android.view.Gravity
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.property.Size.DimensionType.AUTO
import com.urbanairship.android.layout.property.Size.DimensionType.PERCENT
import com.urbanairship.android.layout.ui.EmbeddedLayout
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.embedded.EmbeddedViewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

public object AirshipEmbeddedViewDefaults {
    public val DefaultTransitionSpec: AnimatedContentTransitionScope<EmbeddedLayout?>.() -> ContentTransform = {
        (fadeIn(animationSpec = tween(220, 90)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(220, 90)))
            .togetherWith(fadeOut(animationSpec = tween(90)))
    }

    public val NoneTransitionSpec: AnimatedContentTransitionScope<EmbeddedLayout?>.() -> ContentTransform = {
        EnterTransition.None.togetherWith(ExitTransition.None)
    }
}

@Composable
public fun AirshipEmbeddedView(
    state: AirshipEmbeddedViewState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    // TODO: can we do this without exposing EmbeddedLayout? maybe not...
    transitionSpec: AnimatedContentTransitionScope<EmbeddedLayout?>.() -> ContentTransform = AirshipEmbeddedViewDefaults.DefaultTransitionSpec,
    contentAlignment: Alignment = Alignment.Center,
    parentWidthProvider: (() -> Int)? = null,
    parentHeightProvider: (() -> Int)? = null,
    placeholder: (@Composable () -> Unit)? = null
) {
    AirshipEmbeddedView(
        embeddedId = state.embeddedId,
        modifier = modifier,
        state = state,
        onClick = onClick,
        transitionSpec = transitionSpec,
        contentAlignment = contentAlignment,
        parentWidthProvider = parentWidthProvider,
        parentHeightProvider = parentHeightProvider,
        placeholder = placeholder
    )
}

@Composable
public fun AirshipEmbeddedView(
    embeddedId: String,
    modifier: Modifier = Modifier,
    state: AirshipEmbeddedViewState = rememberAirshipEmbeddedViewState(embeddedId),
    onClick: (() -> Unit)? = null,
    // TODO: can we do this without exposing EmbeddedLayout? maybe not...
    transitionSpec: AnimatedContentTransitionScope<EmbeddedLayout?>.() -> ContentTransform = AirshipEmbeddedViewDefaults.DefaultTransitionSpec,
    contentAlignment: Alignment = Alignment.Center,
    parentWidthProvider: (() -> Int)? = null,
    parentHeightProvider: (() -> Int)? = null,
    placeholder: (@Composable () -> Unit)? = null
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
                    text = "Airship Embedded View (\"$embeddedId\")",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            EmbeddedViewContent(
                embeddedId = embeddedId,
                onClick = onClick,
                state = state,
                transitionSpec = transitionSpec,
                contentAlignment = contentAlignment,
                parentWidthProvider = parentWidthProvider,
                parentHeightProvider = parentHeightProvider,
                placeholder = placeholder,
            )
        }
    }
}

@Composable
private fun BoxScope.EmbeddedViewContent(
    embeddedId: String,
    onClick: (() -> Unit)?,
    state: AirshipEmbeddedViewState,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<EmbeddedLayout?>.() -> ContentTransform,
    contentAlignment: Alignment,
    parentWidthProvider: (() -> Int)? = null,
    parentHeightProvider: (() -> Int)? = null,
    placeholder: (@Composable () -> Unit)?
) {
    AnimatedContent(
        label = "Airship Embedded View (\"$embeddedId\")",
        targetState = state.currentLayout,
        transitionSpec = transitionSpec,
        contentKey = { it?.viewInstanceId },
        contentAlignment = contentAlignment,
        modifier = modifier
    ) { layout ->
        EmbeddedViewWrapper(
            layoutId = embeddedId,
            layout = layout,
            onClick = onClick,
            modifier = Modifier.matchParentSize(),
            parentWidthProvider = parentWidthProvider,
            parentHeightProvider = parentHeightProvider,
            placeholder = placeholder,
        )
    }
}

@Composable
private fun EmbeddedViewWrapper(
    layoutId: String,
    layout: EmbeddedLayout?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    parentWidthProvider: (() -> Int)?,
    parentHeightProvider: (() -> Int)?,
    placeholder: (@Composable () -> Unit)?
) {
    if (layout != null) {
        // This is only nullable because we're safe-casting the placement type.
        // It should never be null, in practice.
        val size = layout.getPlacement()?.size ?: return

        val (widthSpec, fillWidth) = when (size.width.type) {
            AUTO -> WRAP_CONTENT to false
            PERCENT -> parentWidthProvider?.invoke()?.let { parentWidth ->
                (size.width.float * parentWidth).roundToInt() to true
            } ?: (MATCH_PARENT to false)

            else -> MATCH_PARENT to false
        }

        val (heightSpec, fillHeight) = when (size.height.type) {
            AUTO -> WRAP_CONTENT to false
            PERCENT -> parentHeightProvider?.invoke()?.let { parentHeight ->
                (size.height.float * parentHeight).roundToInt() to true
            } ?: (MATCH_PARENT to false)

            else -> MATCH_PARENT to false
        }

        AndroidView(
            factory = { viewContext ->
                FrameLayout(viewContext).apply {
                    layoutParams = LayoutParams(widthSpec, heightSpec)
                    onClick?.let { setOnClickListener { onClick() } }
                    UALog.v { "Displayed embedded layout for id: \"$layoutId\"" }
                }
            },
            update = { frame ->
                layout.makeView(fillWidth, fillHeight)?.apply {
                    layoutParams = LayoutParams(widthSpec, heightSpec).apply {
                        gravity = Gravity.CENTER
                    }
                }?.let { frame.addView(it) }
                UALog.v { "Updated embedded layout for id: \"$layoutId\"" }
            },
            onReset = { view ->
                view.removeAllViews()
                UALog.v { "Reset embedded layout for id: \"$layoutId\"" }
            },
            onRelease = {
                UALog.v { "Released embedded layout for id: \"$layoutId\"" }
            },
            modifier = modifier
        )
    } else if (placeholder != null) {
        placeholder()
    }
}

/**
 * Creates a [AirshipEmbeddedViewState] that can be used to manage the state of an embedded layout.
 */
@Composable
public fun rememberAirshipEmbeddedViewState(
    embeddedId: String,
    onAvailable: () -> Boolean = { true },
    onEmpty: () -> Unit = {},
): AirshipEmbeddedViewState {
    return rememberAirshipEmbeddedViewState(embeddedId, onAvailable, onEmpty, EmbeddedViewManager)
}

@Composable
internal fun rememberAirshipEmbeddedViewState(
    embeddedId: String,
    onAvailable: () -> Boolean = { true },
    onEmpty: () -> Unit = {},
    embeddedViewManager: AirshipEmbeddedViewManager,
): AirshipEmbeddedViewState {
    val context = LocalContext.current
    val state = remember { AirshipEmbeddedViewState(embeddedId) }

    LaunchedEffect(key1 = embeddedId) {
        withContext(Dispatchers.Default) {
            embeddedViewManager
                .displayRequests(embeddedId)
                .map { request ->
                    if (request == null) {
                        // Nothing to display, call onEmpty and return.
                        UALog.v { "No display request available for id: \"$embeddedId\"" }
                        onEmpty()
                        return@map null
                    }

                    if (!onAvailable()) {
                        // Display suppressed, return.
                        UALog.v { "Display request rejected for id: \"$embeddedId\"" }
                        return@map null
                    }

                    UALog.v { "Display request available for id: \"$embeddedId\"" }

                    // Inflate the embedded layout
                    val displayArgs = request.displayArgsProvider.invoke()
                    EmbeddedLayout(context, embeddedId, displayArgs, embeddedViewManager)
                }
                .collect { state.currentLayout = it }
        }
    }

    return state
}

/** State holder for [AirshipEmbeddedView]. */
@Stable
public class AirshipEmbeddedViewState(
    public val embeddedId: String
) {
    public var currentLayout: EmbeddedLayout? by mutableStateOf(null)

    /**
     * Flag indicating whether an embedded layout is available for display.
     *
     * This can be used in a composable to show or hide the embedded view.
     *
     * @return `true` if an embedded layout is available for display, otherwise `false`.
     */
    public val isAvailable: Boolean
        get() = currentLayout != null

    /** Dismiss the currently displayed layout. */
    public suspend fun dismissCurrent() {
        currentLayout?.let { layout ->
            EmbeddedViewManager.dismiss(layout.embeddedViewId, layout.viewInstanceId)
        }
    }

    /** Dismiss all pending embedded layouts for the current embedded view ID. */
    public suspend fun dismissAll() {
        currentLayout?.let { layout ->
            EmbeddedViewManager.dismissAll(layout.embeddedViewId)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AirshipEmbeddedViewState

        if (embeddedId != other.embeddedId) return false
        if (currentLayout != other.currentLayout) return false
        if (isAvailable != other.isAvailable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = embeddedId.hashCode()
        result = 31 * result + (currentLayout?.hashCode() ?: 0)
        result = 31 * result + isAvailable.hashCode()
        return result
    }


}
