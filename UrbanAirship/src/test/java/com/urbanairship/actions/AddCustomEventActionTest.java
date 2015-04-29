package com.urbanairship.actions;

import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.analytics.EventTestUtils;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushManager;
import com.urbanairship.richpush.RichPushMessage;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class AddCustomEventActionTest extends BaseTestCase {

    AddCustomEventAction action;
    Situation[] acceptedSituations;
    Analytics analytics;
    RichPushMessage message;

    @Before
    public void setup() {
        action = new AddCustomEventAction();

        acceptedSituations = new Situation[] {
                Situation.PUSH_OPENED,
                Situation.MANUAL_INVOCATION,
                Situation.WEB_VIEW_INVOCATION,
                Situation.PUSH_RECEIVED,
                Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON,
                Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        analytics = mock(Analytics.class);
        TestApplication.getApplication().setAnalytics(analytics);


        RichPushManager richPushManager = mock(RichPushManager.class);
        RichPushInbox richPushInbox = mock(RichPushInbox.class);
        message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("message id");
        when(richPushManager.getRichPushInbox()).thenReturn(richPushInbox);
        when(richPushInbox.getMessage("message id")).thenReturn(message);

        TestApplication.getApplication().setRichPushManager(richPushManager);
    }

    /**
     * Test custom event action accepts all the situations.
     */
    @Test
    public void testAcceptsArgumentsAllSituations() {
        Map map = new HashMap();
        map.put("event_name", "event name");

        // Check every accepted situation
        for (Situation situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, map);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test that it rejects empty argument values.
     */
    @Test
    public void testAcceptsArgumentsEmptyArgs() {
        ActionArguments args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, null);
        assertFalse("Should reject empty args", action.acceptsArguments(args));
    }

    /**
     * Test that it rejects an empty map.
     */
    @Test
    public void testAcceptsArgumentsEmptyMap() {
        ActionArguments args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, new HashMap());
        assertFalse("Should reject empty map", action.acceptsArguments(args));
    }

    /**
     * Test that it rejects non-map values.
     */
    @Test
    public void testAcceptsArgumentsInvalidValue() {
        ActionArguments args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "not valid");
        assertFalse("Should reject non-map action argument values", action.acceptsArguments(args));
    }

    /**
     * Test performing the action actually creates and adds the event.
     */
    @Test
    public void testPerform() throws JSONException {
        Map map = new HashMap();
        map.put(CustomEvent.TRANSACTION_ID, "transaction id");
        map.put(CustomEvent.EVENT_VALUE, "123.45");
        map.put(CustomEvent.INTERACTION_TYPE, "interaction type");
        map.put(CustomEvent.INTERACTION_ID, "interaction id");
        map.put(CustomEvent.EVENT_NAME, "event name");

        ActionArguments args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map);

        ActionResult result = action.perform(args);
        assertEquals("Action should of completed", ActionResult.Status.COMPLETED, result.getStatus());

        // Verify the event was added
        ArgumentCaptor<CustomEvent> argumentCaptor = ArgumentCaptor.forClass(CustomEvent.class);
        verify(analytics).addEvent(argumentCaptor.capture());

        // Validate the resulting event
        CustomEvent event = argumentCaptor.getValue();
        EventTestUtils.validateEventValue(event, CustomEvent.TRANSACTION_ID, "transaction id");
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_VALUE, 123450000L);
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_TYPE, "interaction type");
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, "interaction id");
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_NAME, "event name");
    }

    /**
     * Test performing in a MCRAP will auto fill the interaction.
     */
    @Test
    public void testPerformMCRAP() throws JSONException {
        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("message id");

        Map map = new HashMap();
        map.put(CustomEvent.EVENT_NAME, "event name");

        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, message.getMessageId());

        ActionArguments args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map, metadata);

        ActionResult result = action.perform(args);
        assertEquals("Action should of completed", ActionResult.Status.COMPLETED, result.getStatus());

        // Verify the event was added
        ArgumentCaptor<CustomEvent> argumentCaptor = ArgumentCaptor.forClass(CustomEvent.class);
        verify(analytics).addEvent(argumentCaptor.capture());

        // Validate the resulting event
        CustomEvent event = argumentCaptor.getValue();
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_NAME, "event name");
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_TYPE, "ua_mcrap");
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, "message id");
    }

    /**
     * Test performing in a MCRAP will not fill the interaction if its set.
     */
    @Test
    public void testPerformMCRAPInteractionSet() throws JSONException {
        RichPushMessage message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("message id");

        Map map = new HashMap();
        map.put(CustomEvent.EVENT_NAME, "event name");
        map.put(CustomEvent.INTERACTION_TYPE, "interaction type");

        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, message.getMessageId());

        ActionArguments args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map, metadata);


        ActionResult result = action.perform(args);
        assertEquals("Action should of completed", ActionResult.Status.COMPLETED, result.getStatus());

        // Verify the event was added
        ArgumentCaptor<CustomEvent> argumentCaptor = ArgumentCaptor.forClass(CustomEvent.class);
        verify(analytics).addEvent(argumentCaptor.capture());

        // Validate the resulting event
        CustomEvent event = argumentCaptor.getValue();
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_TYPE, "interaction type");
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, null);
    }

    /**
     * Test invalid event values does not throw an exception, instead has an execution error.
     */
    @Test
    public void testRunInvalidCustomEventValue() {
        Map map = new HashMap();
        map.put(CustomEvent.EVENT_NAME, "event name");
        // Too large of a value
        map.put(CustomEvent.EVENT_VALUE, Double.toString(Double.MAX_VALUE));

        ActionArguments args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map);

        // Should fail to create the event and result in error
        ActionResult result = action.run(args);
        assertEquals("Action should of fail", ActionResult.Status.EXECUTION_ERROR, result.getStatus());
    }


    /**
     * Test performing with the PushMessage metadata creates an event with the send id from th message.
     */
    @Test
    public void testPerformPushMessageMetadata() throws JSONException {
        Map map = new HashMap();
        map.put(CustomEvent.TRANSACTION_ID, "transaction id");
        map.put(CustomEvent.EVENT_VALUE, "123.45");
        map.put(CustomEvent.INTERACTION_TYPE, "interaction type");
        map.put(CustomEvent.INTERACTION_ID, "interaction id");
        map.put(CustomEvent.EVENT_NAME, "event name");

        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "send id");

        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, new PushMessage(pushBundle));
        metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, message.getMessageId());

        ActionArguments args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, map, metadata);

        ActionResult result = action.perform(args);
        assertEquals("Action should of completed", ActionResult.Status.COMPLETED, result.getStatus());

        // Verify the event was added
        ArgumentCaptor<CustomEvent> argumentCaptor = ArgumentCaptor.forClass(CustomEvent.class);
        verify(analytics).addEvent(argumentCaptor.capture());

        // Validate the resulting event
        CustomEvent event = argumentCaptor.getValue();

        EventTestUtils.validateEventValue(event, CustomEvent.TRANSACTION_ID, "transaction id");
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_VALUE, 123450000L);
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_TYPE, "interaction type");
        EventTestUtils.validateEventValue(event, CustomEvent.INTERACTION_ID, "interaction id");
        EventTestUtils.validateEventValue(event, CustomEvent.EVENT_NAME, "event name");
        EventTestUtils.validateEventValue(event, CustomEvent.CONVERSION_SEND_ID, "send id");
    }
}

