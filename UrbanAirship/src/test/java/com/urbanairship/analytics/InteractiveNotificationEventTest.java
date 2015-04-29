package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.push.PushMessage;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static com.urbanairship.analytics.EventTestUtils.validateEventValue;
import static org.mockito.Mockito.when;

public class InteractiveNotificationEventTest extends BaseTestCase {

    private PushMessage mockPushMessage;

    @Before
    public void setUp() {
        mockPushMessage = Mockito.mock(PushMessage.class);
    }

    /**
     * Test the interactive notification event data is populated properly.
     */
    @Test
    public void testData() throws JSONException {
        when(mockPushMessage.getSendId()).thenReturn("send id");
        when(mockPushMessage.getInteractiveNotificationType()).thenReturn("interactive notification type");


        InteractiveNotificationEvent event = new InteractiveNotificationEvent(mockPushMessage, "button id", "button description", false);

        validateEventValue(event, "button_id", "button id");
        validateEventValue(event, "button_description", "button description");
        validateEventValue(event, "button_group", "interactive notification type");
        validateEventValue(event, "foreground", false);
        validateEventValue(event, "send_id", "send id");
    }
}