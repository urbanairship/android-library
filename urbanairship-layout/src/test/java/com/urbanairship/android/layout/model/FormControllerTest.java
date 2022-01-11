/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventType;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.event.TextInputEvent;
import com.urbanairship.android.layout.event.WebViewEvent;
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

import test.TestEventListener;

import static androidx.core.util.ObjectsCompat.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

public class FormControllerTest {
    private static final String FORM_ID = "form-identifier";
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

        controller = new FormController(FORM_ID, mockView, FormBehaviorType.SUBMIT_EVENT);
        testListener = new TestEventListener();
        controller.addListener(testListener);
    }

    private void initForm() {
        controller.onEvent(inputViewAttachedEvent);
        controller.onEvent(inputFormFieldInitEvent);
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
                && !((FormEvent.ValidationUpdate) event).isValid()));

        // Verify reporting event was sent for the initial page view and form data was added to the event.
        assertEquals(1, testListener.getCount(EventType.REPORTING_EVENT));
        ReportingEvent.FormDisplay reportingEvent = (ReportingEvent.FormDisplay) testListener.getEventAt(EventType.REPORTING_EVENT, 0);
        assertEquals(ReportingEvent.ReportType.FORM_DISPLAY, reportingEvent.getReportType());
        assertEquals(FORM_ID, reportingEvent.getState().getFormId());
        assertEquals(false, reportingEvent.getState().getFormSubmitted());
        // Check to make sure no additional events were received.
        assertEquals(1, testListener.getCount());
    }

    @Test
    public void testOverridesStateForReportingEvents() {
        initForm();

        // Bubble a Reporting Event to the form controller.
        controller.onEvent(new ReportingEvent.ButtonTap("buttonId"));

        // Verify that the listener was notified and form data was added to the event.
        assertEquals(2, testListener.getCount(EventType.REPORTING_EVENT));
        ReportingEvent.ButtonTap reportingEvent = (ReportingEvent.ButtonTap) testListener.getEventAt(EventType.REPORTING_EVENT, 1);
        assertEquals(ReportingEvent.ReportType.BUTTON_TAP, reportingEvent.getReportType());
        LayoutData state = reportingEvent.getState();
        assertEquals(FORM_ID, state.getFormId());
        assertEquals(false, state.getFormSubmitted());
        // Check to make sure no additional events were received.
        assertEquals(2, testListener.getCount());
    }

    @Test
    public void testOverridesStateForWebViewEvents() {
        initForm();

        // Simulate a webview close from within the form.
        controller.onEvent(new WebViewEvent.Close());

        // Verify that the listener was notified and form data was added to the event.
        assertEquals(1, testListener.getCount(EventType.WEBVIEW_CLOSE));
        WebViewEvent.Close closeEvent = (WebViewEvent.Close) testListener.getEventAt(EventType.WEBVIEW_CLOSE, 0);
        LayoutData state = requireNonNull(closeEvent.getState());
        assertEquals(FORM_ID, state.getFormId());
        assertEquals(false, state.getFormSubmitted());
        // Check to make sure no additional events were received.
        assertEquals(2, testListener.getCount());
    }

    @Test
    public void testOverridesStateForButtonCancel() {
        initForm();

        // Simulate a cancel event from a button within the pager.
        controller.onEvent(new ButtonEvent.Cancel(mockSubmitButton));

        // Verify that the listener was notified and pager data was added to the event.
        assertEquals(1, testListener.getCount(EventType.BUTTON_BEHAVIOR_CANCEL));
        ButtonEvent cancelEvent = (ButtonEvent) testListener.getEventAt(EventType.BUTTON_BEHAVIOR_CANCEL, 0);
        LayoutData state = requireNonNull(cancelEvent.getState());
        assertEquals(FORM_ID, state.getFormId());
        assertEquals(false, state.getFormSubmitted());
        // Check to make sure no additional events were received (aside from the form display event).
        assertEquals(2, testListener.getCount());
    }

    @Test
    public void testOverridesStateForButtonDismiss() {
        initForm();

        // Simulate a dismiss event from a button
        controller.onEvent(new ButtonEvent.Dismiss(mockSubmitButton));

        // Verify that the listener was notified and updated pager data was added to the event
        assertEquals(1, testListener.getCount(EventType.BUTTON_BEHAVIOR_DISMISS));
        ButtonEvent dismissEvent = (ButtonEvent) testListener.getEventAt(EventType.BUTTON_BEHAVIOR_DISMISS, 0);
        LayoutData state = requireNonNull(dismissEvent.getState());
        assertEquals(FORM_ID, state.getFormId());
        assertEquals(false, state.getFormSubmitted());
        // Check to make sure no additional events were received (aside from the form display event).
        assertEquals(2, testListener.getCount());
    }
}
