/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventType;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.RadioEvent;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.FormData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import test.TestEventListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadioInputControllerTest {
    private static final String IDENTIFIER = "identifier";
    private static final boolean IS_REQUIRED = true;
    private static final String CONTENT_DESCRIPTION = "content description";
    private static final String SELECTED_VALUE = "foo";
    private static final String SELECTED_VALUE_2 = "bar";

    private AutoCloseable mocksClosable;

    private RadioInputController controller;
    private TestEventListener testListener;

    @Mock
    private BaseModel mockView;

    @Before
    public void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);

        controller = new RadioInputController(IDENTIFIER, mockView, IS_REQUIRED, CONTENT_DESCRIPTION);
        testListener = new TestEventListener();
        controller.addListener(testListener);
    }

    @After
    public void tearDown() throws Exception {
        mocksClosable.close();
    }

    @Test
    public void testViewInit() {
        controller.onEvent(makeViewInitEvent());
        // Verify that we observed a form input init event after the first radio input is initialized.
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT));

        controller.onEvent(makeViewInitEvent());
        controller.onEvent(makeViewInitEvent());

        // Verify that we didn't emit any further input init events after the first.
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT));

        FormEvent.InputInit initEvent = (FormEvent.InputInit) testListener.getEventAt(0);
        assertEquals(IDENTIFIER, initEvent.getIdentifier());
        assertEquals(ViewType.RADIO_INPUT_CONTROLLER, initEvent.getViewType());
        assertFalse(initEvent.isValid());

        assertEquals(3, controller.getRadioInputs().size());
        assertNull(controller.getSelectedValue());
    }

    @Test
    public void testInputChange() {
        controller.onEvent(makeViewInitEvent());
        controller.onEvent(makeViewInitEvent());
        controller.onEvent(makeViewInitEvent());

        // Sanity check
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT));
        assertEquals(3, controller.getRadioInputs().size());
        assertNull(controller.getSelectedValue());
        assertTrue(controller.isRequired());
        assertFalse(controller.isValid());

        // Simulate checking an option
        controller.onEvent(new RadioEvent.InputChange(SELECTED_VALUE, true));
        assertEquals(1, testListener.getCount(EventType.FORM_DATA_CHANGE));

        FormEvent.DataChange changeEvent = (FormEvent.DataChange) testListener.getEventAt(1);
        FormData.RadioInputController data = (FormData.RadioInputController) changeEvent.getValue();

        // Verify event data
        assertTrue(changeEvent.isValid());
        assertEquals(IDENTIFIER, changeEvent.getIdentifier());
        assertEquals(SELECTED_VALUE, data.getValue());

        // Simulate another click on the selected radio input
        controller.onEvent(new RadioEvent.InputChange(SELECTED_VALUE, true));

        // Verify that the controller didn't broadcast a new data change event
        assertEquals(1, testListener.getCount(EventType.FORM_DATA_CHANGE));

        // Simulate a click on a different radio input
        controller.onEvent(new RadioEvent.InputChange(SELECTED_VALUE_2, true));

        // Verify that the controller did broadcast a data change this time
        assertEquals(2, testListener.getCount(EventType.FORM_DATA_CHANGE));

        changeEvent = (FormEvent.DataChange) testListener.getEventAt(2);
        data = (FormData.RadioInputController) changeEvent.getValue();

        // Verify the data change contains the new value
        assertTrue(changeEvent.isValid());
        assertEquals(IDENTIFIER, changeEvent.getIdentifier());
        assertEquals(SELECTED_VALUE_2, data.getValue());
    }

    private static Event.ViewInit makeViewInitEvent() {
        RadioInputModel mockRadioInputModel = mock(RadioInputModel.class);
        when(mockRadioInputModel.getType()).thenReturn(ViewType.RADIO_INPUT);
        return new Event.ViewInit(mockRadioInputModel);
    }
}
