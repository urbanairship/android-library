package com.urbanairship.analytics.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.analytics.AirshipEventData
import com.urbanairship.analytics.EventType
import com.urbanairship.json.JsonMap
import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest

@RunWith(AndroidJUnit4::class)
public class EventDaoTest {

    private val db = AnalyticsDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext())
    private val eventDao = db.eventDao

    @After
    public fun tearDown() {
        db.close()
    }

    @Test
    public fun testInsert(): TestResult = runTest {
        Assert.assertEquals(0, eventDao.count().toLong())
        eventDao.insert(ENTITY)
        Assert.assertEquals(1, eventDao.count().toLong())

        val batch = eventDao.getBatch(1)
        val event = batch[0]
        Assert.assertNotNull(event)

        Assert.assertEquals(ENTITY.eventId, event.eventId)
        Assert.assertEquals(ENTITY.data, event.data)
    }

    @Test
    public fun testGetAndDeleteBatch(): TestResult = runTest {
        val event1 = AirshipEventData(
            "event-1",
            "session-1",
            JsonMap.EMPTY_MAP.toJsonValue(),
            EventType.APP_FOREGROUND,
            System.currentTimeMillis()
        )

        val event2 = AirshipEventData(
            "event-2",
            "session-2",
            JsonMap.EMPTY_MAP.toJsonValue(),
            EventType.APP_FOREGROUND,
            System.currentTimeMillis()
        )

        val entity1 = EventEntity(event1)
        val entity2 = EventEntity(event2)

        eventDao.insert(entity1)
        eventDao.insert(entity2)
        Assert.assertEquals(2, eventDao.count().toLong())

        val batch = eventDao.getBatch(10)
        Assert.assertEquals(2, batch.size.toLong())

        Assert.assertEquals(entity1.eventId, batch[0].eventId)
        Assert.assertEquals(entity1.data, batch[0].data)

        Assert.assertEquals(entity2.eventId, batch[1].eventId)
        Assert.assertEquals(entity2.data, batch[1].data)

        eventDao.deleteBatch(batch)
        Assert.assertEquals(0, eventDao.count().toLong())
    }

    @Test
    public fun testDatabaseSize(): TestResult = runTest {
        Assert.assertEquals(0, eventDao.databaseSize().toLong())

        eventDao.insert(ENTITY)
        Assert.assertEquals(ENTITY.eventSize.toLong(), eventDao.databaseSize().toLong())
    }

    @Test
    public fun testTrim(): TestResult = runTest {
        val event1 = AirshipEventData(
            "event-1",
            "session-1",
            JsonMap.EMPTY_MAP.toJsonValue(),
            EventType.APP_FOREGROUND,
            System.currentTimeMillis()
        )

        val event2 = AirshipEventData(
            "event-2",
            "session-2",
            JsonMap.EMPTY_MAP.toJsonValue(),
            EventType.APP_FOREGROUND,
            System.currentTimeMillis()
        )

        val entity1 = EventEntity(event1)
        val entity2 = EventEntity(event2)

        eventDao.insert(entity1)
        eventDao.insert(entity2)
        Assert.assertEquals(2, eventDao.count().toLong())
        val combinedSize = entity1.eventSize + entity2.eventSize
        Assert.assertEquals(combinedSize.toLong(), eventDao.databaseSize().toLong())
        Assert.assertEquals("session-1", eventDao.oldestSessionId())

        eventDao.trimDatabase(200)
        Assert.assertEquals(1, eventDao.count().toLong())
        Assert.assertTrue(entity2.contentEquals(eventDao.get()[0]))

        eventDao.trimDatabase(0)
        Assert.assertEquals(0, eventDao.count().toLong())
    }

    @Test
    public fun testTrimWithNullSessionId(): TestResult = runTest {
        val nullSessionEntity = ENTITY.copy(sessionId = null)
        eventDao.insert(nullSessionEntity)
        Assert.assertEquals(1, eventDao.count().toLong())

        // Verify trim doesn't throw an exception and bailed out when the oldest session ID was null
        eventDao.trimDatabase(0)
        Assert.assertEquals(1, eventDao.count().toLong())
    }

    @Test
    public fun testDeleteAll(): TestResult = runTest {
        val event1 = AirshipEventData(
            "event-1",
            "session-1",
            JsonMap.EMPTY_MAP.toJsonValue(),
            EventType.APP_FOREGROUND,
            System.currentTimeMillis()
        )

        val event2 = AirshipEventData(
            "event-2",
            "session-2",
            JsonMap.EMPTY_MAP.toJsonValue(),
            EventType.APP_FOREGROUND,
            System.currentTimeMillis()
        )
        val event3 = AirshipEventData(
            "event-3",
            "session-3",
            JsonMap.EMPTY_MAP.toJsonValue(),
            EventType.APP_FOREGROUND,
            System.currentTimeMillis()
        )

        val entity1 = EventEntity(event1)
        val entity2 = EventEntity(event2)
        val entity3 = EventEntity(event3)

        eventDao.insert(entity1)
        eventDao.insert(entity2)
        eventDao.insert(entity3)
        Assert.assertEquals(3, eventDao.count().toLong())

        eventDao.deleteAll()
        Assert.assertEquals(0, eventDao.count().toLong())
    }

    internal companion object {
        private const val SESSION_ID = "session-id"

        private val EVENT = AirshipEventData(
            "event-id",
            "session-id",
            JsonMap.EMPTY_MAP.toJsonValue(),
            EventType.APP_FOREGROUND,
            System.currentTimeMillis()
        )

        private val ENTITY = EventEntity(EVENT)
    }
}
