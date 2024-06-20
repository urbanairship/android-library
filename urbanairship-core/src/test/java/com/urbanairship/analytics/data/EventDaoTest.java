package com.urbanairship.analytics.data;

import com.urbanairship.BaseTestCase;
import com.urbanairship.analytics.AirshipEventData;
import com.urbanairship.analytics.ConversionData;
import com.urbanairship.analytics.Event;
import com.urbanairship.analytics.EventType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EventDaoTest extends BaseTestCase {
    private static final String SESSION_ID = "session-id";
    private static final AirshipEventData EVENT = new AirshipEventData(
            "event-id",
            "session-id",
            JsonMap.EMPTY_MAP.toJsonValue(),
            EventType.APP_FOREGROUND,
            System.currentTimeMillis()
    );

    private static EventEntity ENTITY;

    private AnalyticsDatabase db;
    private EventDao eventDao;

    @Before
    public void setUp() throws JsonException {
        db = AnalyticsDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext());
        eventDao = db.getEventDao();

        ENTITY = EventEntity.create(EVENT);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void testInsert() {
        assertEquals(0, eventDao.count());
        eventDao.insert(ENTITY);
        assertEquals(1, eventDao.count());

        List<EventEntity.EventIdAndData> batch = eventDao.getBatch(1);
        EventEntity.EventIdAndData event = batch.get(0);
        assertNotNull(event);

        assertEquals(ENTITY.eventId, event.eventId);
        assertEquals(ENTITY.data, event.data);
    }

    @Test
    public void testGetAndDeleteBatch() throws JsonException {
        AirshipEventData event1 = new AirshipEventData(
                "event-1",
                "session-1",
                JsonMap.EMPTY_MAP.toJsonValue(),
                EventType.APP_FOREGROUND,
                System.currentTimeMillis()
        );

        AirshipEventData event2 = new AirshipEventData(
                "event-2",
                "session-2",
                JsonMap.EMPTY_MAP.toJsonValue(),
                EventType.APP_FOREGROUND,
                System.currentTimeMillis()
        );

        EventEntity entity1 = EventEntity.create(event1);
        EventEntity entity2 = EventEntity.create(event2);

        eventDao.insert(entity1);
        eventDao.insert(entity2);
        assertEquals(2, eventDao.count());

        List<EventEntity.EventIdAndData> batch = eventDao.getBatch(10);
        assertEquals(2, batch.size());

        assertEquals(entity1.eventId, batch.get(0).eventId);
        assertEquals(entity1.data, batch.get(0).data);

        assertEquals(entity2.eventId, batch.get(1).eventId);
        assertEquals(entity2.data, batch.get(1).data);

        eventDao.deleteBatch(batch);
        assertEquals(0, eventDao.count());
    }

    @Test
    public void testDatabaseSize() {
        assertEquals(0, eventDao.databaseSize());

        eventDao.insert(ENTITY);
        assertEquals(ENTITY.eventSize, eventDao.databaseSize());
    }

    @Test
    public void testTrim() throws JsonException {
        AirshipEventData event1 = new AirshipEventData(
                "event-1",
                "session-1",
                JsonMap.EMPTY_MAP.toJsonValue(),
                EventType.APP_FOREGROUND,
                System.currentTimeMillis()
        );

        AirshipEventData event2 = new AirshipEventData(
                "event-2",
                "session-2",
                JsonMap.EMPTY_MAP.toJsonValue(),
                EventType.APP_FOREGROUND,
                System.currentTimeMillis()
        );

        EventEntity entity1 = EventEntity.create(event1);
        EventEntity entity2 = EventEntity.create(event2);

        eventDao.insert(entity1);
        eventDao.insert(entity2);
        assertEquals(2, eventDao.count());
        int combinedSize = entity1.eventSize + entity2.eventSize;
        assertEquals(combinedSize, eventDao.databaseSize());
        assertEquals("session-1", eventDao.oldestSessionId());

        eventDao.trimDatabase(200);
        assertEquals(1, eventDao.count());
        assertTrue(entity2.contentEquals(eventDao.get().get(0)));

        eventDao.trimDatabase(0);
        assertEquals(0, eventDao.count());
    }

    @Test
    public void testTrimWithNullSessionId() {
        ENTITY.sessionId = null;
        eventDao.insert(ENTITY);
        assertEquals(1, eventDao.count());

        // Verify trim doesn't throw an exception and bailed out when the oldest session ID was null
        eventDao.trimDatabase(0);
        assertEquals(1, eventDao.count());
    }

    @Test
    public void testDeleteAll() throws JsonException {
        AirshipEventData event1 = new AirshipEventData(
                "event-1",
                "session-1",
                JsonMap.EMPTY_MAP.toJsonValue(),
                EventType.APP_FOREGROUND,
                System.currentTimeMillis()
        );

        AirshipEventData event2 = new AirshipEventData(
                "event-2",
                "session-2",
                JsonMap.EMPTY_MAP.toJsonValue(),
                EventType.APP_FOREGROUND,
                System.currentTimeMillis()
        );
        AirshipEventData event3 = new AirshipEventData(
                "event-3",
                "session-3",
                JsonMap.EMPTY_MAP.toJsonValue(),
                EventType.APP_FOREGROUND,
                System.currentTimeMillis()
        );

        EventEntity entity1 = EventEntity.create(event1);
        EventEntity entity2 = EventEntity.create(event2);
        EventEntity entity3 = EventEntity.create(event3);

        eventDao.insert(entity1);
        eventDao.insert(entity2);
        eventDao.insert(entity3);
        assertEquals(3, eventDao.count());

        eventDao.deleteAll();
        assertEquals(0, eventDao.count());
    }
}
