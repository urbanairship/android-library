/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbanairship.R as CoreR
import com.urbanairship.embedded.AirshipEmbeddedSelection
import com.urbanairship.embedded.EmbeddedViewManager
import kotlinx.coroutines.launch

/**
 * State holder for [AirshipEmbeddedCarousel].
 *
 * @param groupState The [AirshipEmbeddedViewGroupState] managing available embedded views.
 * @param pagerState The [PagerState] driving the carousel.
 */
@Stable
public class AirshipEmbeddedCarouselState internal constructor(
    public val groupState: AirshipEmbeddedViewGroupState,
    public val pagerState: PagerState,
) {
    /** Total number of pages. */
    public val pageCount: Int by derivedStateOf { groupState.items.value.size }

    /** Index of the currently visible page. */
    public val currentPage: Int get() = pagerState.currentPage

    /** `true` when at least one embedded view is available for display. */
    public val isAvailable: Boolean by derivedStateOf { groupState.items.value.isNotEmpty() }

    /** Dismiss the currently displayed page's content. */
    public fun dismissCurrent() {
        groupState.items.value.getOrNull(pagerState.currentPage)?.let {
            EmbeddedViewManager.dismiss(it.info.embeddedId, it.info.instanceId)
        }
    }

    /** Dismiss all pending embedded content for the carousel's embedded ID. */
    public fun dismissAll() {
        EmbeddedViewManager.dismissAll(groupState.embeddedId)
    }
}

/**
 * Creates and remembers an [AirshipEmbeddedCarouselState] for the given [embeddedId].
 *
 * @param embeddedId The embedded ID.
 * @param selection Controls which instances are selected for display. Only [AirshipEmbeddedSelection.ByComparator]
 *   sorts instances deterministically; [AirshipEmbeddedSelection.Priority] and
 *   [AirshipEmbeddedSelection.ByInstanceId] preserve arrival order, matching [AirshipEmbeddedViewGroup].
 */
@Composable
public fun rememberAirshipEmbeddedCarouselState(
    embeddedId: String,
    selection: AirshipEmbeddedSelection = AirshipEmbeddedSelection.Priority,
): AirshipEmbeddedCarouselState {
    val groupState = rememberAirshipEmbeddedViewGroupState(embeddedId, selection)
    val pagerState = rememberPagerState { groupState.items.value.size }
    return remember { AirshipEmbeddedCarouselState(groupState, pagerState) }
}

/**
 * A container that displays all embedded content for the given [embeddedId] in a swipeable carousel.
 *
 * When multiple pieces of embedded content share the same ID, each is shown as a separate page.
 *
 * @param embeddedId The embedded ID.
 * @param modifier The modifier to be applied to the layout.
 * @param selection Controls which instances are selected for display. Only [AirshipEmbeddedSelection.ByComparator]
 *   sorts instances deterministically; [AirshipEmbeddedSelection.Priority] and
 *   [AirshipEmbeddedSelection.ByInstanceId] preserve arrival order, matching [AirshipEmbeddedViewGroup].
 * @param indicator Optional overlay composable for page indicators. Receives the [PagerState] and page count.
 *   Use [AirshipEmbeddedCarouselDefaults.dotsIndicator] for a simple default.
 * @param previousArrow Optional composable for a "previous page" button, positioned at [Alignment.CenterStart].
 *   Use [AirshipEmbeddedCarouselDefaults.previousArrow] for a simple accessible default.
 * @param nextArrow Optional composable for a "next page" button, positioned at [Alignment.CenterEnd].
 *   Use [AirshipEmbeddedCarouselDefaults.nextArrow] for a simple accessible default.
 * @param placeholder Optional composable displayed when no embedded content is available.
 */
@Composable
public fun AirshipEmbeddedCarousel(
    embeddedId: String,
    modifier: Modifier = Modifier,
    selection: AirshipEmbeddedSelection = AirshipEmbeddedSelection.Priority,
    indicator: (@Composable BoxScope.(pagerState: PagerState, pageCount: Int) -> Unit)? = null,
    previousArrow: (@Composable (onClick: () -> Unit, enabled: Boolean) -> Unit)? = null,
    nextArrow: (@Composable (onClick: () -> Unit, enabled: Boolean) -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
) {
    AirshipEmbeddedCarousel(
        state = rememberAirshipEmbeddedCarouselState(embeddedId, selection),
        modifier = modifier,
        indicator = indicator,
        previousArrow = previousArrow,
        nextArrow = nextArrow,
        placeholder = placeholder,
    )
}

/**
 * A container that displays embedded carousel content for the `embeddedId` defined on the given
 * [AirshipEmbeddedCarouselState].
 *
 * This composable may be useful when access to the carousel state is needed outside of the
 * `AirshipEmbeddedCarousel` composable, e.g. to observe [AirshipEmbeddedCarouselState.isAvailable].
 *
 * @param state The [AirshipEmbeddedCarouselState] to be used by this carousel.
 * @param modifier The modifier to be applied to the layout.
 * @param indicator Optional overlay composable for page indicators.
 * @param previousArrow Optional composable for a "previous page" button.
 * @param nextArrow Optional composable for a "next page" button.
 * @param placeholder Optional composable displayed when no embedded content is available.
 */
@Composable
public fun AirshipEmbeddedCarousel(
    state: AirshipEmbeddedCarouselState,
    modifier: Modifier = Modifier,
    indicator: (@Composable BoxScope.(pagerState: PagerState, pageCount: Int) -> Unit)? = null,
    previousArrow: (@Composable (onClick: () -> Unit, enabled: Boolean) -> Unit)? = null,
    nextArrow: (@Composable (onClick: () -> Unit, enabled: Boolean) -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
) {
    val items by state.groupState.items

    Box(modifier = modifier) {
        if (items.isEmpty()) {
            placeholder?.invoke()
            return@Box
        }

        val scope = rememberCoroutineScope()

        val hasPrevious by remember { derivedStateOf { state.pagerState.currentPage > 0 } }
        val hasNext by remember { derivedStateOf { state.pagerState.currentPage < items.size - 1 } }

        if (LocalInspectionMode.current) {
            Box(modifier = Modifier.fillMaxSize()) {
                BasicText(text = "AirshipEmbeddedCarousel (${items.size} pages)")
            }
        } else {
            HorizontalPager(
                state = state.pagerState,
                key = { items[it].info.instanceId },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                items[page].content()
            }
        }

        indicator?.invoke(this, state.pagerState, items.size)

        if (previousArrow != null) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                previousArrow(
                    { scope.launch { state.pagerState.animateScrollToPage(state.pagerState.currentPage - 1) } },
                    hasPrevious,
                )
            }
        }

        if (nextArrow != null) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                nextArrow(
                    { scope.launch { state.pagerState.animateScrollToPage(state.pagerState.currentPage + 1) } },
                    hasNext,
                )
            }
        }
    }
}

/** Default values for [AirshipEmbeddedCarousel]. */
public object AirshipEmbeddedCarouselDefaults {

    /**
     * A minimal accessible "previous page" button, announced by TalkBack using one of
     * Airship's own localized strings (e.g. "Previous" in English, "Précédent" in French).
     * Replace with your own composable for custom styling.
     *
     * @param color The color of the arrow glyph.
     * @param fontSize The size of the arrow glyph.
     */
    public fun previousArrow(
        color: Color = Color.Black,
        fontSize: TextUnit = 24.sp,
    ): @Composable (onClick: () -> Unit, enabled: Boolean) -> Unit =
        { onClick, enabled ->
            val description = stringResource(CoreR.string.ua_icon_button_backward_arrow)
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            BasicText(
                text = if (isRtl) "›" else "‹",
                style = TextStyle(color = color, fontSize = fontSize),
                modifier = Modifier
                    .semantics {
                        role = Role.Button
                        contentDescription = description
                        if (!enabled) disabled()
                    }
                    .clickable(enabled = enabled, onClick = onClick)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center)
                    .padding(12.dp),
            )
        }

    /**
     * A minimal accessible "next page" button, announced by TalkBack using one of
     * Airship's own localized strings (e.g. "Next" in English, "Suivant" in French).
     * Replace with your own composable for custom styling.
     *
     * @param color The color of the arrow glyph.
     * @param fontSize The size of the arrow glyph.
     */
    public fun nextArrow(
        color: Color = Color.Black,
        fontSize: TextUnit = 24.sp,
    ): @Composable (onClick: () -> Unit, enabled: Boolean) -> Unit =
        { onClick, enabled ->
            val description = stringResource(CoreR.string.ua_icon_button_forward_arrow)
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            BasicText(
                text = if (isRtl) "‹" else "›",
                style = TextStyle(color = color, fontSize = fontSize),
                modifier = Modifier
                    .semantics {
                        role = Role.Button
                        contentDescription = description
                        if (!enabled) disabled()
                    }
                    .clickable(enabled = enabled, onClick = onClick)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center)
                    .padding(12.dp),
            )
        }

    /**
     * A minimal dots page indicator, positioned at [Alignment.BottomCenter].
     *
     * Announced as e.g. "Page 2 of 5" by TalkBack, using the same localized Airship string as
     * the Scene/Story pager indicator, and merges the individual dots into a single
     * accessibility node so they aren't traversed one by one.
     *
     * Replace with your own composable for custom styling.
     */
    public val dotsIndicator: @Composable BoxScope.(pagerState: PagerState, pageCount: Int) -> Unit =
        { pagerState, pageCount ->
            val progressDescription = stringResource(
                CoreR.string.ua_pager_progress,
                pagerState.currentPage + 1,
                pageCount,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clearAndSetSemantics {
                        contentDescription = progressDescription
                    },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { index ->
                    val isActive = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (isActive) Color.White else Color.White.copy(alpha = 0.4f))
                    )
                }
            }
        }
}

@Preview
@Composable
private fun AirshipEmbeddedCarouselPreview() {
    AirshipEmbeddedCarousel(
        embeddedId = "embeddedId",
        modifier = Modifier.fillMaxSize(),
        indicator = AirshipEmbeddedCarouselDefaults.dotsIndicator,
        previousArrow = AirshipEmbeddedCarouselDefaults.previousArrow(),
        nextArrow = AirshipEmbeddedCarouselDefaults.nextArrow(),
    )
}
