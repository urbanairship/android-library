/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics.data;

import com.urbanairship.BaseTestCase;
import com.urbanairship.analytics.Event;
import com.urbanairship.json.JsonMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventResolverTest extends BaseTestCase {

    EventResolver eventResolver;

    @Before
    public void setUp() {
        eventResolver = new EventResolver(RuntimeEnvironment.application);
    }

    /**
     * Test inserting an event then being able
     * to retrieve the data for the event
     */
    @Test
    public void testInsertEvent() {
        TestEvent event = new TestEvent("some-id");

        eventResolver.insertEvent(event, "session id");
        assertEquals(1, eventResolver.getEventCount());

        Map<String, String> eventData = eventResolver.getEvents(1);
        assertEquals(1, eventData.size());
        Assert.assertEquals(event.createEventPayload("session id"), eventData.get("some-id"));
    }

    /**
     * Test trimming the database deletes the oldest sessions until its under the specified size.
     */
    @Test
    public void testTrimDatabase() {
        TestEvent event = new TestEvent("some-id");

        eventResolver.insertEvent(event, "OLD");
        eventResolver.insertEvent(event, "NEW");

        assertEquals(2, eventResolver.getEventCount());

        // Trim it down to only the size of the first session
        eventResolver.trimDatabase(event.createEventPayload("OLD").length());
        assertEquals(1, eventResolver.getEventCount());

        // Trim it down to 0
        eventResolver.trimDatabase(0);
        assertEquals(0, eventResolver.getEventCount());
    }

    /**
     * Test trimming the database when the oldest session ID is null does not throw
     * an exception.
     */
    @Test
    public void testTrimDatabaseNullSessionId() {
        // Insert an event when a null session ID
        TestEvent event = new TestEvent("some-id");
        eventResolver.insertEvent(event, null);

        // Trim the database
        eventResolver.trimDatabase(0);
    }

    /**
     * Test deleting several events at once
     */
    @Test
    public void testDeleteEvents() {
        eventResolver.insertEvent(new TestEvent("id-1"), "session-id");
        eventResolver.insertEvent(new TestEvent("id-2"), "session-id");
        eventResolver.insertEvent(new TestEvent("id-3"), "session-id");
        eventResolver.insertEvent(new TestEvent("id-4"), "session-id");

        assertEquals(4, eventResolver.getEventCount());

        // Delete all but id-3
        assertTrue(eventResolver.deleteEvents(new HashSet<>(Arrays.asList("id-1", "id-2", "id-4"))));

        assertEquals(1, eventResolver.getEventCount());
        assertTrue(eventResolver.getEvents(1).containsKey("id-3"));
    }

    /**
     * Test when the database is empty, deleting multiple events
     * at once does not throw any exceptions
     */
    @Test
    public void testDeleteEventsEmptyDatabase() {
        assertFalse(eventResolver.deleteEvents(new HashSet<>(Arrays.asList("id", "another-id"))));
        assertEquals(0, eventResolver.getEventCount());
    }

    /**
     * Tests getting events returns a set of events
     * from oldest to newest
     */
    @Test
    public void testGetEvents() {
        eventResolver.insertEvent(new TestEvent("oldest-id"), UUID.randomUUID().toString());
        eventResolver.insertEvent(new TestEvent("older-id"), UUID.randomUUID().toString());
        eventResolver.insertEvent(new TestEvent("newer-id"), UUID.randomUUID().toString());

        assertEquals(3, eventResolver.getEventCount());

        //Should return the oldest events
        Map<String, String> eventData = eventResolver.getEvents(2);

        assertEquals(2, eventData.size());
        assertTrue(eventData.containsKey("oldest-id"));
        assertTrue(eventData.containsKey("older-id"));
    }

    /**
     * Test asking for more events than what is currently available.
     */
    @Test
    public void testGetEventsMoreThanAvailable() {
        eventResolver.insertEvent(new TestEvent("oldest-id"), UUID.randomUUID().toString());
        Map<String, String> eventData = eventResolver.getEvents(300);
        assertEquals(1, eventData.size());
    }

    /**
     * Test getting events on an empty database returns an empty
     * map
     */
    @Test
    public void testGetEventsEmptyDatabase() {
        Map<String, String> eventData = eventResolver.getEvents(300);
        assertEquals(0, eventData.size());
    }

    /**
     * Test getting the database size
     */
    @Test
    public void testGetDatabaseSize() {
        assertEquals(0, eventResolver.getDatabaseSize());

        TestEvent event = new TestEvent();
        int eventSize = event.createEventPayload("session id").length();

        eventResolver.insertEvent(event, "session id");
        assertEquals(eventSize, eventResolver.getDatabaseSize());

        eventResolver.insertEvent(new TestEvent(), "session id");
        eventResolver.insertEvent(new TestEvent(), "session id");
        assertEquals(eventSize * 3, eventResolver.getDatabaseSize());
    }

    /**
     * Test getting the event count
     */
    @Test
    public void testEventCount() {
        assertEquals(0, eventResolver.getEventCount());

        eventResolver.insertEvent(new TestEvent(), UUID.randomUUID().toString());
        eventResolver.insertEvent(new TestEvent(), UUID.randomUUID().toString());
        eventResolver.insertEvent(new TestEvent(), UUID.randomUUID().toString());

        assertEquals(3, eventResolver.getEventCount());
    }

    /**
     * Testing class for testing events
     */
    static class TestEvent extends Event {
        String id;
        String eventType;

        public TestEvent() {
            this(null);
        }

        public TestEvent(String id) {
            this(id, "TEST EVENT");
        }

        public TestEvent(String id, String eventType) {
            this.id = id;
            this.eventType = eventType;
        }

        @Override
        public String getType() {
            return eventType;
        }

        @Override
        public String getEventId() {
            if (id != null) {
                return id;
            }

            return super.getEventId();
        }

        @Override
        protected JsonMap getEventData() {
            return new JsonMap(null);
        }
    }

}
