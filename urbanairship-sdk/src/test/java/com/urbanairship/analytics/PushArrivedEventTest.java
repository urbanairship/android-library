/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.push.PushMessage;

import org.json.JSONException;
import org.junit.Test;

public class PushArrivedEventTest extends BaseTestCase {

    @Test
    public void testEventData() throws JSONException {
        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_SEND_ID, "push id");
        extras.putString(PushMessage.EXTRA_METADATA, "metadata");


        PushMessage pushMessage = new PushMessage(extras);
        PushArrivedEvent event = new PushArrivedEvent(pushMessage);

        EventTestUtils.validateEventValue(event, Event.CONNECTION_TYPE_KEY, event.getConnectionType());
        EventTestUtils.validateEventValue(event, Event.CONNECTION_SUBTYPE_KEY, event.getConnectionSubType());
        EventTestUtils.validateEventValue(event, Event.CARRIER_KEY, event.getCarrier());
        EventTestUtils.validateEventValue(event, Event.PUSH_ID_KEY, "push id");
        EventTestUtils.validateEventValue(event, Event.METADATA_KEY, "metadata");
    }
}