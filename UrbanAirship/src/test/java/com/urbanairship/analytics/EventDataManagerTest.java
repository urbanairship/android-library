package com.urbanairship.analytics;

import com.urbanairship.RobolectricGradleTestRunner;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
public class EventDataManagerTest {

    EventDataManager dataManager;

    @Before
    public void setUp() {
        dataManager = new EventDataManager();
    }

    /**
     * Test inserting an event then being able
     * to retrieve the data for the event
     */
    @Test
    public void testInsertEvent() {
        TestEvent event = new TestEvent("some-id");

        insertEvent(event, "session id");
        assertEquals(1, dataManager.getEventCount());

        Map<String, String> eventData = dataManager.getEvents(1);
        assertEquals(1, eventData.size());
        assertEquals(event.createEventPayload("session id"), eventData.get("some-id"));
    }

    /**
     * Test deleting events by the event id
     */
    @Test
    public void testDeleteEventById() {
        // Add two events with two different ids
        insertEvent(new TestEvent("some-id"));
        insertEvent(new TestEvent("some-other-id"));
        assertEquals(2, dataManager.getEventCount());

        // Delete one of the events
        assertTrue(dataManager.deleteEvent("some-id"));
        assertEquals(1, dataManager.getEventCount());

        // Make sure the other event still exists
        Map<String, String> eventData = dataManager.getEvents(1);
        assertEquals(1, eventData.size());
        assertTrue(eventData.containsKey("some-other-id"));
    }

    /**
     * Test deleting events, when the database is empty,
     * does not throw any exceptions
     */
    @Test
    public void testDeleteEventByIdEmptyDatabase() {
        // Try to delete an event.. should not throw up
        assertFalse(dataManager.deleteEvent("some-id"));
        assertEquals(0, dataManager.getEventCount());
    }

    /**
     * Test deleting events by the event type
     */
    @Test
    public void testDeleteByEventType() {
        insertEvent(new TestEvent("id-1", "EVENT TYPE 1"));
        insertEvent(new TestEvent("id-2", "EVENT TYPE 1"));
        insertEvent(new TestEvent("id-3", "EVENT TYPE 2"));
        assertEquals(3, dataManager.getEventCount());

        // Delete one of the event types
        assertTrue(dataManager.deleteEventType("EVENT TYPE 1"));
        assertEquals(1, dataManager.getEventCount());

        // Make sure the other event still exists
        Map<String, String> eventData = dataManager.getEvents(1);
        assertEquals(1, eventData.size());
        assertTrue(eventData.containsKey("id-3"));

        // Delete the other event type
        assertTrue(dataManager.deleteEventType("EVENT TYPE 2"));
        assertEquals(0, dataManager.getEventCount());
    }

    /**
     * Test deleting events by event type, when the database is empty,
     * does not throw any exceptions
     */
    @Test
    public void testDeleteEventsByTypeEmptyDatabase() {
        // Try to delete an event by type, should not throw up
        assertFalse(dataManager.deleteEventType("EVENT TYPE 1"));
        assertEquals(0, dataManager.getEventCount());
    }

    /**
     * Test deleting events by session id
     */
    @Test
    public void testDeleteBySessionId() {
        TestEvent event = new TestEvent("some-id");

        insertEvent(event, "session id");
        assertEquals(1, dataManager.getEventCount());

        assertTrue(dataManager.deleteSession("session id"));
        assertEquals(0, dataManager.getEventCount());
    }

    /**
     * Test deleting events by session id, when the database is empty,
     * does not throw any exceptions
     */
    @Test
    public void testDeleteBySessionIdEmptyDatabase() {
        // Should not throw up
        assertFalse(dataManager.deleteSession("some-session-id"));
        assertEquals(0, dataManager.getEventCount());
    }

    /**
     * Test getting the oldest session id
     */
    @Test
    public void testGetOldestSessionId() {
        insertEvent(new TestEvent(), "OLD");
        insertEvent(new TestEvent(), "NEW");

        assertEquals("OLD", dataManager.getOldestSessionId());
    }

    /**
     * Test that when the database is empty, oldest
     * session id should be null
     */
    @Test
    public void testGetOldestSessionIdEmptyDatabase() {
        assertNull(dataManager.getOldestSessionId());
    }

    /**
     * Test deleting several events at once
     */
    @Test
    public void testDeleteEvents() {
        insertEvent(new TestEvent("id-1"));
        insertEvent(new TestEvent("id-2"));
        insertEvent(new TestEvent("id-3"));
        insertEvent(new TestEvent("id-4"));

        assertEquals(4, dataManager.getEventCount());

        // Delete all but id-3
        assertTrue(dataManager.deleteEvents(new HashSet<String>(Arrays.asList("id-1", "id-2", "id-4"))));

        assertEquals(1, dataManager.getEventCount());
        assertTrue(dataManager.getEvents(1).containsKey("id-3"));
    }

    /**
     * Test when the database is empty, deleting multiple events
     * at once does not throw any exceptions
     */
    @Test
    public void testDeleteEventsEmptyDatabase() {
        assertFalse(dataManager.deleteEvents(new HashSet<String>(Arrays.asList("id", "another-id"))));
        assertEquals(0, dataManager.getEventCount());
    }


    /**
     * Tests getting events returns a set of events
     * from oldest to newest
     */
    @Test
    public void testGetEvents() {
        insertEvent(new TestEvent("oldest-id"));
        insertEvent(new TestEvent("older-id"));
        insertEvent(new TestEvent("newer-id"));

        assertEquals(3, dataManager.getEventCount());

        //Should return the oldest events
        Map<String, String> eventData = dataManager.getEvents(2);

        assertEquals(2, eventData.size());
        assertTrue(eventData.containsKey("oldest-id"));
        assertTrue(eventData.containsKey("older-id"));

        // Make sure if we request more than what's in the database
        // it wont blow up
        eventData = dataManager.getEvents(300);
        assertEquals(3, eventData.size());
    }

    /**
     * Test getting events on an empty database returns an empty
     * map
     */
    @Test
    public void testGetEventsEmptyDatabase() {
        Map<String, String> eventData = dataManager.getEvents(300);
        assertEquals(0, eventData.size());
    }

    /**
     * Test getting the database size
     */
    @Test
    public void testGetDatabaseSize() {
        assertEquals(0, dataManager.getDatabaseSize());

        TestEvent event = new TestEvent();
        int eventSize = event.createEventPayload("session id").length();

        insertEvent(event, "session id");
        assertEquals(eventSize, dataManager.getDatabaseSize());

        insertEvent(new TestEvent(), "session id");
        insertEvent(new TestEvent(), "session id");
        assertEquals(eventSize * 3, dataManager.getDatabaseSize());
    }

    /**
     * Test getting the event count
     */
    @Test
    public void testEventCount() {
        assertEquals(0, dataManager.getEventCount());

        insertEvent(new TestEvent());
        insertEvent(new TestEvent());
        insertEvent(new TestEvent());

        assertEquals(3, dataManager.getEventCount());
    }


    public long insertEvent(Event event) {
        return insertEvent(event, UUID.randomUUID().toString());
    }

    public long insertEvent(Event event, String sessionId) {
        return dataManager.insertEvent(event.getType(), event.createEventPayload(sessionId), event.getEventId(), sessionId, event.getTime());
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
        protected JSONObject getEventData() {
            return new JSONObject();
        }
    }

}
