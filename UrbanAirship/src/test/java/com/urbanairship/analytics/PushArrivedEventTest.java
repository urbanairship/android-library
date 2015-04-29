package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;

import org.json.JSONException;
import org.junit.Test;

public class PushArrivedEventTest extends BaseTestCase {

    @Test
    public void testEventData() throws JSONException {
        PushArrivedEvent event = new PushArrivedEvent("push id");

        EventTestUtils.validateEventValue(event, Event.CONNECTION_TYPE_KEY, event.getConnectionType());
        EventTestUtils.validateEventValue(event, Event.CONNECTION_SUBTYPE_KEY, event.getConnectionSubType());
        EventTestUtils.validateEventValue(event, Event.CARRIER_KEY, event.getCarrier());
        EventTestUtils.validateEventValue(event, Event.PUSH_ID_KEY, "push id");
    }
}