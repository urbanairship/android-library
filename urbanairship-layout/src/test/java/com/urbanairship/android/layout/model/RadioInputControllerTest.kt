/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.ModelProvider
import com.urbanairship.android.layout.event.Event.ViewInit
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.FormEvent.InputInit
import com.urbanairship.android.layout.event.RadioEvent
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import test.TestEventListener

public class RadioInputControllerTest {

    private lateinit var controller: RadioInputController
    private lateinit var testListener: TestEventListener

    private val mockView = mockk<BaseModel>(relaxed = true)
    private val mockEnv = spyk(ModelEnvironment(ModelProvider(), emptyMap()))

    @Before
    public fun setUp() {
        controller = RadioInputController(
            view = mockView,
            identifier = IDENTIFIER,
            isRequired = IS_REQUIRED,
            attributeName = ATTRIBUTE_NAME,
            contentDescription = CONTENT_DESCRIPTION,
            environment = mockEnv
        )
        testListener = TestEventListener()
        controller.addListener(testListener)
    }

    @Test
    public fun testViewInit() {
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        // Verify that we observed a form input init event after the first radio input is initialized.
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT).toLong())
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())

        // Verify that we didn't emit any further input init events after the first.
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT).toLong())
        val initEvent = testListener.getEventAt(0) as InputInit
        assertEquals(IDENTIFIER, initEvent.identifier)
        assertEquals(ViewType.RADIO_INPUT_CONTROLLER, initEvent.viewType)
        assertFalse(initEvent.isValid)
        assertEquals(3, controller.radioInputs.size.toLong())
        assertNull(controller.selectedValue)
    }

    @Test
    public fun testInputChange() {
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())

        // Sanity check
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT).toLong())
        assertEquals(3, controller.radioInputs.size.toLong())
        assertNull(controller.selectedValue)
        assertTrue(controller.isRequired)
        assertFalse(controller.isValid)

        // Simulate checking an option
        controller.onEvent(
            RadioEvent.InputChange(SELECTED_VALUE, ATTRIBUTE_VALUE, true),
            LayoutData.empty()
        )
        assertEquals(1, testListener.getCount(EventType.FORM_DATA_CHANGE).toLong())
        var changeEvent = testListener.getEventAt(1) as DataChange
        var data = changeEvent.value as FormData.RadioInputController

        // Verify event data
        assertTrue(changeEvent.isValid)
        assertEquals(IDENTIFIER, changeEvent.value.identifier)
        assertEquals(SELECTED_VALUE, data.value)

        // Simulate another click on the selected radio input
        controller.onEvent(
            RadioEvent.InputChange(SELECTED_VALUE, ATTRIBUTE_VALUE, true),
            LayoutData.empty()
        )

        // Verify that the controller didn't broadcast a new data change event
        assertEquals(1, testListener.getCount(EventType.FORM_DATA_CHANGE).toLong())

        // Simulate a click on a different radio input
        controller.onEvent(
            RadioEvent.InputChange(SELECTED_VALUE_2, ATTRIBUTE_VALUE, true),
            LayoutData.empty()
        )

        // Verify that the controller did broadcast a data change this time
        assertEquals(2, testListener.getCount(EventType.FORM_DATA_CHANGE).toLong())
        changeEvent = testListener.getEventAt(2) as DataChange
        data = changeEvent.value as FormData.RadioInputController
        val attributes = changeEvent.attributes
        assertEquals(1, attributes.size.toLong())
        assertEquals(ATTRIBUTE_VALUE, attributes[ATTRIBUTE_NAME])

        // Verify the data change contains the new value
        assertTrue(changeEvent.isValid)
        assertEquals(IDENTIFIER, changeEvent.value.identifier)
        assertEquals(SELECTED_VALUE_2, data.value)
    }

    private companion object {
        private const val IDENTIFIER = "identifier"
        private const val IS_REQUIRED = true
        private const val CONTENT_DESCRIPTION = "content description"
        private val SELECTED_VALUE = JsonValue.wrap("foo")
        private val SELECTED_VALUE_2 = JsonValue.wrap("bar")
        private val ATTRIBUTE_VALUE = JsonValue.wrap("some attribute value")
        private val ATTRIBUTE_NAME = AttributeName("some-channel", null)
        private fun makeViewInitEvent(): ViewInit {
            val mockRadioInputModel = mockk<RadioInputModel>(relaxed = true)
            every { mockRadioInputModel.viewType } returns ViewType.RADIO_INPUT
            return ViewInit(mockRadioInputModel)
        }
    }
}
