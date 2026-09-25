/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A story's automated advance waits for the videos the page is showing to be ready --
 * [State.Pager.isMediaPaused] is what holds its timers, and [State.Pager.copyWithMediaPaused] is
 * how a video arms and releases that hold.
 */
public class PagerMediaGateTest {

    private val pager = State.Pager(
        identifier = "story-pager",
        pageIds = listOf("page-1", "page-2", "page-3")
    )

    @Test
    public fun aPageScopedVideoHoldsOnlyItsOwnPage() {
        val loading = pager.copyWithMediaPaused("page-2", "video-on-page-2", isPaused = true)

        assertFalse("page one isn't waiting on a video that lives on page two", loading.isMediaPaused)
        assertTrue(loading.copy(pageIndex = 1).isMediaPaused)
        assertFalse(loading.copy(pageIndex = 2).isMediaPaused)
    }

    /**
     * A video on the pager's own `backgrounds` stays on screen for every page, so it isn't scoped
     * to one and carries no page id. Skipping it -- which a `pageId ?: return` in
     * `MediaModel.setMediaLoading` used to do -- let a story advance off a page whose video hadn't
     * loaded yet, the one case the whole gate exists to prevent.
     */
    @Test
    public fun aPagerOwnedVideoHoldsWhicheverPageIsShowing() {
        val loading = pager.copyWithMediaPaused(pageId = null, videoId = "background-video", isPaused = true)

        assertTrue(loading.isMediaPaused)
        assertTrue(loading.copy(pageIndex = 1).isMediaPaused)
        assertTrue(loading.copy(pageIndex = 2).isMediaPaused)
    }

    @Test
    public fun aPagerOwnedVideoReleasesEveryPageOnceReady() {
        val ready = pager
            .copyWithMediaPaused(pageId = null, videoId = "background-video", isPaused = true)
            .copyWithMediaPaused(pageId = null, videoId = "background-video", isPaused = false)

        assertFalse(ready.isMediaPaused)
        assertFalse(ready.copy(pageIndex = 2).isMediaPaused)
    }

    @Test
    public fun oneReadyVideoDoesNotReleaseAPageAnotherIsStillHolding() {
        val loading = pager
            .copyWithMediaPaused(pageId = null, videoId = "background-video", isPaused = true)
            .copyWithMediaPaused("page-1", "video-on-page-1", isPaused = true)
            .copyWithMediaPaused("page-1", "video-on-page-1", isPaused = false)

        assertTrue("the pager's own video is still loading", loading.isMediaPaused)
    }
}
