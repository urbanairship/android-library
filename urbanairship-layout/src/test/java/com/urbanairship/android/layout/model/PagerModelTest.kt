/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import app.cash.turbine.test
import com.urbanairship.android.layout.environment.ActionsRunner
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.util.PagerScrollEvent
import com.urbanairship.android.layout.util.pagerScrolls
import com.urbanairship.android.layout.view.PagerView
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

    private val mockReporter: Reporter = mockk(relaxed = true)
    private val mockActionsRunner: ActionsRunner = mockk(relaxed = true)
    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { reporter } returns mockReporter
        every { actionsRunner } returns mockActionsRunner
        every { modelScope } returns testScope
    }
    private val mockView: PagerView = mockk(relaxed = true)
    private val mockViewListener: PagerModel.Listener = mockk(relaxed = true)

    private val pagerState: SharedState<State.Pager> =
        spyk(SharedState(State.Pager(identifier = PAGER_ID)))

    private lateinit var pagerModel: PagerModel

    @Before
    public fun setup() {
        pagerModel = spyk(PagerModel(
            items = ITEMS,
            isSwipeDisabled = false,
            pagerState = pagerState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null)
        )).apply {
            listener = mockViewListener
        }

        mockkStatic(PagerView::pagerScrolls)
        every { mockView.pagerScrolls() } returns scrollsFlow

        testScope.runCurrent()
    }

    @Test
    public fun testInitialState(): TestResult = runTest {
        pagerModel.onViewAttached(mockView)

        verify { pagerState.update(any()) }

        val state = pagerState.changes.first()
        // Sanity check
        assertEquals(PAGER_ID, state.identifier)
        // Verify that the model set page IDs on pagerState
        assertEquals(PAGE_IDS, state.pages)
        // Verify that the correct number of page items is available via the model
        assertEquals(3, pagerModel.items.size)

        // Verify that the model notified the view to scroll to the first page
        verify { mockViewListener.scrollTo(0) }
        // Verify that actions were run for the initial page display
        verify(exactly = 1) { mockActionsRunner.run(any(), any()) }
    }

    @Test
    public fun testUserScrolls(): TestResult = runTest {
        pagerState.changes.test {
            pagerModel.onViewAttached(mockView)
            verify { mockViewListener.scrollTo(0) }

            val initialState = awaitItem()
            assertEquals(0, initialState.pageIndex)
            assertEquals(0, initialState.lastPageIndex)
            assertTrue(initialState.hasNext)
            assertFalse(initialState.hasPrevious)

            // Simulate a user swipe to the first page.
            scrollsFlow.emit(PagerScrollEvent(position = 1, isInternalScroll = false))

            val updatedState = awaitItem()
            assertEquals(1, updatedState.pageIndex)
            assertEquals(0, updatedState.lastPageIndex)
            assertTrue(updatedState.hasNext)
            assertTrue(updatedState.hasPrevious)

            // Verify that we reported an event and ran actions
            verify { mockReporter.report(any(), any()) }
            verify(exactly = 1) { mockActionsRunner.run(any(), any()) }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testInternalScrolls(): TestResult = runTest {
        pagerState.changes.test {
            pagerModel.onViewAttached(mockView)
            verify { mockViewListener.scrollTo(0) }

            val initialState = awaitItem()
            assertEquals(0, initialState.pageIndex)
            assertEquals(0, initialState.lastPageIndex)
            assertTrue(initialState.hasNext)
            assertFalse(initialState.hasPrevious)

            // Simulate an internal scroll to the first page.
            scrollsFlow.emit(PagerScrollEvent(position = 1, isInternalScroll = true))

            val updatedState = awaitItem()
            assertEquals(1, updatedState.pageIndex)
            assertEquals(0, updatedState.lastPageIndex)
            assertTrue(updatedState.hasNext)
            assertTrue(updatedState.hasPrevious)

            // Verify that we didn't report an event, but did run actions.
            verify(exactly = 0) { mockReporter.report(any(), any()) }
            verify(exactly = 1) { mockActionsRunner.run(any(), any()) }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testStateChanges(): TestResult = runTest {
        pagerModel.onViewAttached(mockView)

        verify { mockViewListener.scrollTo(0) }
        // Verify actions were run for the initial page display
        verify(exactly = 1) { mockActionsRunner.run(any(), any()) }

        pagerState.update { it.copyWithPageIndex(1) }
        // Run the pending state update task, so the model can process it.
        testScope.runCurrent()

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
        private val EMPTY_ACTIONS = mapOf<String, JsonValue>()
        private val ITEMS = listOf(
            PagerModel.Item(mockk(relaxed = true), PAGE_1_ID, EMPTY_ACTIONS),
            PagerModel.Item(mockk(relaxed = true), PAGE_2_ID, EMPTY_ACTIONS),
            PagerModel.Item(mockk(relaxed = true), PAGE_3_ID, EMPTY_ACTIONS)
        )
        private val PAGE_IDS = ITEMS.map { it.identifier }
    }
}
