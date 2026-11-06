/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.gestures.PagerGestureEvent
import com.urbanairship.android.layout.info.AccessibilityAction
import com.urbanairship.android.layout.info.PagerInfo
import com.urbanairship.android.layout.property.AutomatedAction
import com.urbanairship.android.layout.property.GestureLocation
import com.urbanairship.android.layout.property.Outcome
import com.urbanairship.android.layout.property.PagerGesture
import com.urbanairship.json.JsonValue
import com.urbanairship.android.layout.util.PagerScrollEvent
import com.urbanairship.android.layout.util.pagerGestures
import com.urbanairship.android.layout.util.pagerScrolls
import com.urbanairship.android.layout.view.PagerView
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class PagerModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val scrollsFlow = MutableSharedFlow<PagerScrollEvent>()
    private val gesturesFlow = MutableSharedFlow<PagerGestureEvent>()
    private val layoutEventsFlow = MutableSharedFlow<LayoutEvent>()
    private val isScrollingFlow = MutableStateFlow(false)

    private val mockReporter: Reporter = mockk(relaxUnitFun = true)
    private val mockActionsRunner: ThomasActionRunner = mockk(relaxUnitFun = true) {
        coEvery { run(any(), any()) } answers { nothing }
    }
    private val mockEnv: ModelEnvironment = mockk(relaxed = true, relaxUnitFun = true) {
        every { reporter } returns mockReporter
        every { actionsRunner } returns mockActionsRunner
        every { modelScope } returns testScope
        every { layoutState } returns mockk(relaxed = true) {
            every { reportingContext(any(), any(), any()) } returns mockk()
        }
        every { layoutEvents } returns layoutEventsFlow
    }
    private val mockView: PagerView = mockk(relaxUnitFun = true) {
        every { context } returns mockk {
            every { getSystemService(Context.ACCESSIBILITY_SERVICE) } returns mockk()
        }
    }
    private val mockViewListener: PagerModel.Listener = mockk(relaxUnitFun = true)

    private val pagerState: SharedState<State.Pager> =
        spyk(SharedState(State.Pager(identifier = PAGER_ID)))

    private val mockViewInfo: PagerInfo = mockk(relaxUnitFun = true) {
        every { gestures } returns emptyList()
        every { disableSwipeWhen } returns null
        every { backgrounds } returns null
        every { isSwipeDisabled } returns false
    }

    private lateinit var pagerModel: PagerModel

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)

        pagerModel =
            spyk(PagerModel(
            availablePages = ITEMS,
            viewInfo = mockViewInfo,
            pagerState = pagerState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null)
        )).apply {
            listener = mockViewListener
        }

        mockkStatic(Airship::class)
        every { Airship.platform } returns Platform.ANDROID
        every { Airship.application } returns mockk()

        mockkStatic(PagerView::pagerScrolls)
        every { mockView.pagerScrolls() } returns scrollsFlow
        every { mockView.pagerGestures() } returns gesturesFlow
        every { mockView.isScrolling } returns isScrollingFlow
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()

        isScrollingFlow.value = false

        unmockkStatic(Airship::class)
        unmockkStatic(PagerView::pagerScrolls)
    }

    @Test
    public fun testInitialState(): TestResult = runTest {
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        verify { pagerState.update(any()) }

        val state = pagerState.changes.first()
        advanceUntilIdle()

        // Sanity check
        assertEquals(PAGER_ID, state.identifier)
        // Verify that the model set page IDs on pagerState
        assertEquals(PAGE_IDS, state.pageIds)
        // Verify that the correct number of page items is available via the model
        assertEquals(3, pagerModel.pages.size)
        // Verify that actions were run for the initial page display
        coVerify(exactly = 1) { mockActionsRunner.run(any(), any()) }
    }

    @Test
    public fun testNavigationFollowsOnlyItsOwnController(): TestResult = runTest {
        pagerState.changes.test {
            pagerModel.onViewAttached(mockView)
            advanceUntilIdle()
            assertEquals(0, awaitItem().pageIndex)

            // Sent from outside this pager's controller, as an enclosing pager's Next button is.
            val enclosingPager = SharedState(State.Pager(identifier = "enclosing"))
            layoutEventsFlow.emit(LayoutEvent.Pager.Next(PagerNextFallback.NONE, enclosingPager))
            advanceUntilIdle()
            expectNoEvents()

            layoutEventsFlow.emit(LayoutEvent.Pager.Next(PagerNextFallback.NONE, pagerState))
            advanceUntilIdle()
            assertEquals(1, awaitItem().pageIndex)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun testNavigationFromOutsideAnyControllerStillReachesThePager(): TestResult = runTest {
        pagerState.changes.test {
            pagerModel.onViewAttached(mockView)
            advanceUntilIdle()
            assertEquals(0, awaitItem().pageIndex)

            // A button with no enclosing pager_controller sends no pager state.
            layoutEventsFlow.emit(LayoutEvent.Pager.Next(PagerNextFallback.NONE, pager = null))
            advanceUntilIdle()
            assertEquals(1, awaitItem().pageIndex)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    public fun testUserScrolls(): TestResult = runTest {
        pagerState.changes.test {
            pagerModel.onViewAttached(mockView)
            advanceUntilIdle()
            // Verify we ran actions for the 1st page
            coVerify(exactly = 1) { mockActionsRunner.run(any(), any()) }

            val initialState = awaitItem()
            assertEquals(0, initialState.pageIndex)
            assertEquals(0, initialState.lastPageIndex)
            assertTrue(initialState.hasNext)
            assertFalse(initialState.hasPrevious)

            // Simulate a user swipe to the second page.
            scrollsFlow.emit(PagerScrollEvent(position = 1, isInternalScroll = false))
            advanceUntilIdle()

            val resolvedState = awaitItem()
            assertEquals(1, resolvedState.pageIndex)
            assertEquals(0, resolvedState.lastPageIndex)
            assertFalse(resolvedState.isScrolling)

            // Simulate RecyclerView entering non-idle (DRAGGING/SETTLING).
            isScrollingFlow.value = true
            advanceUntilIdle()

            val scrollingState = awaitItem()
            assertEquals(1, scrollingState.pageIndex)
            assertEquals(0, scrollingState.lastPageIndex)
            assertTrue(scrollingState.hasNext)
            assertTrue(scrollingState.hasPrevious)
            assertTrue(scrollingState.isScrolling)

            // Simulate the RecyclerView reaching SCROLL_STATE_IDLE (true → false transition).
            isScrollingFlow.value = false
            advanceUntilIdle()

            val updatedState = awaitItem()
            assertEquals(1, updatedState.pageIndex)
            assertEquals(0, updatedState.lastPageIndex)
            assertTrue(updatedState.hasNext)
            assertTrue(updatedState.hasPrevious)
            assertFalse(updatedState.isScrolling)

            // Verify that we reported an event and ran actions when scrolling to the 2nd page
            verify { mockReporter.report(any()) }
            coVerify(exactly = 2) { mockActionsRunner.run(any(), any()) }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testInternalScrolls(): TestResult = runTest {
        pagerState.changes.test {
            pagerModel.onViewAttached(mockView)
            advanceUntilIdle()
            // Verify we ran actions for the 1st page
            coVerify(exactly = 1) { mockActionsRunner.run(any(), any()) }

            val initialState = awaitItem()
            assertEquals(0, initialState.pageIndex)
            assertEquals(0, initialState.lastPageIndex)
            assertTrue(initialState.hasNext)
            assertFalse(initialState.hasPrevious)

            // Simulate an internal scroll to the second page.
            scrollsFlow.emit(PagerScrollEvent(position = 1, isInternalScroll = true))
            advanceUntilIdle()

            // resolve() updates pageIndex but does not set isScrolling.
            val resolvedState = awaitItem()
            assertEquals(1, resolvedState.pageIndex)
            assertEquals(0, resolvedState.lastPageIndex)
            assertFalse(resolvedState.isScrolling)

            // Simulate RecyclerView entering non-idle (DRAGGING/SETTLING).
            isScrollingFlow.value = true
            advanceUntilIdle()

            val scrollingState = awaitItem()
            assertEquals(1, scrollingState.pageIndex)
            assertEquals(0, scrollingState.lastPageIndex)
            assertTrue(scrollingState.hasNext)
            assertTrue(scrollingState.hasPrevious)
            assertTrue(scrollingState.isScrolling)

            // Simulate the RecyclerView reaching SCROLL_STATE_IDLE (true → false transition).
            isScrollingFlow.value = false
            advanceUntilIdle()

            val updatedState = awaitItem()
            assertEquals(1, updatedState.pageIndex)
            assertEquals(0, updatedState.lastPageIndex)
            assertTrue(updatedState.hasNext)
            assertTrue(updatedState.hasPrevious)
            assertFalse(updatedState.isScrolling)

            // Verify that we didn't report an event, but did run
            // actions again when scrolling to the 2nd page.
            verify(exactly = 0) { mockReporter.report(any()) }
            coVerify(exactly = 2) { mockActionsRunner.run(any(), any()) }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testStateChanges(): TestResult = runTest {
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        // Verify actions were run for the initial page display
        coVerify(exactly = 1) { mockActionsRunner.run(any(), any()) }

        pagerState.update { it.copyWithPageIndex(1) }
        // Run the pending state update task, so the model can process it.
        advanceUntilIdle()

        val state = pagerState.changes.first()
        // Sanity check
        assertEquals(1, state.pageIndex)

        verify { mockViewListener.scrollTo(1, any()) }
        // Verify actions were also run on display of the next page
        coVerify(exactly = 2) { mockActionsRunner.run(any(), any()) }
    }

    @Test
    public fun testFirstNavigationAfterAttachAnimates(): TestResult = runTest {
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        pagerState.update { it.copyWithPageIndex(1) }
        advanceUntilIdle()

        verify(exactly = 1) { mockViewListener.scrollTo(1, true) }
        verify(exactly = 0) { mockViewListener.scrollTo(any(), false) }
    }

    @Test
    public fun testRestoredPageSnapsThenNavigationAnimates(): TestResult = runTest {
        pagerState.update { it.copyWithPageIndex(1) }
        advanceUntilIdle()

        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        verify(exactly = 1) { mockViewListener.scrollTo(1, false) }

        pagerState.update { it.copyWithPageIndex(2) }
        advanceUntilIdle()

        verify(exactly = 1) { mockViewListener.scrollTo(2, true) }
    }

    @Test
    public fun testDirectionalKeyPagesLikeASwipe(): TestResult = runTest {
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        assertTrue(pagerModel.onDirectionalKey(isRight = true, isRtl = false))
        advanceUntilIdle()
        assertEquals(1, pagerState.changes.value.pageIndex)

        assertTrue(pagerModel.onDirectionalKey(isRight = false, isRtl = false))
        advanceUntilIdle()
        assertEquals(0, pagerState.changes.value.pageIndex)

        verify(exactly = 2) { mockReporter.report(match { it is ReportingEvent.PageSwipe }) }
    }

    @Test
    public fun testDirectionalKeyFollowsLayoutDirection(): TestResult = runTest {
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        assertFalse(pagerModel.onDirectionalKey(isRight = true, isRtl = true))
        assertTrue(pagerModel.onDirectionalKey(isRight = false, isRtl = true))
        advanceUntilIdle()
        assertEquals(1, pagerState.changes.value.pageIndex)
    }

    @Test
    public fun testDirectionalKeyStopsAtTheEnds(): TestResult = runTest {
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        assertFalse(pagerModel.onDirectionalKey(isRight = false, isRtl = false))

        pagerState.update { it.copyWithPageIndex(2) }
        advanceUntilIdle()
        assertFalse(pagerModel.onDirectionalKey(isRight = true, isRtl = false))

        verify(exactly = 0) { mockReporter.report(match { it is ReportingEvent.PageSwipe }) }
    }

    @Test
    public fun testDirectionalKeyDoesNotPageWhenSwipeIsDisabled(): TestResult = runTest {
        every { mockViewInfo.isSwipeDisabled } returns true
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        assertFalse(pagerModel.onDirectionalKey(isRight = true, isRtl = false))
        advanceUntilIdle()
        assertEquals(0, pagerState.changes.value.pageIndex)
    }

    @Test
    public fun testDirectionalKeyRunsTheEdgeTapGesture(): TestResult = runTest {
        every { mockViewInfo.isSwipeDisabled } returns true
        every { mockViewInfo.gestures } returns listOf(
            PagerGesture.Tap(
                identifier = "end-tap",
                reportingMetadata = null,
                location = GestureLocation.END,
                outcomes = listOf(
                    Outcome.PagerStepNavigation("next", Outcome.PagerStepNavigation.Direction.NEXT)
                )
            )
        )
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        assertTrue(pagerModel.onDirectionalKey(isRight = true, isRtl = false))
        advanceUntilIdle()
        assertEquals(1, pagerState.changes.value.pageIndex)
        verify { mockReporter.report(match { it is ReportingEvent.Gesture }) }
        verify(exactly = 0) { mockReporter.report(match { it is ReportingEvent.PageSwipe }) }

        // The end edge is on the left in RTL, and nothing is tapped on the right.
        assertFalse(pagerModel.onDirectionalKey(isRight = true, isRtl = true))
    }

    @Test
    public fun testDirectionalKeyRunsATapAnywhereGesture(): TestResult = runTest {
        every { mockViewInfo.isSwipeDisabled } returns true
        every { mockViewInfo.gestures } returns listOf(
            PagerGesture.Tap(
                identifier = "any-tap",
                reportingMetadata = null,
                location = GestureLocation.ANY,
                outcomes = listOf(
                    Outcome.PagerStepNavigation("next", Outcome.PagerStepNavigation.Direction.NEXT)
                )
            )
        )
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        assertTrue(pagerModel.onDirectionalKey(isRight = false, isRtl = false))
        advanceUntilIdle()
        assertEquals(1, pagerState.changes.value.pageIndex)
    }

    @Test
    public fun testDirectionalKeySwipesBeforeATapAnywhereGesture(): TestResult = runTest {
        every { mockViewInfo.gestures } returns listOf(
            PagerGesture.Tap(
                identifier = "any-tap",
                reportingMetadata = null,
                location = GestureLocation.ANY,
                outcomes = listOf(
                    Outcome.PagerStepNavigation("next", Outcome.PagerStepNavigation.Direction.NEXT)
                )
            )
        )
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        // Right and Left swipe while a swipe is possible...
        assertTrue(pagerModel.onDirectionalKey(isRight = true, isRtl = false))
        advanceUntilIdle()
        assertEquals(1, pagerState.changes.value.pageIndex)
        assertTrue(pagerModel.onDirectionalKey(isRight = false, isRtl = false))
        advanceUntilIdle()
        assertEquals(0, pagerState.changes.value.pageIndex)
        verify(exactly = 2) { mockReporter.report(match { it is ReportingEvent.PageSwipe }) }
        verify(exactly = 0) { mockReporter.report(match { it is ReportingEvent.Gesture }) }

        // ...and the tap anywhere runs once there's nothing to swipe back to.
        assertTrue(pagerModel.onDirectionalKey(isRight = false, isRtl = false))
        advanceUntilIdle()
        assertEquals(1, pagerState.changes.value.pageIndex)
        verify(exactly = 1) { mockReporter.report(match { it is ReportingEvent.Gesture }) }
    }

    @Test
    public fun testEdgeTapRunsATapAnywhereGestureOnce(): TestResult = runTest {
        every { mockViewInfo.gestures } returns listOf(
            PagerGesture.Tap(
                identifier = "any-tap",
                reportingMetadata = null,
                location = GestureLocation.ANY,
                outcomes = listOf(
                    Outcome.PagerStepNavigation("next", Outcome.PagerStepNavigation.Direction.NEXT)
                )
            )
        )
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        listOf(GestureLocation.RIGHT, GestureLocation.END, GestureLocation.ANY).forEach {
            gesturesFlow.emit(PagerGestureEvent.Tap(it))
        }
        advanceUntilIdle()
        assertEquals(1, pagerState.changes.value.pageIndex)
    }

    @Test
    public fun testHandlesDirectionalKeys() {
        assertTrue(pagerModel.handlesDirectionalKeys)

        every { mockViewInfo.isSwipeDisabled } returns true
        assertFalse(pagerModel.handlesDirectionalKeys)

        every { mockViewInfo.gestures } returns listOf(
            PagerGesture.Tap(
                identifier = "start-tap",
                reportingMetadata = null,
                location = GestureLocation.START,
                outcomes = emptyList()
            )
        )
        assertTrue(pagerModel.handlesDirectionalKeys)

        every { mockViewInfo.gestures } returns listOf(
            PagerGesture.Tap(
                identifier = "any-tap",
                reportingMetadata = null,
                location = GestureLocation.ANY,
                outcomes = emptyList()
            )
        )
        assertTrue(pagerModel.handlesDirectionalKeys)
    }

    @Test
    public fun testHandlesDirectionalKeysFollowsDisabledScrolling() {
        assertTrue(pagerModel.handlesDirectionalKeys)

        pagerState.update { it.copy(isScrollDisabled = true) }
        assertFalse(pagerModel.handlesDirectionalKeys)

        pagerState.update { it.copy(isScrollDisabled = false) }
        assertTrue(pagerModel.handlesDirectionalKeys)
    }

    @Test
    public fun testPageViewIds() {
        val id0 = pagerModel.getPageViewId(0)
        val id1 = pagerModel.getPageViewId(1)
        val id2 = pagerModel.getPageViewId(2)

        // Ensure the same IDs are returned for subsequent gets
        assertEquals(id0, pagerModel.getPageViewId(0))
        assertEquals(id1, pagerModel.getPageViewId(1))
        assertEquals(id2, pagerModel.getPageViewId(2))
    }

    private companion object {
        private const val PAGER_ID = "pager"
        private const val PAGE_1_ID = "page-one-identifier"
        private const val PAGE_2_ID = "page-two-identifier"
        private const val PAGE_3_ID = "page-three-identifier"
        private val EMPTY_AUTOMATED_ACTIONS = emptyList<AutomatedAction>()
        private val EMPTY_ACCESSIBILITY_ACTIONS = emptyList<AccessibilityAction>()
        private val DISPLAY_OUTCOMES = listOf(
            Outcome.AirshipAction(
                identifier = "display_action_outcome",
                actions = mapOf("display_action" to JsonValue.wrap("value"))
            )
        )

        private val ITEMS = listOf(
            PagerModel.Item(mockk(relaxed = true), PAGE_1_ID, EMPTY_AUTOMATED_ACTIONS, EMPTY_ACCESSIBILITY_ACTIONS, DISPLAY_OUTCOMES, null),
            PagerModel.Item(mockk(relaxed = true), PAGE_2_ID, EMPTY_AUTOMATED_ACTIONS, EMPTY_ACCESSIBILITY_ACTIONS, DISPLAY_OUTCOMES, null),
            PagerModel.Item(mockk(relaxed = true), PAGE_3_ID, EMPTY_AUTOMATED_ACTIONS, EMPTY_ACCESSIBILITY_ACTIONS, DISPLAY_OUTCOMES, null)
        )
        private val PAGE_IDS = ITEMS.map { it.identifier }
    }
}
