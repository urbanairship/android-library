/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.CheckboxEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventType;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.FormData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import test.TestEventListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CheckboxControllerTest {
    private static final String IDENTIFIER = "identifier";
    private static final int MIN_SELECTION = 1;
    private static final int MAX_SELECTION = 2;
    private static final boolean IS_REQUIRED = true;
    private static final String CONTENT_DESCRIPTION = "content description";
    private static final String SELECTED_VALUE = "foo";

    private AutoCloseable mocksClosable;

    private CheckboxController controller;
    private TestEventListener testListener;

    @Mock
    private BaseModel mockView;

    @Before
    public void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);

        controller = new CheckboxController(IDENTIFIER, mockView, MIN_SELECTION, MAX_SELECTION, IS_REQUIRED, CONTENT_DESCRIPTION);
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
        // Verify that we observed a form input init event after the first checkbox is initialized.
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT));

        controller.onEvent(makeViewInitEvent());
        controller.onEvent(makeViewInitEvent());

        // Verify that we didn't emit any further input init events after the first.
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT));

        FormEvent.InputInit initEvent = (FormEvent.InputInit) testListener.getEventAt(0);
        assertEquals(IDENTIFIER, initEvent.getIdentifier());
        assertEquals(ViewType.CHECKBOX_CONTROLLER, initEvent.getViewType());
        assertFalse(initEvent.isValid());

        assertEquals(3, controller.getCheckboxes().size());
        assertTrue(controller.getSelectedValues().isEmpty());
    }

    @Test
    public void testInputChange() {
        controller.onEvent(makeViewInitEvent());
        controller.onEvent(makeViewInitEvent());
        controller.onEvent(makeViewInitEvent());

        // Sanity check
        assertEquals(1, testListener.getCount(EventType.FORM_INPUT_INIT));
        assertEquals(3, controller.getCheckboxes().size());
        assertTrue(controller.getSelectedValues().isEmpty());
        assertTrue(controller.isRequired());
        assertFalse(controller.isValid());

        // Simulate checking an option
        controller.onEvent(new CheckboxEvent.InputChange(SELECTED_VALUE, true));
        assertEquals(1, testListener.getCount(EventType.FORM_DATA_CHANGE));

        FormEvent.DataChange changeEvent = (FormEvent.DataChange) testListener.getEventAt(1);
        FormData.CheckboxController data = (FormData.CheckboxController) changeEvent.getValue();

        // Verify event data
        assertTrue(changeEvent.isValid());
        assertEquals(IDENTIFIER, changeEvent.getIdentifier());
        assertEquals(Collections.singleton(SELECTED_VALUE), data.getValue());
        assertEquals(1, controller.getSelectedValues().size());

        // Simulate unchecking the option
        controller.onEvent(new CheckboxEvent.InputChange(SELECTED_VALUE, false));
        assertEquals(2, testListener.getCount(EventType.FORM_DATA_CHANGE));

        changeEvent = (FormEvent.DataChange) testListener.getEventAt(2);
        data = (FormData.CheckboxController) changeEvent.getValue();

        // Verify updated data
        assertFalse(changeEvent.isValid());
        assertEquals(IDENTIFIER, changeEvent.getIdentifier());
        assertEquals(Collections.emptySet(), data.getValue());

        assertEquals(0, controller.getSelectedValues().size());
        assertFalse(controller.isValid());
    }

    @Test
    public void testMaxSelection() {
        controller.onEvent(makeViewInitEvent());
        controller.onEvent(makeViewInitEvent());
        controller.onEvent(makeViewInitEvent());

        assertEquals(3, controller.getCheckboxes().size());

        controller.onEvent(new CheckboxEvent.InputChange("one", true));
        controller.onEvent(new CheckboxEvent.InputChange("two", true));
        controller.onEvent(new CheckboxEvent.InputChange("three", true));

        // Verify that we didn't accept the third input change
        Set<String> expected = new HashSet<String>() {{
            add("one");
            add("two");
        }};
        assertEquals(expected, controller.getSelectedValues());
    }

    private static Event.ViewInit makeViewInitEvent() {
        CheckboxModel mockCheckboxModel = mock(CheckboxModel.class);
        when(mockCheckboxModel.getType()).thenReturn(ViewType.CHECKBOX);
        return new Event.ViewInit(mockCheckboxModel);
    }
}