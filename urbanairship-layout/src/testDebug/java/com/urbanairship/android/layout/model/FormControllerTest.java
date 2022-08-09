/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventType;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.event.TextInputEvent;
import com.urbanairship.android.layout.property.FormBehaviorType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.LayoutData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import test.TestEventListener;

import static androidx.core.util.ObjectsCompat.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

public class FormControllerTest {
    private static final String FORM_ID = "form-identifier";
    private static final String RESPONSE_TYPE = "some-response-type";
    private static final String TEXT_INPUT_ID = "text-input-identifier";
    private static final String BUTTON_ID = "button-identifier";
    private static final String BUTTON_DESCRIPTION = "button-description";

    private AutoCloseable mocksClosable;

    private FormController controller;
    private TestEventListener testListener;

    @Mock
    private ButtonModel mockSubmitButton;

    @Mock
    private TextInputModel mockInput;

    @Spy
    private LayoutModel mockView = new LayoutModel(ViewType.CONTAINER, null, null) {
        @Override
        public List<BaseModel> getChildren() {
            return Arrays.asList(mockInput, mockSubmitButton);
        }
    };

    private Event inputViewAttachedEvent;
    private Event inputFormFieldInitEvent;

    @Before
    public void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);

        when(mockSubmitButton.getType()).thenReturn(ViewType.LABEL_BUTTON);
        when(mockInput.getType()).thenReturn(ViewType.TEXT_INPUT);

        when(mockSubmitButton.getIdentifier()).thenReturn(BUTTON_ID);
        when(mockSubmitButton.reportingDescription()).thenReturn(BUTTON_DESCRIPTION);

        inputViewAttachedEvent = new Event.ViewAttachedToWindow(mockInput);
        inputFormFieldInitEvent = new TextInputEvent.Init(TEXT_INPUT_ID, false);

        controller = new FormController(FORM_ID, RESPONSE_TYPE, mockView, FormBehaviorType.SUBMIT_EVENT);
        testListener = new TestEventListener();
        controller.addListener(testListener);
    }

    private void initForm() {
        controller.onEvent(inputViewAttachedEvent, LayoutData.empty());
        controller.onEvent(inputFormFieldInitEvent, LayoutData.empty());
    }

    @After
    public void tearDown() throws Exception {
        mocksClosable.close();
    }

    @Test
    public void testViewInit() {
        initForm();

        // Verify that the text input received a form validity update from the form controller.
        Mockito.verify(mockSubmitButton).trickleEvent(argThat(event ->
            event instanceof FormEvent.ValidationUpdate
                && !((FormEvent.ValidationUpdate) event).isValid()), any());

        // Verify reporting event was sent for the initial page view and form data was added to the event.
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT));
        ReportingEvent.FormDisplay reportingEvent = (ReportingEvent.FormDisplay) testListener.getEventAt(EventType.REPORTING_EVENT, 0);
        LayoutData layoutData =  testListener.getLayoutDataAt(EventType.REPORTING_EVENT, 0);


        assertEquals(ReportingEvent.ReportType.FORM_DISPLAY, reportingEvent.getReportType());
        assertEquals(FORM_ID, reportingEvent.getFormInfo().getIdentifier());
        assertEquals(false, layoutData.getFormInfo().getFormSubmitted());
        assertEquals(RESPONSE_TYPE, reportingEvent.getFormInfo().getFormResponseType());
        // Check to make sure no additional events were received.
        assertEquals(1, testListener.getCount());
    }

    @Test
    public void testOverrideState() {
        initForm();

        // Bubble a Reporting Event to the form controller.
        controller.onEvent(new ReportingEvent.ButtonTap("buttonId"), LayoutData.empty());

        // Verify that the listener was notified and form data was added to the event.
        assertEquals(2, testListener.getCount(EventType.REPORTING_EVENT));

        ReportingEvent.ButtonTap reportingEvent = (ReportingEvent.ButtonTap) testListener.getEventAt(EventType.REPORTING_EVENT, 1);
        LayoutData layoutData = requireNonNull(testListener.getLayoutDataAt(EventType.REPORTING_EVENT, 0));

        assertEquals(ReportingEvent.ReportType.BUTTON_TAP, reportingEvent.getReportType());
        assertEquals(FORM_ID, layoutData.getFormInfo().getIdentifier());
        assertEquals(RESPONSE_TYPE, layoutData.getFormInfo().getFormResponseType());
        assertEquals(false, layoutData.getFormInfo().getFormSubmitted());
        // Check to make sure no additional events were received.
        assertEquals(2, testListener.getCount());
    }

}
