/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import androidx.test.core.app.ApplicationProvider
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestClock
import com.urbanairship.contacts.Contact
import com.urbanairship.contacts.ContactIdUpdate
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.json.jsonMapOf
import com.urbanairship.locale.LocaleChangedListener
import com.urbanairship.locale.LocaleManager
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import java.util.Locale
import java.util.UUID
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class RemoteDataTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private var config: TestAirshipRuntimeConfig = TestAirshipRuntimeConfig()
    private val dataStore: PreferenceDataStore = PreferenceDataStore.inMemoryStore(
        ApplicationProvider.getApplicationContext()
    )

    private val privacyManager: PrivacyManager = PrivacyManager(
        dataStore, PrivacyManager.Feature.ALL, dispatcher = UnconfinedTestDispatcher()
    )

    private val localeFlow = MutableSharedFlow<Locale>()
    private val mockLocaleManager: LocaleManager = mockk {
        every { localeUpdates } returns localeFlow
        every { this@mockk.locale } returns Locale.CANADA_FRENCH
    }

    private val pushListeners = mutableListOf<PushListener>()
    private val mockPushManager: PushManager = mockk {
        every { this@mockk.addInternalPushListener(capture(pushListeners)) } just runs
    }

    private val contactIdUpdates = MutableStateFlow<ContactIdUpdate?>(null)
    private val mockContact: Contact = mockk {
        every { this@mockk.contactIdUpdateFlow } returns this@RemoteDataTest.contactIdUpdates
    }

    private val testClock: TestClock = TestClock()
    private val testActivityMonitor: TestActivityMonitor = TestActivityMonitor()

    private val mockContactRemoteDataProvider: RemoteDataProvider = mockk {
        every { this@mockk.source } returns RemoteDataSource.CONTACT
    }
    private val mockAppRemoteDataProvider: RemoteDataProvider = mockk {
        every { this@mockk.source } returns RemoteDataSource.APP
    }

    private val refreshFlow = MutableSharedFlow<Pair<RemoteDataSource, RemoteDataProvider.RefreshResult>>()
    private val mockRefreshManager: RemoteDataRefreshManager = mockk(relaxed = true) {
        every { this@mockk.refreshFlow } returns this@RemoteDataTest.refreshFlow
    }

    private val jobInfo = JobInfo.newBuilder().setAction(RemoteData.ACTION_REFRESH).build()

    private val remoteData = RemoteData(
        context = ApplicationProvider.getApplicationContext(),
        preferenceDataStore = dataStore,
        config = config,
        privacyManager = privacyManager,
        localeManager = mockLocaleManager,
        pushManager = mockPushManager,
        contact = mockContact,
        providers = listOf(mockAppRemoteDataProvider, mockContactRemoteDataProvider),
        appVersion = 412312,
        activityMonitor = testActivityMonitor,
        refreshManager = mockRefreshManager,
        clock = testClock,
        coroutineDispatcher = testDispatcher
    )

    @Before
    public fun verifyStartupTask() {
        remoteData.onAirshipReady()
        coVerify {
            mockRefreshManager.performRefresh(
                any(), any(), any()
            )
        }
    }

    @Test
    public fun testConfigChangeDispatchesUpdate(): TestResult = runTest {
        every { mockContactRemoteDataProvider.isEnabled } returns false

        verify(exactly = 0) { mockRefreshManager.dispatchRefreshJob() }
        config.updateRemoteConfig(
            RemoteConfig(
                airshipConfig = RemoteAirshipConfig(
                    deviceApiUrl = UUID.randomUUID().toString()
                )
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockRefreshManager.dispatchRefreshJob() }
    }

    @Test
    public fun testSettingRefreshInterval(): TestResult = runTest {
        every { mockContactRemoteDataProvider.isEnabled } returns false

        assertEquals(10000L, remoteData.getRefreshInterval())
        config.updateRemoteConfig(
            RemoteConfig(
                remoteDataRefreshInterval = 21L
            )
        )
        assertEquals(21L, remoteData.getRefreshInterval())
    }

    @Test
    public fun testPrivacyManagerChangesDispatchesUpdate(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.NONE)
        verify(exactly = 0) { mockRefreshManager.dispatchRefreshJob() }
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.ANALYTICS)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockRefreshManager.dispatchRefreshJob() }
    }

    @Test
    public fun testLocalChangeDispatchesUpdate(): TestResult = runTest {
        verify(exactly = 0) { mockRefreshManager.dispatchRefreshJob() }
        localeFlow.emit(Locale.CANADA_FRENCH)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockRefreshManager.dispatchRefreshJob() }
    }

    @Test
    public fun testContactIDChangesDispatchesUpdate(): TestResult = runTest {
        verify(exactly = 0) { mockRefreshManager.dispatchRefreshJob() }
        contactIdUpdates.emit(ContactIdUpdate("some contact", null, false, 0))
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockRefreshManager.dispatchRefreshJob() }
    }

    @Test
    public fun testRemoteDataPushDispatchesUpdates(): TestResult = runTest {
        verify(exactly = 0) { mockRefreshManager.dispatchRefreshJob() }
        val mockPush = mockk<PushMessage> {
            every { this@mockk.isRemoteDataUpdate } returns true
        }
        pushListeners.forEach { it.onPushReceived(mockPush, false) }
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockRefreshManager.dispatchRefreshJob() }
    }

    @Test
    public fun testForegroundDispatchesEveryInterval(): TestResult = runTest {
        verify(exactly = 0) { mockRefreshManager.dispatchRefreshJob() }
        testActivityMonitor.foreground()
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockRefreshManager.dispatchRefreshJob() }

        testActivityMonitor.background()
        testActivityMonitor.foreground()
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockRefreshManager.dispatchRefreshJob() }

        testClock.currentTimeMillis += remoteData.getRefreshInterval() - 1
        testActivityMonitor.background()
        testActivityMonitor.foreground()
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockRefreshManager.dispatchRefreshJob() }

        testClock.currentTimeMillis += 1
        testActivityMonitor.background()
        testActivityMonitor.foreground()
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 2) { mockRefreshManager.dispatchRefreshJob() }
    }

    @Test
    public fun testRefreshSuccess(): TestResult = runTest {
        awaitAll(
            async {
                assertTrue(remoteData.refresh())
            },
            async {
                refreshFlow.emit(Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.NEW_DATA))
                refreshFlow.emit(Pair(RemoteDataSource.CONTACT, RemoteDataProvider.RefreshResult.SKIPPED))
            }
        )
    }

    @Test
    public fun testRefreshFailed(): TestResult = runTest {
        awaitAll(
            async {
                assertFalse(remoteData.refresh())
            },
            async {
                refreshFlow.emit(Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.NEW_DATA))
                refreshFlow.emit(Pair(RemoteDataSource.CONTACT, RemoteDataProvider.RefreshResult.FAILED))
            }
        )
    }

    @Test
    public fun testRefreshSingleSource(): TestResult = runTest {
        awaitAll(
            async {
                assertTrue(remoteData.refresh(RemoteDataSource.APP))
            },
            async {
                refreshFlow.emit(Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.NEW_DATA))
                refreshFlow.emit(Pair(RemoteDataSource.CONTACT, RemoteDataProvider.RefreshResult.FAILED))
            }
        )
    }

    @Test
    public fun testPayloads(): TestResult = runTest {
        val contactFoo = RemoteDataPayload(
            type = "foo",
            timestamp = 1L,
            data = jsonMapOf("foo" to "contact")
        )

        val channelFoo = RemoteDataPayload(
            type = "foo",
            timestamp = 1L,
            data = jsonMapOf("foo" to "channel")
        )

        val channelBar = RemoteDataPayload(
            type = "bar",
            timestamp = 1L,
            data = jsonMapOf("bar" to "channel")
        )

        coEvery { mockContactRemoteDataProvider.payloads(listOf("foo", "bar")) } returns setOf(contactFoo)
        coEvery { mockAppRemoteDataProvider.payloads(listOf("foo", "bar")) } returns setOf(channelBar, channelFoo)

        val payloads = remoteData.payloads(listOf("foo", "bar"))
        assertEquals(listOf(channelFoo, contactFoo, channelBar), payloads)
    }

    @Test
    public fun testPayloadsEmptyResult(): TestResult = runTest {
        coEvery { mockContactRemoteDataProvider.payloads(listOf("foo", "bar")) } returns emptySet()
        coEvery { mockAppRemoteDataProvider.payloads(listOf("foo", "bar")) } returns emptySet()

        val payloads = remoteData.payloads(listOf("foo", "bar"))
        assertEquals(emptyList<RemoteDataPayload>(), payloads)
    }

    @Test
    public fun testPayloadsEmptyTypes(): TestResult = runTest {
        val payloads = remoteData.payloads(emptyList())
        assertEquals(emptyList<RemoteDataPayload>(), payloads)
    }

    @Test
    public fun testPayloadFlow(): TestResult = runTest {
        val contactFoo = RemoteDataPayload(
            type = "foo",
            timestamp = 1L,
            data = jsonMapOf("foo" to "contact")
        )

        val channelFoo = RemoteDataPayload(
            type = "foo",
            timestamp = 1L,
            data = jsonMapOf("foo" to "channel")
        )

        val channelBar = RemoteDataPayload(
            type = "bar",
            timestamp = 1L,
            data = jsonMapOf("bar" to "channel")
        )

        coEvery { mockContactRemoteDataProvider.payloads(listOf("foo", "bar")) } returnsMany listOf(
            emptySet(),
            setOf(contactFoo),
            setOf(contactFoo)
        )

        coEvery { mockAppRemoteDataProvider.payloads(listOf("foo", "bar")) } returnsMany listOf(
            emptySet(),
            emptySet(),
            setOf(channelFoo, channelBar)
        )

        remoteData.payloadFlow(listOf("foo", "bar")).test {
            assertEquals(emptyList<RemoteDataPayload>(), awaitItem())

            // Verify Skipped and Failed are ignored
            refreshFlow.emit(Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.SKIPPED))
            refreshFlow.emit(Pair(RemoteDataSource.CONTACT, RemoteDataProvider.RefreshResult.SKIPPED))
            refreshFlow.emit(Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.FAILED))
            refreshFlow.emit(Pair(RemoteDataSource.CONTACT, RemoteDataProvider.RefreshResult.FAILED))
            ensureAllEventsConsumed()

            refreshFlow.emit(Pair(RemoteDataSource.CONTACT, RemoteDataProvider.RefreshResult.NEW_DATA))
            assertEquals(listOf(contactFoo), awaitItem())

            refreshFlow.emit(Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.NEW_DATA))
            assertEquals(listOf(channelFoo, contactFoo, channelBar), awaitItem())
        }
    }

    @Test
    public fun testNotifyOutdatedAppInfo(): TestResult = runTest {
        val appRemoteDataInfo = RemoteDataInfo("some url", "some modified", RemoteDataSource.APP)
        every { mockAppRemoteDataProvider.notifyOutdated(appRemoteDataInfo) } returns true
        remoteData.notifyOutdated(appRemoteDataInfo)
        verify { mockAppRemoteDataProvider.notifyOutdated(appRemoteDataInfo) }
    }

    @Test
    public fun testNotifyOutdatedContactInfo(): TestResult = runTest {
        val contactRemoteDataInfo = RemoteDataInfo("some other url", "some modified", RemoteDataSource.CONTACT)
        every { mockContactRemoteDataProvider.notifyOutdated(contactRemoteDataInfo) } returns true
        remoteData.notifyOutdated(contactRemoteDataInfo)
        verify { mockContactRemoteDataProvider.notifyOutdated(contactRemoteDataInfo) }
    }

    @Test
    public fun testJobDispatchSuccess(): TestResult = runTest {
        coEvery { mockRefreshManager.performRefresh(any(), any(), any()) } returns JobResult.SUCCESS
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(mockk(), jobInfo))
    }

    @Test
    public fun testJobDispatchFailure(): TestResult = runTest {
        coEvery { mockRefreshManager.performRefresh(any(), any(), any()) } returns JobResult.FAILURE
        assertEquals(JobResult.FAILURE, remoteData.onPerformJob(mockk(), jobInfo))
    }

    @Test
    public fun testRefresh(): TestResult = runTest {
        coEvery { mockRefreshManager.performRefresh(any(), any(), any()) } returns JobResult.SUCCESS
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(mockk(), jobInfo))

        coVerify {
            mockRefreshManager.performRefresh(
                match { it.endsWith("412312") },
                Locale.CANADA_FRENCH,
                remoteData.randomValue
            )
        }
    }

    @Test
    public fun testRefreshWithPrivacyManager(): TestResult = runTest {
        coEvery { mockRefreshManager.performRefresh(any(), any(), any()) } returns JobResult.SUCCESS

        privacyManager.setEnabledFeatures(PrivacyManager.Feature.NONE)
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(mockk(), jobInfo))

        privacyManager.setEnabledFeatures(PrivacyManager.Feature.ANALYTICS)
        assertEquals(JobResult.SUCCESS, remoteData.onPerformJob(mockk(), jobInfo))

        coVerify(exactly = 3) { mockRefreshManager.performRefresh(any(), any(), any()) }
    }

    @Test
    public fun testRefreshStatusSuccess(): TestResult = runTest {
        remoteData.refreshStatusFlow(RemoteDataSource.APP).test {
            assertEquals(RemoteData.RefreshStatus.NONE, awaitItem())
            refreshFlow.emit(Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.NEW_DATA))
            assertEquals(RemoteData.RefreshStatus.SUCCESS, awaitItem())
        }
    }

    @Test
    public fun testRefreshStatusFailed(): TestResult = runTest {
        remoteData.refreshStatusFlow(RemoteDataSource.APP).test {
            assertEquals(RemoteData.RefreshStatus.NONE, awaitItem())
            refreshFlow.emit(Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.FAILED))
            assertEquals(RemoteData.RefreshStatus.FAILED, awaitItem())
        }
    }

    @Test
    public fun testRefreshStatusSkipped(): TestResult = runTest {
        remoteData.refreshStatusFlow(RemoteDataSource.APP).test {
            assertEquals(RemoteData.RefreshStatus.NONE, awaitItem())
            refreshFlow.emit(Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.SKIPPED))
            assertEquals(RemoteData.RefreshStatus.SUCCESS, awaitItem())
        }
    }
}
