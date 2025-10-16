package com.urbanairship.liveupdate.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.BaseTestCase
import com.urbanairship.liveupdate.util.jsonMapOf
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
public class LiveUpdateDaoTest : BaseTestCase() {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var db: LiveUpdateDatabase
    private lateinit var dao: LiveUpdateDao

    @Before
    public fun setUp() {
        db = LiveUpdateDatabase.createInMemoryDatabase(context, testDispatcher)
        dao = db.liveUpdateDao()
    }

    @After
    public fun tearDown() {
        db.close()
    }

    @Test
    public fun testInsert(): TestResult = runTest(testDispatcher) {
        assertRowCount(0)

        dao.upsert(STATE_V1)
        dao.upsert(CONTENT_V1)

        assertRowCount(1)
        assertEquals(ENTITY_V1, dao.get(NAME))
    }

    @Test
    public fun testUpdateState(): TestResult = runTest(testDispatcher) {
        suspend fun assert(count: Int, activeCount: Int) {
            assertRowCount(count)
            assertEquals(activeCount, dao.getAllActive().size)
        }

        dao.upsert(STATE_V1)
        dao.upsert(CONTENT_V1)
        assert(count = 1, activeCount = 0)

        dao.upsert(STATE_V1.copy(isActive = true, timestamp = TIME_V2))
        assert(count = 1, activeCount = 1)

        dao.upsert(STATE_V1.copy(isActive = false, timestamp = TIME_V3))
        assert(count = 1, activeCount = 0)
    }

    @Test
    public fun testUpdateContent(): TestResult = runTest(testDispatcher) {
        /** Asserts that we have a single Live Update that is not active. */
        suspend fun assert() {
            assertRowCount(1)
            assertEquals(STATE_V1, dao.getState(NAME))
        }

        dao.upsert(STATE_V1)
        dao.upsert(CONTENT_V1)

        assert()
        assertEquals(CONTENT_V1, dao.getContent(NAME))

        dao.upsert(CONTENT_V2)

        assert()
        assertEquals(CONTENT_V2, dao.getContent(NAME))
    }

    private suspend fun assertRowCount(count: Int) {
        assertEquals(count, dao.countState())
        assertEquals(count, dao.countContent())
    }

    @Suppress("unused")
    private companion object {
        private const val NAME = "live-update-name"
        private const val TYPE = "live-update-type"

        private const val TIME_V1 = 10L
        private val STATE_V1 = LiveUpdateState(
            name = NAME,
            type = TYPE,
            timestamp = TIME_V1,
            isActive = false
        )
        private val CONTENT_V1 = LiveUpdateContent(
            name = NAME,
            content = jsonMapOf("foo" to "bar"),
            timestamp = TIME_V1
        )

        private val ENTITY_V1 = LiveUpdateStateWithContent(state = STATE_V1, content = CONTENT_V1)

        private const val TIME_V2 = 20L
        private val CONTENT_V2 = CONTENT_V1.copy(
            content = jsonMapOf("fizz" to "buzz"),
            timestamp = TIME_V2
        )

        private val ENTITY_V2 = ENTITY_V1.copy(content = CONTENT_V2)

        private const val TIME_V3 = 30L
        private val CONTENT_V3 = CONTENT_V2.copy(
            content = jsonMapOf("slim" to "none"),
            timestamp = TIME_V3
        )
        private val ENTITY_V3 = ENTITY_V2.copy(content = CONTENT_V3)
    }
}
