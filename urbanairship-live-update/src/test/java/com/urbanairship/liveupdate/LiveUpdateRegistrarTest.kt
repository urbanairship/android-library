package com.urbanairship.liveupdate

import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.liveupdate.LiveUpdateProcessor.Operation
import com.urbanairship.liveupdate.data.LiveUpdateContent
import com.urbanairship.liveupdate.data.LiveUpdateDao
import com.urbanairship.liveupdate.data.LiveUpdateDatabase
import com.urbanairship.liveupdate.data.LiveUpdateState
import com.urbanairship.liveupdate.data.LiveUpdateStateWithContent
import com.urbanairship.liveupdate.notification.NotificationTimeoutCompat
import com.urbanairship.liveupdate.util.jsonMapOf
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
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

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = StandardTestDispatcher()
    private val channel: AirshipChannel = mockk(relaxed = true)

    private val database: LiveUpdateDatabase = mockk(relaxed = true)
    private val dao: LiveUpdateDao = mockk(relaxed = true)
    private val processor: LiveUpdateProcessor = mockk(relaxed = true)
    private val notificationManager: NotificationManagerCompat = mockk(relaxed = true)
    private val notificationTimeoutCompat: NotificationTimeoutCompat = mockk(relaxed = true)
    private val clock: TestClock = TestClock().apply { currentTimeMillis = NOW }

    /** Stands in for the processor's callback channel so tests can drive handler callbacks. */
    private val handlerCallbacks = MutableSharedFlow<LiveUpdateProcessor.HandlerCallback>()

    private lateinit var registrar: LiveUpdateRegistrar

    @Before
    public fun setUp() {
        every { processor.handlerCallbacks } returns handlerCallbacks

        registrar = LiveUpdateRegistrar(
            context = context,
            channel = channel,
            dao = dao,
            processor = processor,
            dispatcher = testDispatcher,
            notificationManager = notificationManager,
            notificationTimeoutCompat = notificationTimeoutCompat,
            clock = clock,
            handlerDispatcher = testDispatcher
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

    @Test
    public fun testStaleWithoutNotificationIsEnded(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, TestNotificationHandler())
        givenActiveLiveUpdate(lastActivityAt = NOW - MAX_INACTIVITY_MS - 1)
        givenActiveNotificationTags()

        registrar.endStaleLiveUpdates()
        advanceUntilIdle()

        verify {
            processor.enqueue(eq(Operation.Stop(NAME, CONTENT, TIMESTAMP, DISMISS_TIMESTAMP)))
        }
    }

    @Test
    public fun testRecentWithoutNotificationIsNotEnded(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, TestNotificationHandler())
        givenActiveLiveUpdate(lastActivityAt = NOW)
        givenActiveNotificationTags()

        registrar.endStaleLiveUpdates()
        advanceUntilIdle()

        verify(exactly = 0) { processor.enqueue(any()) }
    }

    @Test
    public fun testStaleWithNotificationIsNotEnded(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, TestNotificationHandler())
        givenActiveLiveUpdate(lastActivityAt = NOW - MAX_INACTIVITY_MS - 1)
        givenActiveNotificationTags("$TYPE:$NAME")

        registrar.endStaleLiveUpdates()
        advanceUntilIdle()

        verify(exactly = 0) { processor.enqueue(any()) }
    }

    @Test
    public fun testRecentWithNotificationIsNotEnded(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, TestNotificationHandler())
        givenActiveLiveUpdate(lastActivityAt = NOW)
        givenActiveNotificationTags("$TYPE:$NAME")

        registrar.endStaleLiveUpdates()
        advanceUntilIdle()

        verify(exactly = 0) { processor.enqueue(any()) }
    }

    @Test
    public fun testExactlyAtInactivityThresholdIsNotEnded(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, TestNotificationHandler())
        givenActiveLiveUpdate(lastActivityAt = NOW - MAX_INACTIVITY_MS)
        givenActiveNotificationTags()

        registrar.endStaleLiveUpdates()
        advanceUntilIdle()

        verify(exactly = 0) { processor.enqueue(any()) }
    }

    @Test
    public fun testRecentContentKeepsAnOldStateAlive(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, TestNotificationHandler())
        coEvery { dao.getAllActive() } returns listOf(
            LiveUpdateStateWithContent(
                state = LiveUpdateState(
                    name = NAME,
                    type = TYPE,
                    isActive = true,
                    timestamp = NOW - MAX_INACTIVITY_MS - 1,
                    dismissalDate = DISMISS_TIMESTAMP
                ),
                content = LiveUpdateContent(name = NAME, content = CONTENT, timestamp = NOW)
            )
        )
        givenActiveNotificationTags()

        registrar.endStaleLiveUpdates()
        advanceUntilIdle()

        verify(exactly = 0) { processor.enqueue(any()) }
    }

    /** Custom handlers own their presentation, so the shade says nothing about them. */
    @Test
    public fun testCustomHandlerIsNeverEnded(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, TestHandler())
        givenActiveLiveUpdate(lastActivityAt = NOW - MAX_INACTIVITY_MS - 1)
        givenActiveNotificationTags()

        registrar.endStaleLiveUpdates()
        advanceUntilIdle()

        verify(exactly = 0) { processor.enqueue(any()) }
    }

    /** The snapshot is only worth taking once something is actually stale. */
    @Test
    public fun testNotificationsNotQueriedWhenNothingIsStale(): TestResult =
        runTest(testDispatcher) {
            registrar.register(TYPE, TestNotificationHandler())
            givenActiveLiveUpdate(lastActivityAt = NOW)

            registrar.endStaleLiveUpdates()
            advanceUntilIdle()

            verify(exactly = 0) { notificationManager.activeNotifications }
        }

    /** `activeNotifications` is known to throw on some OEM builds. */
    @Test
    public fun testNotificationQueryFailureEndsNothing(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, TestNotificationHandler())
        givenActiveLiveUpdate(lastActivityAt = NOW - MAX_INACTIVITY_MS - 1)
        every { notificationManager.activeNotifications } throws RuntimeException("OEM")

        registrar.endStaleLiveUpdates()
        advanceUntilIdle()

        verify(exactly = 0) { processor.enqueue(any()) }
    }

    //
    // Handler cancel results
    //

    /**
     * An `END` callback is itself produced by a stop, so cancelling in response must not enqueue
     * another. Besides logging a warning on every ordinary end, a redundant stop can end a Live
     * Update that has since been restarted under the same name.
     */
    @Test
    public fun testCancelOnEndDoesNotStopAgain(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, CancellingNotificationHandler())

        handlerCallbacks.emit(callback(LiveUpdateEvent.END))
        advanceUntilIdle()

        verify(exactly = 0) { processor.enqueue(any()) }
        // The notification is still cleared — that is what cancelling on END is for.
        verify { notificationManager.cancel("$TYPE:$NAME", 1010) }
    }

    /** Cancelling on any other event is the app ending the Live Update, so the stop is required. */
    @Test
    public fun testCancelOnUpdateStops(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, CancellingNotificationHandler())

        handlerCallbacks.emit(callback(LiveUpdateEvent.UPDATE))
        advanceUntilIdle()

        verify {
            processor.enqueue(eq(Operation.Stop(NAME, CONTENT, NOW, null)))
            notificationManager.cancel("$TYPE:$NAME", 1010)
        }
    }

    @Test
    public fun testCancelOnStartStops(): TestResult = runTest(testDispatcher) {
        registrar.register(TYPE, CancellingNotificationHandler())

        handlerCallbacks.emit(callback(LiveUpdateEvent.START))
        advanceUntilIdle()

        verify { processor.enqueue(eq(Operation.Stop(NAME, CONTENT, NOW, null))) }
    }

    private fun callback(event: LiveUpdateEvent) = LiveUpdateProcessor.HandlerCallback(
        action = event,
        update = LiveUpdate(
            name = NAME,
            type = TYPE,
            content = CONTENT,
            lastContentUpdateTime = TIMESTAMP,
            lastStateChangeTime = TIMESTAMP,
            dismissalTime = DISMISS_TIMESTAMP
        ),
        message = null
    )

    private fun givenActiveLiveUpdate(lastActivityAt: Long) {
        coEvery { dao.getAllActive() } returns listOf(
            LiveUpdateStateWithContent(
                state = LiveUpdateState(
                    name = NAME,
                    type = TYPE,
                    isActive = true,
                    timestamp = TIMESTAMP,
                    dismissalDate = DISMISS_TIMESTAMP
                ),
                content = LiveUpdateContent(
                    name = NAME,
                    content = CONTENT,
                    timestamp = lastActivityAt
                )
            )
        )
    }

    private fun givenActiveNotificationTags(vararg tags: String) {
        every { notificationManager.activeNotifications } returns tags.map { value ->
            mockk<StatusBarNotification>(relaxed = true) { every { tag } returns value }
        }
    }

    private companion object {
        private const val NAME = "name"
        private const val TYPE = "type"
        private val TIMESTAMP: Instant = Instant.ofEpochMilli(1000)
        private val DISMISS_TIMESTAMP: Instant = Instant.ofEpochMilli(9000)
        private val CONTENT = jsonMapOf("foo" to "bar")

        /** Well clear of the inactivity window, so "recent" and "stale" are unambiguous. */
        private const val NOW = 100_000_000_000L
        private val MAX_INACTIVITY_MS = LiveUpdateRegistrar.MAX_INACTIVITY.inWholeMilliseconds
    }
}
