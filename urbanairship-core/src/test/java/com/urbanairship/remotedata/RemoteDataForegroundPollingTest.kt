/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import androidx.test.core.app.ApplicationProvider
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestClock
import com.urbanairship.TestTaskSleeper
import com.urbanairship.contacts.Contact
import com.urbanairship.contacts.ContactIdUpdate
import com.urbanairship.locale.LocaleManager
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.remoteconfig.RemoteConfig
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class RemoteDataForegroundPollingTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val config = TestAirshipRuntimeConfig()
    private val dataStore = PreferenceDataStore.inMemoryStore(
        ApplicationProvider.getApplicationContext()
    )

    private val privacyManager = PrivacyManager(
        dataStore, PrivacyManager.Feature.ALL, dispatcher = UnconfinedTestDispatcher()
    )

    private val localeFlow = MutableSharedFlow<Locale>()
    private val mockLocaleManager: LocaleManager = mockk {
        every { localeUpdates } returns localeFlow
        every { this@mockk.locale } returns Locale.CANADA_FRENCH
    }

    private val mockPushManager: PushManager = mockk {
        every { this@mockk.addInternalPushListener(any<PushListener>()) } just runs
    }

    private val contactIdUpdates = MutableStateFlow<ContactIdUpdate?>(null)
    private val mockContact: Contact = mockk {
        every { this@mockk.contactIdUpdateFlow } returns contactIdUpdates
    }

    private val testClock = TestClock().apply { currentTimeMillis = 0 }
    private val testActivityMonitor = TestActivityMonitor()

    private val sleepGate = Channel<Unit>(Channel.UNLIMITED)
    private val testTaskSleeper = TestTaskSleeper(testClock) { sleepGate.receive() }

    private val mockContactProvider: RemoteDataProvider = mockk {
        every { source } returns RemoteDataSource.CONTACT
        every { isEnabled } returns false
    }
    private val mockAppProvider: RemoteDataProvider = mockk {
        every { source } returns RemoteDataSource.APP
    }

    private val refreshFlow =
        MutableSharedFlow<Pair<RemoteDataSource, RemoteDataProvider.RefreshResult>>()
    private val mockRefreshManager: RemoteDataRefreshManager = mockk(relaxed = true) {
        every { this@mockk.refreshFlow } returns this@RemoteDataForegroundPollingTest.refreshFlow
    }

    private fun makeRemoteData(): RemoteData = RemoteData(
        context = ApplicationProvider.getApplicationContext(),
        preferenceDataStore = dataStore,
        config = config,
        privacyManager = privacyManager,
        localeManager = mockLocaleManager,
        pushManager = mockPushManager,
        contact = mockContact,
        providers = listOf(mockAppProvider, mockContactProvider),
        appVersion = 1L,
        activityMonitor = testActivityMonitor,
        refreshManager = mockRefreshManager,
        clock = testClock,
        taskSleeper = testTaskSleeper,
        coroutineDispatcher = testDispatcher
    )

    @Test
    public fun testForegroundStartsPolling(): TestResult = runTest {
        val remoteData = makeRemoteData()
        remoteData.onAirshipReady()

        assertTrue(testTaskSleeper.sleeps.isEmpty())

        testActivityMonitor.foreground()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(10.minutes), testTaskSleeper.sleeps)
    }

    @Test
    public fun testAirshipReadyStartsPollingWhenForegrounded(): TestResult = runTest {
        // Foreground before constructing so init's synchronous onForeground triggers the polling
        // start path; onAirshipReady is then the only thing that unblocks the loop's first
        // dispatchRefreshJob, but the recorded sleep is what we assert on.
        testActivityMonitor.foreground()
        val remoteData = makeRemoteData()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(10.minutes), testTaskSleeper.sleeps)

        // onAirshipReady should be a no-op since polling is already active.
        remoteData.onAirshipReady()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(10.minutes), testTaskSleeper.sleeps)
    }

    @Test
    public fun testAirshipReadyDoesNotStartPollingWhenBackgrounded(): TestResult = runTest {
        val remoteData = makeRemoteData()
        remoteData.onAirshipReady()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(testTaskSleeper.sleeps.isEmpty())
    }

    @Test
    public fun testBackgroundStopsPolling(): TestResult = runTest {
        val remoteData = makeRemoteData()
        remoteData.onAirshipReady()

        testActivityMonitor.foreground()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, testTaskSleeper.sleeps.size)

        // Release one polling cycle so the loop fires a refresh and parks at the next sleep.
        sleepGate.trySend(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, testTaskSleeper.sleeps.size)
        verify(exactly = 1) { mockRefreshManager.dispatchRefreshJob() }

        // Background cancels the polling job; further gate releases should no-op.
        testActivityMonitor.background()
        testDispatcher.scheduler.advanceUntilIdle()
        sleepGate.trySend(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, testTaskSleeper.sleeps.size)
        verify(exactly = 1) { mockRefreshManager.dispatchRefreshJob() }
    }

    @Test
    public fun testForegroundPollingUsesRemoteConfigInterval(): TestResult = runTest {
        val remoteData = makeRemoteData()
        remoteData.onAirshipReady()

        config.updateRemoteConfig(
            RemoteConfig(remoteDataForegroundPollingInterval = 30.seconds)
        )

        assertEquals(30.seconds, remoteData.getForegroundPollingInterval())

        testActivityMonitor.foreground()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(30.seconds), testTaskSleeper.sleeps)
    }
}
