/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.UAirship
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.info.AccessibilityAction
import com.urbanairship.android.layout.info.PagerInfo
import com.urbanairship.android.layout.property.AutomatedAction
import com.urbanairship.android.layout.util.PagerScrollEvent
import com.urbanairship.android.layout.util.pagerScrolls
import com.urbanairship.android.layout.view.PagerView
import com.urbanairship.json.JsonValue
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val mockReporter: Reporter = mockk(relaxUnitFun = true)
    private val mockActionsRunner: ThomasActionRunner = mockk(relaxUnitFun = true) {
        every { run(any(), any()) } answers { nothing }
    }
    private val mockEnv: ModelEnvironment = mockk(relaxUnitFun = true) {
        every { reporter } returns mockReporter
        every { actionsRunner } returns mockActionsRunner
        every { modelScope } returns testScope
        every { layoutState } returns mockk {
            every { reportingContext(any(), any(), any()) } returns mockk()
        }
    }
    private val mockView: PagerView = mockk(relaxUnitFun = true) {
        every { context } returns mockk {
            every { getSystemService(Context.ACCESSIBILITY_SERVICE) } returns mockk()
        }
    }
    private val mockViewListener: PagerModel.Listener = mockk(relaxUnitFun = true)

    private val pagerState: SharedState<State.Pager> =
        spyk(SharedState(State.Pager(identifier = PAGER_ID)))

    private lateinit var pagerModel: PagerModel

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)

        pagerModel = spyk(PagerModel(
            items = ITEMS,
            viewInfo = mockk<PagerInfo>(relaxUnitFun = true) {
                every { gestures } returns emptyList()
            },
            pagerState = pagerState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null)
        )).apply {
            listener = mockViewListener
        }

        mockkStatic(UAirship::class)
        every { UAirship.shared() } returns mockk {
            every { platformType } returns UAirship.ANDROID_PLATFORM
        }
        every { UAirship.getApplicationContext() } returns mockk()

        mockkStatic(PagerView::pagerScrolls)
        every { mockView.pagerScrolls() } returns scrollsFlow
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()

        unmockkStatic(PagerView::pagerScrolls)
        unmockkStatic(UAirship::class)
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
        assertEquals(3, pagerModel.items.size)
        // Verify that actions were run for the initial page display
        verify(exactly = 1) { mockActionsRunner.run(any(), any()) }
    }

    @Test
    public fun testUserScrolls(): TestResult = runTest {
        pagerState.changes.test {
            pagerModel.onViewAttached(mockView)
            advanceUntilIdle()
            // Verify we ran actions for the 1st page
            verify(exactly = 1) { mockActionsRunner.run(any(), any()) }

            val initialState = awaitItem()
            assertEquals(0, initialState.pageIndex)
            assertEquals(0, initialState.lastPageIndex)
            assertTrue(initialState.hasNext)
            assertFalse(initialState.hasPrevious)

            // Simulate a user swipe to the first page.
            scrollsFlow.emit(PagerScrollEvent(position = 1, isInternalScroll = false))
            advanceUntilIdle()

            val updatedState = awaitItem()
            advanceUntilIdle()
            assertEquals(1, updatedState.pageIndex)
            assertEquals(0, updatedState.lastPageIndex)
            assertTrue(updatedState.hasNext)
            assertTrue(updatedState.hasPrevious)

            // Verify that we reported an event and ran actions when scrolling to the 2nd page
            verify { mockReporter.report(any(), any()) }
            verify(exactly = 2) { mockActionsRunner.run(any(), any()) }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testInternalScrolls(): TestResult = runTest {
        pagerState.changes.test {
            pagerModel.onViewAttached(mockView)
            advanceUntilIdle()
            // Verify we ran actions for the 1st page
            verify(exactly = 1) { mockActionsRunner.run(any(), any()) }

            val initialState = awaitItem()
            assertEquals(0, initialState.pageIndex)
            assertEquals(0, initialState.lastPageIndex)
            assertTrue(initialState.hasNext)
            assertFalse(initialState.hasPrevious)

            // Simulate an internal scroll to the first page.
            scrollsFlow.emit(PagerScrollEvent(position = 1, isInternalScroll = true))
            advanceUntilIdle()

            val updatedState = awaitItem()
            assertEquals(1, updatedState.pageIndex)
            assertEquals(0, updatedState.lastPageIndex)
            assertTrue(updatedState.hasNext)
            assertTrue(updatedState.hasPrevious)

            // Verify that we didn't report an event, but did run
            // actions again when scrolling to the 2nd page.
            verify(exactly = 0) { mockReporter.report(any(), any()) }
            verify(exactly = 2) { mockActionsRunner.run(any(), any()) }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testStateChanges(): TestResult = runTest {
        pagerModel.onViewAttached(mockView)
        advanceUntilIdle()

        // Verify actions were run for the initial page display
        verify(exactly = 1) { mockActionsRunner.run(any(), any()) }

        pagerState.update { it.copyWithPageIndex(1) }
        // Run the pending state update task, so the model can process it.
        advanceUntilIdle()

        val state = pagerState.changes.first()
        // Sanity check
        assertEquals(1, state.pageIndex)

        verify { mockViewListener.scrollTo(1) }
        // Verify actions were also run on display of the next page
        verify(exactly = 2) { mockActionsRunner.run(any(), any()) }
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
        private const val PAGE_3_ID = "page-two-identifier"
        private val EMPTY_ACTIONS = emptyMap<String, JsonValue>()
        private val EMPTY_AUTOMATED_ACTIONS = emptyList<AutomatedAction>()
        private val EMPTY_ACCESSIBILITY_ACTIONS = emptyList<AccessibilityAction>()

        private val ITEMS = listOf(
            PagerModel.Item(mockk(relaxed = true), PAGE_1_ID, EMPTY_ACTIONS, EMPTY_AUTOMATED_ACTIONS, EMPTY_ACCESSIBILITY_ACTIONS),
            PagerModel.Item(mockk(relaxed = true), PAGE_2_ID, EMPTY_ACTIONS, EMPTY_AUTOMATED_ACTIONS, EMPTY_ACCESSIBILITY_ACTIONS),
            PagerModel.Item(mockk(relaxed = true), PAGE_3_ID, EMPTY_ACTIONS, EMPTY_AUTOMATED_ACTIONS, EMPTY_ACCESSIBILITY_ACTIONS)
        )
        private val PAGE_IDS = ITEMS.map { it.identifier }
    }
}
