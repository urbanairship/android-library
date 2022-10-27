/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import androidx.core.util.ObjectsCompat
import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.ModelProvider
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.FormEvent
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.event.TextInputEvent
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.ViewType.CONTAINER
import com.urbanairship.android.layout.reporting.LayoutData
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import test.TestEventListener

public class FormControllerTest {

    private lateinit var controller: FormController
    private lateinit var testListener: TestEventListener

    private val mockSubmitButton = mockk<ButtonModel>(relaxed = true)

    private val mockInput = mockk<TextInputModel>(relaxed = true)

    private val mockEnv: ModelEnvironment = spyk(ModelEnvironment(ModelProvider(), emptyMap()))

    private lateinit var mockView: LayoutModel
    private lateinit var inputViewAttachedEvent: Event
    private lateinit var inputFormFieldInitEvent: Event

    @Before
    public fun setUp() {
        mockView = spyk(
            object : LayoutModel(viewType = CONTAINER, environment = mockEnv) {
                override val children: List<BaseModel> = listOf(mockInput, mockSubmitButton)
            }
        )

        every { mockInput.viewType } returns ViewType.TEXT_INPUT
        every { mockSubmitButton.viewType } returns ViewType.LABEL_BUTTON
        every { mockSubmitButton.identifier } returns BUTTON_ID
        every { mockSubmitButton.reportingDescription() } returns BUTTON_DESCRIPTION

        inputViewAttachedEvent = Event.ViewAttachedToWindow(mockInput)
        inputFormFieldInitEvent = TextInputEvent.Init(TEXT_INPUT_ID, false)

        controller = FormController(mockView, FORM_ID, RESPONSE_TYPE, FormBehaviorType.SUBMIT_EVENT, environment = mockEnv)
        testListener = TestEventListener()
        controller.addListener(testListener)
    }

    private fun initForm() {
        controller.onEvent(inputViewAttachedEvent, LayoutData.empty())
        controller.onEvent(inputFormFieldInitEvent, LayoutData.empty())
    }

    @Test
    public fun testViewInit() {
        initForm()

        // Verify that the text input received a form validity update from the form controller.
        verify {
            mockSubmitButton.trickleEvent(
                match { event -> event is FormEvent.ValidationUpdate && !event.isValid },
                any()
            )
        }

        // Verify reporting event was sent for the initial page view and form data was added to the event.
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT).toLong())
        val reportingEvent: ReportingEvent.FormDisplay =
            testListener.getEventAt(EventType.REPORTING_EVENT, 0) as ReportingEvent.FormDisplay
        val layoutData: LayoutData = testListener.getLayoutDataAt(EventType.REPORTING_EVENT, 0)
        assertEquals(ReportingEvent.ReportType.FORM_DISPLAY, reportingEvent.reportType)
        assertEquals(FORM_ID, reportingEvent.formInfo.identifier)
        assertEquals(false, layoutData.formInfo?.formSubmitted)
        assertEquals(RESPONSE_TYPE, reportingEvent.formInfo.formResponseType)
        // Check to make sure no additional events were received.
        assertEquals(1, testListener.getCount().toLong())
    }

    @Test
    public fun testOverrideState() {
        initForm()

        // Bubble a Reporting Event to the form controller.
        controller.onEvent(ReportingEvent.ButtonTap("buttonId"), LayoutData.empty())

        // Verify that the listener was notified and form data was added to the event.
        assertEquals(2, testListener.getCount(EventType.REPORTING_EVENT).toLong())
        val reportingEvent: ReportingEvent.ButtonTap =
            testListener.getEventAt(EventType.REPORTING_EVENT, 1) as ReportingEvent.ButtonTap
        val layoutData: LayoutData = ObjectsCompat.requireNonNull<LayoutData>(
            testListener.getLayoutDataAt(EventType.REPORTING_EVENT, 0)
        )
        assertEquals(ReportingEvent.ReportType.BUTTON_TAP, reportingEvent.reportType)
        assertEquals(FORM_ID, layoutData.formInfo?.identifier)
        assertEquals(RESPONSE_TYPE, layoutData.formInfo?.formResponseType)
        assertEquals(false, layoutData.formInfo?.formSubmitted)
        // Check to make sure no additional events were received.
        assertEquals(2, testListener.getCount().toLong())
    }

    private companion object {
        private const val FORM_ID = "form-identifier"
        private const val RESPONSE_TYPE = "some-response-type"
        private const val TEXT_INPUT_ID = "text-input-identifier"
        private const val BUTTON_ID = "button-identifier"
        private const val BUTTON_DESCRIPTION = "button-description"
    }
}
