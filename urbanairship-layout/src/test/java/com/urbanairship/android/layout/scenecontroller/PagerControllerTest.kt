package com.urbanairship.android.layout.scenecontroller

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.environment.SharedState
import app.cash.turbine.test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.urbanairship.android.layout.environment.State as CoreState

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class PagerControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sharedPagerState: SharedState<CoreState.Pager>

    @Before
    public fun setUp() {
        sharedPagerState = SharedState(
            initialValue = CoreState.Pager(
                identifier = "mockPager",
                pageIndex = 0,
                lastPageIndex = 0,
                completed = false,
                pageIds = listOf("page1", "page2", "page3"),
            )
        )

        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun `initial state when pagerState is null`(): TestResult = runTest(testDispatcher) {
        val controller = PagerController(pagerState = null, dispatcher = testDispatcher)
        val initialState = controller.state.value

        assertFalse(initialState.canGoBack)
        assertFalse(initialState.canGoNext)
    }

    @Test
    public fun `initial state reflects pagerState - hasPrevious false, hasNext true`(): TestResult = runTest(testDispatcher) {
        // Initial state: page 0 of 3
        sharedPagerState.update {
            it.copy(pageIndex = 0)
        }

        val controller = PagerController(pagerState = sharedPagerState, dispatcher = testDispatcher)
        val initialState = controller.state.value

        assertFalse("Initial state should not allow going back", initialState.canGoBack)
        assertTrue("Initial state should allow going forward", initialState.canGoNext)
    }

    @Test
    public fun `initial state reflects pagerState - hasPrevious true, hasNext true`(): TestResult = runTest(testDispatcher) {
        // Initial state: page 1 of 3 (index 1, count 3)
        sharedPagerState.update {
            it.copy(pageIndex = 1)
        }
        val controller = PagerController(pagerState = sharedPagerState, dispatcher = testDispatcher)
        val initialState = controller.state.value

        assertTrue("Initial state should allow going back", initialState.canGoBack)
        assertTrue("Initial state should allow going forward", initialState.canGoNext)
    }

    @Test
    public fun `initial state reflects pagerState - hasPrevious true, hasNext false`(): TestResult = runTest(testDispatcher) {
        // Initial state: page 2 of 3 (index 2, count 3)
        sharedPagerState.update { it.copy(pageIndex = 2) }
        val controller = PagerController(pagerState = sharedPagerState, dispatcher = testDispatcher)
        val initialState = controller.state.value

        assertTrue("Initial state should allow going back", initialState.canGoBack)
        assertFalse("Initial state should not allow going forward", initialState.canGoNext)
    }

    @Test
    public fun `initial state reflects pagerState - single page`(): TestResult = runTest(testDispatcher) {
        // Initial state: page 0 of 1 (index 0, count 1)
        sharedPagerState.update { it.copy(pageIndex = 0, pageIds = listOf("page1")) }
        val controller = PagerController(pagerState = sharedPagerState, dispatcher = testDispatcher)
        val initialState = controller.state.value

        assertFalse("Initial state should not allow going back for single page", initialState.canGoBack)
        assertFalse("Initial state should not allow going forward for single page", initialState.canGoNext)
    }

    @Test
    public fun `controller state updates when pagerState changes`(): TestResult = runTest(testDispatcher) {
        val controller = PagerController(pagerState = sharedPagerState, dispatcher = testDispatcher)

        controller.state.test {
            var state = awaitItem()
            assertFalse(state.canGoBack)
            assertTrue(state.canGoNext)

            // Change pagerState: page 1 of 3
            sharedPagerState.update { it.copy(pageIndex = 1) }
            state = awaitItem()
            assertTrue(state.canGoBack)
            assertTrue(state.canGoNext)

            // Change pagerState: page 2 of 3
            sharedPagerState.update { it.copy(pageIndex = 2) }
            state = awaitItem()
            assertTrue(state.canGoBack)
            assertFalse(state.canGoNext)
        }
    }

    @Test
    public fun `navigate NEXT returns false if pagerState is null`(): TestResult = runTest(testDispatcher) {
        val controller = PagerController(pagerState = null, dispatcher = testDispatcher)
        val result = controller.navigate(PagerController.NavigationRequest.NEXT)
        assertFalse(result)
    }

    @Test
    public fun `navigate BACK returns false if pagerState is null`(): TestResult = runTest(testDispatcher) {
        val controller = PagerController(pagerState = null, dispatcher = testDispatcher)
        val result = controller.navigate(PagerController.NavigationRequest.BACK)
        assertFalse(result)
    }

    @Test
    public fun `navigate NEXT success when can go forward`(): TestResult = runTest(testDispatcher) {
        val controller = PagerController(pagerState = sharedPagerState, dispatcher = testDispatcher)

        val state = controller.state.value
        assertTrue(state.canGoNext)
        assertFalse(state.canGoBack)
        assertEquals(sharedPagerState.changes.value.pageIndex, 0)

        sharedPagerState.changes.test {
            skipItems(1)

            val result = controller.navigate(PagerController.NavigationRequest.NEXT)
            assertTrue("Navigation should succeed", result)

            val state = awaitItem()
            assertEquals(state.pageIndex, 1)
        }
    }

    @Test
    public fun `navigate NEXT failure when cannot go forward`(): TestResult = runTest(testDispatcher) {
        sharedPagerState.update { it.copy(pageIndex = it.pageIds.size - 1, progress = 1) }
        val controller = PagerController(pagerState = sharedPagerState, dispatcher = testDispatcher)

        sharedPagerState.changes.test {
            var state = awaitItem()
            assertEquals(state.pageIndex, 2)

            val result = controller.navigate(PagerController.NavigationRequest.NEXT)
            assertFalse("Navigation should fail", result)

            state = awaitItem()
            assertEquals(state.pageIndex, 2)
        }
    }

    @Test
    public fun `navigate BACK success when can go backward`(): TestResult = runTest(testDispatcher) {
        sharedPagerState.update { it.copy(pageIndex = 2, progress = 1) }
        val controller = PagerController(pagerState = sharedPagerState, dispatcher = testDispatcher)

        sharedPagerState.changes.test {
            var state = awaitItem()
            assertEquals(state.pageIndex, 2)

            val result = controller.navigate(PagerController.NavigationRequest.BACK)
            assertTrue("Navigation should succeed", result)

            state = awaitItem()
            assertEquals(state.pageIndex, 1)
        }
    }

    @Test
    public fun `navigate BACK failure when cannot go backward`(): TestResult = runTest(testDispatcher) {
        sharedPagerState.update { it.copy(pageIndex = 0, progress = 1) }
        val controller = PagerController(pagerState = sharedPagerState, dispatcher = testDispatcher)

        sharedPagerState.changes.test {
            val state = awaitItem()
            assertEquals(state.pageIndex, 0)

            val result = controller.navigate(PagerController.NavigationRequest.BACK)
            assertFalse("Navigation should fail", result)

            expectNoEvents()
        }
    }
}
