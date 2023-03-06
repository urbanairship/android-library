/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import app.cash.turbine.test
import com.urbanairship.android.layout.environment.FormType
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class CheckboxControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockEnv: ModelEnvironment = mockk {
        every { modelScope } returns testScope
        every { layoutState } returns LayoutState.EMPTY
    }
    private val mockView: AnyModel = mockk(relaxed = true)

    private val formState = spyk(SharedState(
        State.Form(identifier = "form-id", formType = FormType.Form, formResponseType = "form")
    ))

    private lateinit var checkboxState: SharedState<State.Checkbox>

    private lateinit var controller: CheckboxController

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testInit(): TestResult = runTest {
        formState.changes.test {
            // Sanity check initial form state.
            assertTrue(awaitItem().data.isEmpty())

            initCheckboxController()

            val item = awaitItem()
            // Verify that our checkbox controller updated the form state.
            assertTrue(item.data.containsKey(IDENTIFIER))
            // Verify that the response is valid, since the checkbox controller is not required.
            assertTrue(item.inputValidity[IDENTIFIER]!!)

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testRequiredWithMinSelection(): TestResult = runTest {
        formState.changes.test {
            // Sanity check initial form state.
            assertTrue(awaitItem().data.isEmpty())

            initCheckboxController(isRequired = true, minSelection = MIN_SELECTION)

            // Not valid yet, because nothing is selected.
            assertFalse(awaitItem().inputValidity[IDENTIFIER]!!)

            checkboxState.update {
                it.copy(selectedItems = it.selectedItems + SELECTED_VALUE)
            }
            testScheduler.runCurrent()

            // Verify that the response is valid now that it has 1 selection
            assertTrue(awaitItem().inputValidity[IDENTIFIER]!!)

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testControllerInheritsFormEnableState(): TestResult = runTest {
        initCheckboxController()

        checkboxState.changes.test {
            // Verify that the controller is enabled by default.
            assertTrue(awaitItem().isEnabled)

            formState.update {
                it.copy(isEnabled = false)
            }

            // Verify that the controller is disabled when the form is disabled.
            assertFalse(awaitItem().isEnabled)

            ensureAllEventsConsumed()
        }
    }

    private fun initCheckboxController(
        isRequired: Boolean = false,
        minSelection: Int = if (isRequired) 1 else 0,
        maxSelection: Int = Int.MAX_VALUE
    ) {
        checkboxState = spyk(SharedState(
            State.Checkbox(
                identifier = IDENTIFIER,
                minSelection = minSelection,
                maxSelection = maxSelection
            )
        ))

        controller = CheckboxController(
            view = mockView,
            identifier = IDENTIFIER,
            isRequired = isRequired,
            minSelection = minSelection,
            maxSelection = maxSelection,
            formState = formState,
            checkboxState = checkboxState,
            environment = mockEnv
        )

        testScope.runCurrent()
    }

    private companion object {
        private const val IDENTIFIER = "checkbox-controller-id"
        private const val MIN_SELECTION = 1
        private val SELECTED_VALUE = JsonValue.wrap("foo")
    }
}
