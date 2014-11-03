package com.urbanairship.analytics;

import com.urbanairship.RobolectricGradleTestRunner;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RobolectricGradleTestRunner.class)
public class PushArrivedEventTest {

    @Test
    public void testEventData() throws JSONException {
        PushArrivedEvent event = new PushArrivedEvent("push id");

        EventTestUtils.validateEventValue(event, Event.CONNECTION_TYPE_KEY, event.getConnectionType());
        EventTestUtils.validateEventValue(event, Event.CONNECTION_SUBTYPE_KEY, event.getConnectionSubType());
        EventTestUtils.validateEventValue(event, Event.CARRIER_KEY, event.getCarrier());
        EventTestUtils.validateEventValue(event, Event.PUSH_ID_KEY, "push id");
    }
}