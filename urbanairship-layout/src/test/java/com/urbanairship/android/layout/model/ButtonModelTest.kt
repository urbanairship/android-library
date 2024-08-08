/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model

import com.urbanairship.UAirship
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.TextAlignment
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.android.layout.view.ImageButtonView
import com.urbanairship.android.layout.view.LabelButtonView
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ButtonModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockReporter: Reporter = mockk(relaxed = true)
    private val mockActionsRunner: ThomasActionRunner = mockk(relaxed = true)
    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { reporter } returns mockReporter
        every { actionsRunner } returns mockActionsRunner
        every { modelScope } returns testScope
    }

    private val mockView: LabelButtonView = mockk(relaxed = true)
    private val taps = MutableStateFlow(Unit)

    private lateinit var buttonModel: LabelButtonModel

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)

        buttonModel = makeButton()
        every { mockView.taps() } returns taps

        testScope.runCurrent()
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

        buttonModel.onViewAttached(mockView)

        every { mockActionsRunner.run(any(), any()) } answers {
            val expected = mapOf(
                "foo" to JsonValue.wrap("bar2"),
                "shouldnt_change" to JsonValue.wrap("value"),
                "added" to JsonValue.wrap("value")
            )

            assertEquals(expected, firstArg())
        }

        testScope.runTest {
            taps.emit(Unit)
            verify { mockActionsRunner.run(any(), any()) }
        }
    }

    private fun makeButton(actions: Map<String, JsonValue>? = null): LabelButtonModel {
        return LabelButtonModel(
            identifier = "test_button_id",
            label = LabelModel(
                text = "test",
                textAppearance = TextAppearance(Color(Color.WHITE, emptyList()), 14, TextAlignment.START, emptyList(), emptyList()),
                environment = mockEnv,
                properties = ModelProperties(null)
            ),
            actions = actions,
            clickBehaviors = listOf(ButtonClickBehaviorType.DISMISS),
            environment = mockEnv,
            formState = null,
            pagerState = null,
            properties = ModelProperties(null),
            platformProvider = { UAirship.ANDROID_PLATFORM }
        )
    }
}
