package com.urbanairship.analytics;

import com.urbanairship.RobolectricGradleTestRunner;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricGradleTestRunner.class)
public class EventTest {

    private BasicEvent event;

    @Before
    public void setUp() {
        event = new BasicEvent();
    }

    @Test
    public void testCreateEventPayload() throws JSONException {
        JSONObject body = new JSONObject(event.createEventPayload("session id"));

        assertEquals(body.get(Event.TYPE_KEY), event.getType());
        assertEquals(body.get(Event.EVENT_ID_KEY), event.getEventId());
        assertEquals(body.get(Event.TIME_KEY), event.getTime());
        assertEquals(body.getJSONObject(Event.DATA_KEY).get(Event.SESSION_ID_KEY), "session id");
        assertEquals(body.getJSONObject(Event.DATA_KEY).get("some key"), "some value");
    }

    //a simple extension of the abstract Event class so
    //we can verify the essential JSON representation shared
    //by all derived events
    class BasicEvent extends Event {

        @Override
        public String getType() {
            return "basic";
        }

        @Override
        protected JSONObject getEventData() {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.putOpt("some key", "some value");
            } catch (JSONException e) {

            }
            return jsonObject;
        }
    }
}
