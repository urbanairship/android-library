/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.LayoutEventHandler
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.info.LabelButtonInfo
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.view.LabelButtonView
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ButtonModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockReporter: Reporter = mockk(relaxed = true)
    private val mockActionsRunner: ThomasActionRunner = mockk(relaxed = true)
    private val layoutEventHandler = spyk(LayoutEventHandler(testScope))
    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { reporter } returns mockReporter
        every { actionsRunner } returns mockActionsRunner
        every { modelScope } returns testScope
        every { eventHandler } returns layoutEventHandler
    }

    private val mockView: LabelButtonView = mockk(relaxed = true)
    private val taps = MutableStateFlow(Unit)

    private lateinit var buttonModel: LabelButtonModel

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)

        clearMocks(layoutEventHandler, answers = false, recordedCalls = true, verificationMarks = true)

        mockkStatic(Airship::class)
        every { Airship.platform } returns Platform.ANDROID
        every { Airship.application } returns mockk()

        buttonModel = makeButton()
        every { mockView.taps() } returns taps
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()

        unmockkStatic(Airship::class)
    }

    @Test
    public fun testMergedActionsCalled(): TestResult = runTest {
        buttonModel = makeButton(actions = mapOf(
            "foo" to JsonValue.wrap("bar"),
            "shouldnt_change" to JsonValue.wrap("value"),
            "platform_action_overrides" to jsonMapOf(
                "android" to jsonMapOf(
                    "foo" to "bar2",
                    "added" to "value"
                ).toJsonValue()
            ).toJsonValue()
        ))

        every { mockActionsRunner.run(any(), any()) } answers {
            val expected = mapOf(
                "foo" to JsonValue.wrap("bar2"),
                "shouldnt_change" to JsonValue.wrap("value"),
                "added" to JsonValue.wrap("value")
            )

            assertEquals(expected, firstArg())
        }

        buttonModel.onViewAttached(mockView)
        advanceUntilIdle()

        taps.emit(Unit)
        advanceUntilIdle()

        verify { mockActionsRunner.run(any(), any()) }
    }

    @Test
    public fun asyncViewRetry_tapBroadcastsAsyncViewReloadWithButtonIdentifier(): TestResult = runTest(testDispatcher) {
        val buttonId = "async-retry-button"
        buttonModel = makeButton(
            clickBehaviors = listOf(ButtonClickBehaviorType.ASYNC_VIEW_RETRY),
            identifier = buttonId
        )

        buttonModel.onViewAttached(mockView)
        advanceUntilIdle()

        taps.emit(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            layoutEventHandler.broadcast(
                match { it is LayoutEvent.AsyncViewReload && (it as LayoutEvent.AsyncViewReload).identifier == buttonId }
            )
        }
    }

    @Test
    public fun dismissOnly_tapDoesNotBroadcastAsyncViewReload(): TestResult = runTest(testDispatcher) {
        buttonModel = makeButton(clickBehaviors = listOf(ButtonClickBehaviorType.DISMISS))

        buttonModel.onViewAttached(mockView)
        advanceUntilIdle()

        taps.emit(Unit)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            layoutEventHandler.broadcast(match { it is LayoutEvent.AsyncViewReload })
        }
    }

    @Test
    public fun pagerNextThenAsyncViewRetry_tapBroadcastsInOrder(): TestResult = runTest(testDispatcher) {
        val buttonId = "combo-button"
        buttonModel = makeButton(
            clickBehaviors = listOf(
                ButtonClickBehaviorType.PAGER_NEXT,
                ButtonClickBehaviorType.ASYNC_VIEW_RETRY
            ),
            identifier = buttonId
        )

        buttonModel.onViewAttached(mockView)
        advanceUntilIdle()

        taps.emit(Unit)
        advanceUntilIdle()

        coVerifyOrder {
            layoutEventHandler.broadcast(match { it is LayoutEvent.PagerNext })
            layoutEventHandler.broadcast(
                match { it is LayoutEvent.AsyncViewReload && (it as LayoutEvent.AsyncViewReload).identifier == buttonId }
            )
        }
    }

    private fun makeButton(
        actions: Map<String, JsonValue>? = null,
        clickBehaviors: List<ButtonClickBehaviorType> = listOf(ButtonClickBehaviorType.DISMISS),
        identifier: String = "test-button"
    ): LabelButtonModel {
        val mockInfo = mockk<LabelButtonInfo>(relaxed = true) {
            every { this@mockk.actions } returns actions
            every { this@mockk.clickBehaviors } returns clickBehaviors
            every { this@mockk.identifier } returns identifier
        }

        val mockLabel = mockk<LabelModel>(relaxed = true)

        return LabelButtonModel(
            viewInfo = mockInfo,
            label = mockLabel,
            formState = null,
            pagerState = null,
            environment = mockEnv,
            properties = ModelProperties(null),
        )
    }
}
