/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ModelEnvironment
import com.urbanairship.android.layout.ModelProvider
import com.urbanairship.android.layout.event.CheckboxEvent
import com.urbanairship.android.layout.event.Event.ViewInit
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.FormEvent.DataChange
import com.urbanairship.android.layout.event.FormEvent.InputInit
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import test.TestEventListener

public class CheckboxControllerTest {

    private lateinit var controller: CheckboxController
    private lateinit var testListener: TestEventListener

    private val mockView = mockk<BaseModel>(relaxed = true)

    private val mockEnv = spyk(ModelEnvironment(ModelProvider(), emptyMap()))

    @Before
    public fun setUp() {
        controller = CheckboxController(
            mockView,
            IDENTIFIER,
            IS_REQUIRED,
            MIN_SELECTION,
            MAX_SELECTION,
            CONTENT_DESCRIPTION,
            environment = mockEnv
        )
        testListener = TestEventListener()
        controller.addListener(testListener)
    }

    @Test
    public fun testViewInit() {
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        // Verify that we observed a form input init event after the first checkbox is initialized.
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT).toLong())
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())

        // Verify that we didn't emit any further input init events after the first.
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT).toLong())
        val initEvent = testListener.getEventAt(0) as InputInit
        assertEquals(IDENTIFIER, initEvent.identifier)
        assertEquals(ViewType.CHECKBOX_CONTROLLER, initEvent.viewType)
        assertFalse(initEvent.isValid)
        assertEquals(3, controller.getCheckboxes().size.toLong())
        assertTrue(controller.getSelectedValues().isEmpty())
    }

    @Test
    public fun testInputChange() {
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())

        // Sanity check
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT).toLong())
        assertEquals(3, controller.getCheckboxes().size.toLong())
        assertTrue(controller.getSelectedValues().isEmpty())
        assertTrue(controller.isRequired)
        assertFalse(controller.isValid)

        // Simulate checking an option
        controller.onEvent(CheckboxEvent.InputChange(SELECTED_VALUE, true), LayoutData.empty())
        assertEquals(1, testListener.getCount(EventType.FORM_DATA_CHANGE).toLong())
        var changeEvent = testListener.getEventAt(1) as DataChange
        var data = changeEvent.value as FormData.CheckboxController

        // Verify event data
        assertTrue(changeEvent.isValid)
        assertEquals(IDENTIFIER, changeEvent.value.identifier)
        assertEquals(setOf(SELECTED_VALUE), data.value)
        assertEquals(1, controller.getSelectedValues().size.toLong())

        // Simulate unchecking the option
        controller.onEvent(CheckboxEvent.InputChange(SELECTED_VALUE, false), LayoutData.empty())
        assertEquals(2, testListener.getCount(EventType.FORM_DATA_CHANGE).toLong())
        changeEvent = testListener.getEventAt(2) as DataChange
        data = changeEvent.value as FormData.CheckboxController

        // Verify updated data
        assertFalse(changeEvent.isValid)
        assertEquals(IDENTIFIER, changeEvent.value.identifier)
        assertEquals(emptySet<Any>(), data.value)
        assertEquals(0, controller.getSelectedValues().size.toLong())
        assertFalse(controller.isValid)
    }

    @Test
    public fun testMaxSelection() {
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        controller.onEvent(makeViewInitEvent(), LayoutData.empty())
        assertEquals(3, controller.getCheckboxes().size.toLong())
        controller.onEvent(
            CheckboxEvent.InputChange(JsonValue.wrap("one"), true),
            LayoutData.empty()
        )
        controller.onEvent(
            CheckboxEvent.InputChange(JsonValue.wrap("two"), true),
            LayoutData.empty()
        )
        controller.onEvent(
            CheckboxEvent.InputChange(JsonValue.wrap("three"), true),
            LayoutData.empty()
        )

        // Verify that we didn't accept the third input change
        val expected: Set<JsonValue> = setOf(JsonValue.wrap("one"), JsonValue.wrap("two"))

        assertEquals(expected, controller.getSelectedValues())
    }

    private companion object {
        private const val IDENTIFIER = "identifier"
        private const val MIN_SELECTION = 1
        private const val MAX_SELECTION = 2
        private const val IS_REQUIRED = true
        private const val CONTENT_DESCRIPTION = "content description"
        private val SELECTED_VALUE = JsonValue.wrap("foo")
        private fun makeViewInitEvent(): ViewInit {
            val mockCheckboxModel = mockk<CheckboxModel>()
            every { mockCheckboxModel.viewType } returns ViewType.CHECKBOX
            return ViewInit(mockCheckboxModel)
        }
    }
}
