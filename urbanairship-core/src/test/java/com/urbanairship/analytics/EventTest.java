/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;

public class EventTest extends BaseTestCase {

    private BasicEvent event;

    @Before
    public void setUp() {
        event = new BasicEvent();
    }

    @Test
    public void testCreateEventPayload() throws JsonException {
        JsonMap body = JsonValue.parseString(event.createEventPayload("session id")).optMap();

        assertEquals(body.get(Event.TYPE_KEY).getString(), event.getType().getReportingName());
        assertEquals(body.get(Event.EVENT_ID_KEY).getString(), event.getEventId());
        assertEquals(body.get(Event.TIME_KEY).getString(), event.getTime());
        assertEquals(body.get(Event.DATA_KEY).optMap().get(Event.SESSION_ID_KEY).getString(), "session id");
        assertEquals(body.get(Event.DATA_KEY).optMap().get("some key").getString(), "some value");
    }

    //a simple extension of the abstract Event class so
    //we can verify the essential JSON representation shared
    //by all derived events
    class BasicEvent extends Event {

        @NonNull
        @Override
        public EventType getType() {
            return EventType.APP_FOREGROUND;
        }

        @NonNull
        @Override
        public JsonMap getEventData() {
            return JsonMap.newBuilder().put("some key", "some value").build();
        }

    }

}
