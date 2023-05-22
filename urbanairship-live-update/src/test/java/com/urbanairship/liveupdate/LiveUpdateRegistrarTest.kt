package com.urbanairship.liveupdate

import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestApplication
import com.urbanairship.job.JobDispatcher
import com.urbanairship.liveupdate.LiveUpdateProcessor.Operation
import com.urbanairship.liveupdate.data.LiveUpdateDao
import com.urbanairship.liveupdate.data.LiveUpdateDatabase
import com.urbanairship.liveupdate.notification.NotificationTimeoutCompat
import com.urbanairship.liveupdate.util.jsonMapOf
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class LiveUpdateRegistrarTest {

    private val context = TestApplication.getApplication()
    private val testDispatcher = StandardTestDispatcher()

    private val database: LiveUpdateDatabase = mockk(relaxed = true)
    private val dao: LiveUpdateDao = mockk(relaxed = true)
    private val processor: LiveUpdateProcessor = mockk(relaxed = true)
    private val notificationManager: NotificationManagerCompat = mockk(relaxed = true)
    private val jobDispatcher: JobDispatcher = mockk(relaxed = true)
    private val notificationTimeoutCompat: NotificationTimeoutCompat = mockk(relaxed = true)

    private lateinit var registrar: LiveUpdateRegistrar

    @Before
    public fun setUp() {
        registrar = LiveUpdateRegistrar(
            context = context,
            dao = dao,
            processor = processor,
            dispatcher = testDispatcher,
            notificationManager = notificationManager,
            jobDispatcher = jobDispatcher,
            notificationTimeoutCompat = notificationTimeoutCompat
        )
        verifySequence {
            processor.handlerCallbacks
            processor.notificationCancels
            processor.channelUpdates
        }
        // Clear the initial handlerCallbacks call
        clearMocks(processor)
    }

    @After
    public fun tearDown() {
        database.close()
    }

    @Test
    public fun testRegisterHandler(): TestResult = runTest(testDispatcher) {
        assertTrue(registrar.handlers.isEmpty())

        val handler = TestHandler()
        registrar.register(TYPE, handler)

        assertEquals(1, registrar.handlers.size)
        assertEquals(handler, registrar.handlers[TYPE])
    }

    @Test
    public fun testStart(): TestResult = runTest(testDispatcher) {
        val handler = TestHandler()
        registrar.register(TYPE, handler)

        registrar.start(NAME, TYPE, CONTENT, TIMESTAMP, DISMISS_TIMESTAMP)

        val expected = Operation.Start(NAME, TYPE, CONTENT, TIMESTAMP, DISMISS_TIMESTAMP)

        verifySequence {
            processor.enqueue(eq(expected))
        }
    }

    @Test
    public fun testUpdate(): TestResult = runTest(testDispatcher) {
        val handler = TestHandler()
        registrar.register(TYPE, handler)

        registrar.start(NAME, TYPE, CONTENT, TIMESTAMP, DISMISS_TIMESTAMP)

        val expected = Operation.Start(NAME, TYPE, CONTENT, TIMESTAMP, DISMISS_TIMESTAMP)

        verifySequence {
            processor.enqueue(eq(expected))
        }
    }

    @Test
    public fun testStop(): TestResult = runTest(testDispatcher) {
        val handler = TestHandler()
        registrar.register(TYPE, handler)

        registrar.stop(NAME, CONTENT, TIMESTAMP, DISMISS_TIMESTAMP)

        val expected = Operation.Stop(NAME, CONTENT, TIMESTAMP, DISMISS_TIMESTAMP)

        verifySequence {
            processor.enqueue(eq(expected))
        }
    }

    private companion object {
        private const val NAME = "name"
        private const val TYPE = "type"
        private const val TIMESTAMP = 1000L
        private const val DISMISS_TIMESTAMP = 9000L
        private val CONTENT = jsonMapOf("foo" to "bar")
    }
}
