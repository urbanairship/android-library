/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.environment.AttributeHandler
import com.urbanairship.android.layout.environment.FormType
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.LayoutEventHandler
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.Reporter
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.environment.ThomasChannelRegistrar
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.FormControllerInfo
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.info.ThomasEmailRegistrationOptions
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.DisplayTimer
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.json.jsonMapOf
import app.cash.turbine.test
import app.cash.turbine.testIn
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
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
public class FormControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockReporter: Reporter = mockk(relaxUnitFun = true)
    private val mockActionsRunner: ThomasActionRunner = mockk()
    private val mockAttributeHandler: AttributeHandler = mockk {
        every { update(any()) } returns Unit
    }
    private val mockChannelRegistrar: ThomasChannelRegistrar = mockk {
        every { register(any()) } returns Unit
    }
    private val mockDisplayTimer: DisplayTimer = mockk {
        every { time } returns System.currentTimeMillis()
    }
    private val testEventHandler = spyk(LayoutEventHandler(testScope))
    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { reporter } returns mockReporter
        every { actionsRunner } returns mockActionsRunner
        every { attributeHandler } returns mockAttributeHandler
        every { displayTimer } returns mockDisplayTimer
        every { layoutState } returns LayoutState.EMPTY
        every { eventHandler } returns testEventHandler
        every { layoutEvents } returns testEventHandler.layoutEvents
        every { modelScope } returns testScope
        every { channelRegistrar } returns mockChannelRegistrar
    }

    private val mockView: AnyModel = mockk(relaxed = true)

    private val parentFormState = spyk(SharedState(State.Form(
        identifier = PARENT_FORM_ID, formType = FormType.Form, formResponseType = "form", validationMode = FormValidationMode.IMMEDIATE)
    ))

    private val childFormState = spyk(SharedState(State.Form(
        identifier = CHILD_FORM_ID, formType = FormType.Form, formResponseType = "form", validationMode = FormValidationMode.IMMEDIATE)
    ))

    private val pagerState = spyk(SharedState(State.Pager(
        identifier = PAGER_ID, pageIds = PAGER_PAGE_IDS)
    ))

    private lateinit var formController: FormController

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testParentFormInit(): TestResult = runTest {
        initParentFormController()

        verify { mockEnv.layoutEvents }
        verify { parentFormState.changes }
    }

    @Test
    public fun testParentFormReportsDisplayWithoutPager(): TestResult = runTest {
        parentFormState.changes.test {
            assertFalse(awaitItem().isDisplayReported)

            initParentFormController()

            parentFormState.update { state ->
                state.copyWithDisplayState(identifier = "input-id", isDisplayed = true)
            }

            assertFalse(awaitItem().isDisplayReported)
            assertFalse(awaitItem().isDisplayReported)
            assertFalse(awaitItem().isDisplayReported)

            assertTrue(awaitItem().isDisplayReported)

            verify { mockReporter.report(any<ReportingEvent.FormDisplay>()) }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testParentFormReportsDisplayWithPager(): TestResult = runTest {
        parentFormState.changes.test {
            assertFalse(awaitItem().isDisplayReported)

            initParentFormController(pagerState = pagerState)

            parentFormState.update { state ->
                state.copyWithDisplayState(identifier = "input-id", isDisplayed = true)
            }

            skipItems(2)

            awaitItem().let { item ->
                assertFalse(item.isDisplayReported)
                assertFalse(item.displayedInputs.isEmpty())
            }

            assertTrue(awaitItem().isDisplayReported)

            verify { mockReporter.report(any<ReportingEvent.FormDisplay>()) }

            ensureAllEventsConsumed()
        }
    }
    @Test
    public fun testParentFormSubmit(): TestResult = runTest {
        parentFormState.changes.test {
            // Init controller and verify initial state
            initParentFormController()
            assertTrue(awaitItem().filteredFields.isEmpty())

            // Update form data
            parentFormState.update {
                it.copyWithFormInput(
                    ThomasFormField.TextInput(
                        textInput = FormInputType.TEXT,
                        identifier = TEXT_INPUT_ID,
                        originalValue = TEXT_INPUT_VALUE,
                        fieldType = ThomasFormField.FieldType.just(
                            value = TEXT_INPUT_VALUE,
                            attributes = ThomasFormField.makeAttributes(
                                name = ATTR_NAME,
                                value = ATTR_VALUE
                            ),
                            channels = listOf(CHANNEL_REGISTRATION)
                        ),
                    )
                )
            }

            // Skip to the final VALID item
            skipItems(4)

            assertFalse(awaitItem().filteredFields.isEmpty())

            // Emit FORM_SUBMIT event
            val event = spyk(LayoutEvent.SubmitForm(BUTTON_ID))
            testEventHandler.broadcast(event)

            skipItems(2)

            // Verify state was updated
            assertTrue(awaitItem().isSubmitted)

            // Verify submit logic was called
            coVerify { event.onSubmitted }
            coVerify { mockAttributeHandler.update(eq(ATTRIBUTES)) }
            coVerify { mockChannelRegistrar.register(eq(listOf(CHANNEL_REGISTRATION))) }

            coVerify { mockReporter.report(any<ReportingEvent.FormResult>()) }

            ensureAllEventsConsumed()
        }
    }


    @Test
    public fun testChildFormInit(): TestResult = runTest {
        initChildFormController()

        verify { childFormState.changes }
    }

    @Test
    public fun testChildFormDoesNotReportDisplay(): TestResult = runTest {
        childFormState.changes.test {
            initChildFormController()

            // Skip to the final VALID item
            skipItems(2)

            assertFalse(awaitItem().isDisplayReported)

            verify(exactly = 0) { mockReporter.report(any<ReportingEvent.FormDisplay>()) }

            ensureAllEventsConsumed()
        }
    }

    @OptIn(DelicateLayoutApi::class)
    @Test
    public fun testChildFormInheritsParentFormEnabledState(): TestResult = runTest {
        val parentChanges = parentFormState.changes.testIn(testScope)
        val childChanges = childFormState.changes.testIn(testScope)

        // Sanity check initial state.
        assertTrue(parentChanges.awaitItem().filteredFields.isEmpty())
        assertTrue(childChanges.awaitItem().filteredFields.isEmpty())
        assertTrue(childFormState.value.isEnabled)

        initChildFormController()
        parentFormState.update { form ->
            form.copy(isEnabled = false)
        }

        // Skip child form init event, displayedInputs change, and the parent form state update above.
        childChanges.skipItems(2)
        parentChanges.skipItems(4)

        // Verify that the child state was updated appropriately.
        childChanges.awaitItem().run {
            assertFalse(isEnabled)
        }

        parentChanges.ensureAllEventsConsumed()
        childChanges.ensureAllEventsConsumed()
    }

    private fun initParentFormController(
        pagerState: SharedState<State.Pager>? = null,
        properties: ModelProperties = DEFAULT_PROPERTIES
    ) {
        formController = FormController(
            viewInfo = mockk<FormControllerInfo>(relaxed = true) {
                every { this@mockk.type } returns ViewType.FORM_CONTROLLER
                every { this@mockk.identifier } returns PARENT_FORM_ID
                every { this@mockk.responseType } returns "form"
                every { this@mockk.submitBehavior } returns FormBehaviorType.SUBMIT_EVENT
            },
            view = mockView,
            formState = ThomasForm(parentFormState),
            parentState = null,
            pagerState = pagerState,
            environment = mockEnv,
            properties = properties
        )
        testScope.runCurrent()
    }

    private fun initChildFormController(
        pagerState: SharedState<State.Pager>? = null,
        properties: ModelProperties = DEFAULT_PROPERTIES
    ) {
        formController = FormController(
            viewInfo = mockk<FormControllerInfo>(relaxed = true) {
                every { this@mockk.type } returns ViewType.FORM_CONTROLLER
                every { this@mockk.identifier } returns CHILD_FORM_ID
                every { this@mockk.responseType } returns "form"
                every { this@mockk.submitBehavior } returns null
            },
            view = mockView,
            formState = ThomasForm(childFormState),
            parentState = ThomasForm(parentFormState),
            pagerState = pagerState,
            environment = mockEnv,
            properties = properties
        )
        testScope.runCurrent()
    }

    private companion object {
        private const val PARENT_FORM_ID = "parent-form-identifier"
        private const val CHILD_FORM_ID = "child-form-identifier"
        private const val TEXT_INPUT_ID = "text-input-identifier"
        private const val TEXT_INPUT_VALUE = "no comment."
        private const val BUTTON_ID = "button-identifier"
        private const val CHANNEL_ID = "channel-identifier"
        private const val PAGER_ID = "pager-identifier"
        private val PAGER_PAGE_IDS = listOf("pg-1", "pg-2", "pg-3")

        private val ATTR_NAME = AttributeName(CHANNEL_ID, null)
        private val ATTR_VALUE = jsonMapOf("foo" to "bar").toJsonValue()
        private val ATTRIBUTES = mapOf(ATTR_NAME to ATTR_VALUE)
        private val CHANNEL_REGISTRATION = ThomasChannelRegistration.Email(
            "some@email.com",
            ThomasEmailRegistrationOptions.DoubleOptIn(null)
        )


        private val DEFAULT_PROPERTIES = ModelProperties(pagerPageId = null)
    }
}
