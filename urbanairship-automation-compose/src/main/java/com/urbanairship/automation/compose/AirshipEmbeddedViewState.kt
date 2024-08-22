/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import android.widget.RelativeLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.platform.LocalContext
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.Size.DimensionType.AUTO
import com.urbanairship.android.layout.property.Size.DimensionType.PERCENT
import com.urbanairship.android.layout.ui.EmbeddedLayout
import com.urbanairship.embedded.AirshipEmbeddedInfo
import com.urbanairship.embedded.EmbeddedViewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.round


/**
 * Creates a [AirshipEmbeddedViewState] that can be used to manage the state of an embedded view.
 */
@Composable
public fun rememberAirshipEmbeddedViewState(
    embeddedId: String,
    comparator: Comparator<AirshipEmbeddedInfo>? = null
): AirshipEmbeddedViewState {
    return rememberAirshipEmbeddedViewState(embeddedId, comparator, EmbeddedViewManager)
}

/** State holder for [EmbeddedViewContent]. */
@Stable
public class AirshipEmbeddedViewState(
    public val embeddedId: String
) {
    internal var currentLayout: EmbeddedLayout? by mutableStateOf(null)

    /**
     * Flag indicating whether an embedded layout is available for display.
     *
     * This can be used in a composable to show or hide the embedded view.
     *
     * @return `true` if an embedded layout is available for display, otherwise `false`.
     */
    public val isAvailable: Boolean by derivedStateOf(structuralEqualityPolicy()) {
        currentLayout != null
    }

    /** Dismiss the currently displayed content. */
    public suspend fun dismissCurrent(): Unit = coroutineScope {
        currentLayout?.let { layout ->
            EmbeddedViewManager.dismiss(layout.embeddedViewId, layout.viewInstanceId)
        }
    }

    /** Dismiss all pending embedded content for the current embedded view ID. */
    public suspend fun dismissAll(): Unit = coroutineScope {
        currentLayout?.let { layout ->
            EmbeddedViewManager.dismissAll(layout.embeddedViewId)
        }
    }

    internal val placementSize: ConstrainedSize? by derivedStateOf {
        currentLayout?.getPlacement()?.size
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AirshipEmbeddedViewState

        if (embeddedId != other.embeddedId) return false
        if (currentLayout != other.currentLayout) return false

        return true
    }

    override fun hashCode(): Int {
        var result = embeddedId.hashCode()
        result = 31 * result + (currentLayout?.hashCode() ?: 0)
        return result
    }
}

/**
 * Creates an [AirshipEmbeddedViewState] and launches an effect to collect pending display requests.
 */
@Composable
internal fun rememberAirshipEmbeddedViewState(
    embeddedId: String,
    comparator: Comparator<AirshipEmbeddedInfo>?,
    embeddedViewManager: AirshipEmbeddedViewManager,
): AirshipEmbeddedViewState {
    val context = LocalContext.current
    val state = remember { AirshipEmbeddedViewState(embeddedId) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(key1 = embeddedId) {
        // Collect display requests and update the current layout state.
        withContext(Dispatchers.Default) {
            embeddedViewManager.displayRequests(embeddedId, comparator, scope)
                .map { request ->
                    if (request == null) {
                        // Nothing to display.
                        UALog.v { "No display request available for id: \"$embeddedId\"" }
                        null
                    } else {
                        // Inflate the embedded layout.
                        UALog.v { "Display request available for id: \"$embeddedId\"" }
                        val displayArgs = request.displayArgsProvider.invoke()
                        EmbeddedLayout(context, embeddedId, request.viewInstanceId, displayArgs, embeddedViewManager)
                    }
                }
                .collect { state.currentLayout = it }
        }
    }

    return state
}

//
// Helpers
//

internal fun ConstrainedSize.toEmbeddedSize(
    parentWidthProvider: (() -> Int)?,
    parentHeightProvider: (() -> Int)?
): EmbeddedSize =
    EmbeddedSize(
        width = width.toEmbeddedDimension(parentWidthProvider),
        height = height.toEmbeddedDimension(parentHeightProvider)
    )

private fun Size.Dimension.toEmbeddedDimension(
    parentDimensionProvider: (() -> Int)?
): EmbeddedDimension = when (this.type) {
    AUTO -> EmbeddedDimension(RelativeLayout.LayoutParams.WRAP_CONTENT, false)
    PERCENT -> parentDimensionProvider?.invoke()?.let { parentDimension ->
        EmbeddedDimension(round(this.float * parentDimension).toInt(), true)
    }
    else -> null
} ?: EmbeddedDimension(RelativeLayout.LayoutParams.MATCH_PARENT,  false)

internal data class EmbeddedSize(
    val width: EmbeddedDimension,
    val height: EmbeddedDimension
)

internal data class EmbeddedDimension(
    val spec: Int,
    val fill: Boolean
)
