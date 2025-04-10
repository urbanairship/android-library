/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.environment.FormType
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.ThomasFormStatus
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.info.RadioInputControllerInfo
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.json.JsonValue
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class RadioInputControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockEnv: ModelEnvironment = mockk {
        every { modelScope } returns testScope
        every { layoutState } returns LayoutState.EMPTY
    }
    private val mockView: AnyModel = mockk(relaxed = true)

    private val formState = spyk(SharedState(
        State.Form(identifier = "form-id", formType = FormType.Form, formResponseType = "form", validationMode = FormValidationMode.IMMEDIATE)
    ))

    private val radioState = spyk(SharedState(State.Radio(identifier = IDENTIFIER)))

    private lateinit var controller: RadioInputController

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
            assertTrue(awaitItem().filteredFields.isEmpty())

            initRadioInputController()

            val item = awaitItem()
            // Verify that our checkbox controller updated the form state.
            assertTrue(item.filteredFields.containsKey(IDENTIFIER))
            // Verify that the response is valid, since the checkbox controller is not required.
            assertTrue(item.lastProcessedStatus(IDENTIFIER)?.isValid == true)

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testRequired(): TestResult = runTest {
        formState.changes.test {
            // Sanity check initial form state.
            assertTrue(awaitItem().filteredFields.isEmpty())

            initRadioInputController(isRequired = true)

            awaitItem().let {
                // Verify that our checkbox controller updated the form state.
                assertTrue(it.filteredFields.containsKey(IDENTIFIER))
                // Not valid yet, since nothing is selected.
                assertTrue(it.lastProcessedStatus(IDENTIFIER)?.isValid == false)
            }
            radioState.update { it.copy(selectedItem = SELECTED_VALUE) }
            testScheduler.runCurrent()

            awaitItem().let {
                // Valid now that we've selected a value
                assertTrue(it.lastProcessedStatus(IDENTIFIER)?.isValid == true)
                // Make sure the controller updated form state with the selected value
                assertEquals(
                    SELECTED_VALUE,
                    it.inputData<ThomasFormField.RadioInputController>(IDENTIFIER)?.originalValue
                )
            }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testControllerInheritsFormEnableState(): TestResult = runTest {
        initRadioInputController()

        radioState.changes.test {
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

    private fun initRadioInputController(
        isRequired: Boolean = false,
        attributeName: AttributeName? = null
    ) {
        controller = RadioInputController(
            view = mockView,
            viewInfo = mockk<RadioInputControllerInfo>(relaxed = true) {
                every { this@mockk.identifier } returns IDENTIFIER
                every { this@mockk.isRequired } returns isRequired
                every { this@mockk.attributeName } returns attributeName

            },
            formState = ThomasForm(formState),
            radioState = radioState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null)
        )
    }

    private companion object {
        private const val IDENTIFIER = "identifier"
        private val SELECTED_VALUE = JsonValue.wrap("foo")
    }
}
