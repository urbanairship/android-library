/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import app.cash.turbine.test
import com.urbanairship.android.layout.environment.ActionsRunner
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.DisplayTimer
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class PagerControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockReporter: Reporter = mockk(relaxUnitFun = true)
    private val mockActionsRunner: ActionsRunner = mockk()
    private val mockDisplayTimer: DisplayTimer = mockk() {
        every { time } returns System.currentTimeMillis()
    }
    private val mockEnv: ModelEnvironment = mockk() {
        every { reporter } returns mockReporter
        every { actionsRunner } returns mockActionsRunner
        every { displayTimer } returns mockDisplayTimer
        every { layoutState } returns LayoutState.EMPTY
        every { modelScope } returns testScope
    }
    private val mockView: AnyModel = mockk(relaxed = true)

    private val pagerState: SharedState<State.Pager> =
        spyk(SharedState(State.Pager(identifier = PAGER_ID)))

    private lateinit var pagerController: PagerController

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)

        pagerController = PagerController(
            view = mockView,
            identifier = PAGER_ID,
            pagerState = pagerState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null)
        )
        testScope.runCurrent()
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testStateChanges(): TestResult = runTest {
        pagerState.changes.test {
            assertEquals(0, awaitItem().pageIndex)

            pagerState.update { it.copyWithPageIndex(1) }

            verify { pagerState.update(any()) }

            assertEquals(1, awaitItem().pageIndex)

            // Verify we reported the page view
            verify { mockReporter.report(any<ReportingEvent.PageView>(), any()) }
        }
    }

    @Test
    public fun testCreateView() {
        val context: Context = mockk(relaxed = true)
        val viewEnv: ViewEnvironment = mockk(relaxed = true)
        pagerController.createView(context, viewEnv)

        verify { mockView.createView(eq(context), eq(viewEnv)) }
    }

    private companion object {
        private const val PAGER_ID = "pager-identifier"
    }
}
