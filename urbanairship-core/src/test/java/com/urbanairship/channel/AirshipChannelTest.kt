/* Copyright Airship and Contributors */
package com.urbanairship.channel

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestClock
import com.urbanairship.UAirship
import com.urbanairship.base.Extender
import com.urbanairship.config.AirshipUrlConfig
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.locale.LocaleChangedListener
import com.urbanairship.locale.LocaleManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class AirshipChannelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferenceDataStore = PreferenceDataStore.inMemoryStore(context)

    private val localeChangeListeners = mutableListOf<LocaleChangedListener>()
    private val channelPayloadExtenders =
        mutableListOf<Extender<ChannelRegistrationPayload.Builder>>()

    private val channelIdFlow = MutableStateFlow<String?>(null)

    private val testConfig = TestAirshipRuntimeConfig.newTestConfig()
    private val privacyManager = PrivacyManager(preferenceDataStore, PrivacyManager.FEATURE_ALL)
    private val testDispatcher = StandardTestDispatcher()
    private val mockLocaleManager = mockk<LocaleManager>() {
        every { this@mockk.addListener(capture(localeChangeListeners)) } just runs
    }

    private val mockRegistrar = mockk<ChannelRegistrar>() {
        every { this@mockk.channelIdFlow } returns this@AirshipChannelTest.channelIdFlow
        every { this@mockk.channelId } returns null
        every { this@mockk.addChannelRegistrationPayloadExtender(capture(channelPayloadExtenders)) } just runs
    }

    private val mockBatchUpdateManager = mockk<ChannelBatchUpdateManager>(relaxed = true)
    private val mockSubscriptions = mockk<ChannelSubscriptions>()
    private val mockJobDispatcher = mockk<JobDispatcher>(relaxed = true)

    private val testClock = TestClock()
    private val testActivityMonitor = TestActivityMonitor()

    private val replaceJob = JobInfo.newBuilder()
        .setAction("ACTION_UPDATE_CHANNEL")
        .setNetworkAccessRequired(true)
        .setAirshipComponent(AirshipChannel::class.java)
        .setConflictStrategy(JobInfo.REPLACE)
        .build()

    private val keepJob = JobInfo.newBuilder()
        .setAction("ACTION_UPDATE_CHANNEL")
        .setNetworkAccessRequired(true)
        .setAirshipComponent(AirshipChannel::class.java)
        .setConflictStrategy(JobInfo.KEEP)
        .build()

    private val channel = AirshipChannel(
        context,
        preferenceDataStore,
        testConfig,
        privacyManager,
        mockLocaleManager,
        mockSubscriptions,
        mockBatchUpdateManager,
        mockRegistrar,
        testActivityMonitor,
        mockJobDispatcher,
        testClock,
        testDispatcher
    )

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testForegroundUpdatesRegistration(): TestResult = runTest {
        testActivityMonitor.foreground()
        verify { mockJobDispatcher.dispatch(keepJob) }
    }

    @Test
    public fun testLocaleChangeUpdatesRegistration(): TestResult = runTest {
        localeChangeListeners.forEach { it.onLocaleChanged(Locale.ENGLISH) }
        verify { mockJobDispatcher.dispatch(keepJob) }
    }

    @Test
    public fun testUrlConfigUpdatedUpdatesRegistration(): TestResult = runTest {
        channel.onUrlConfigUpdated()
        verify { mockJobDispatcher.dispatch(keepJob) }
    }

    @Test
    public fun testPrivacyManagerChangeUpdatesRegistration(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS)
        verify { mockJobDispatcher.dispatch(keepJob) }
    }

    @Test
    public fun testDisableTagsClearsPendingUpdates(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        verify { mockBatchUpdateManager.clearPending() }
        verify { mockJobDispatcher.dispatch(keepJob) }
    }

    @Test
    public fun testChannelListener(): TestResult = runTest {
        val listener = mockk<AirshipChannelListener>(relaxed = true)
        channel.addChannelListener(listener)

        channelIdFlow.emit("some channel")
        testDispatcher.scheduler.advanceUntilIdle()
        verify { listener.onChannelCreated("some channel") }
    }

    @Test
    public fun testDisableTagsClearsTags(): TestResult = runTest {
        channel.editTags().addTag("foo").apply()
        assertEquals(channel.tags, setOf("foo"))

        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        assertEquals(channel.tags, emptySet<String>())
    }

    @Test
    public fun testSetTags(): TestResult = runTest {
        channel.tags = setOf("foo")
        verify { mockJobDispatcher.dispatch(keepJob) }
        assertEquals(channel.tags, setOf("foo"))
    }

    @Test
    public fun testNormalizeTags(): TestResult = runTest {
        channel.tags = setOf(
            // too long
            "128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" + "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[1",
            // Max length
            "128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" + "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[",
            // white space
            "",
            "  ",
            "    whitespace_test_tag    ",
        )

        val expected = setOf(
            "128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" + "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[",
            "whitespace_test_tag"
        )

        assertEquals(expected, channel.tags)
    }

    @Test
    public fun testSetTagsFeatureDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        channel.tags = setOf("foo")
        assertEquals(channel.tags, emptySet<String>())
    }

    @Test
    public fun testEditTags(): TestResult = runTest {
        channel.editTags().addTag("foo").apply()
        verify { mockJobDispatcher.dispatch(keepJob) }
        assertEquals(channel.tags, setOf("foo"))
    }

    @Test
    public fun testEditTagsFeatureDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        channel.editTags().addTag("foo").apply()
        assertEquals(channel.tags, emptySet<String>())
    }

    @Test
    public fun testEditTagGroups(): TestResult = runTest {
        channel.editTagGroups().addTags("some group", setOf("foo")).apply()
        verify { mockJobDispatcher.dispatch(keepJob) }
        verify {
            mockBatchUpdateManager.addUpdate(
                tags = listOf(
                    TagGroupsMutation.newAddTagsMutation("some group", setOf("foo"))
                )
            )
        }
    }

    @Test
    public fun testEditTagGroupsFeatureDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        channel.editTagGroups().addTags("some group", setOf("foo")).apply()
        verify(exactly = 0) { mockBatchUpdateManager.addUpdate(tags = any()) }
    }

    @Test
    public fun testEditTagGroupsEmpty(): TestResult = runTest {
        channel.editTagGroups().apply()
        verify(exactly = 0) { mockBatchUpdateManager.addUpdate(tags = any()) }
    }

    @Test
    public fun testEditSubscriptions(): TestResult = runTest {
        channel.editSubscriptionLists().subscribe("some list").apply()
        verify { mockJobDispatcher.dispatch(keepJob) }
        verify {
            mockBatchUpdateManager.addUpdate(
                subscriptions = listOf(
                    SubscriptionListMutation.newSubscribeMutation("some list", testClock.currentTimeMillis)
                )
            )
        }
    }

    @Test
    public fun testEditSubscriptionsFeatureDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        channel.editSubscriptionLists().subscribe("some list").apply()
        verify(exactly = 0) { mockBatchUpdateManager.addUpdate(subscriptions = any()) }
    }

    @Test
    public fun testEditSubscriptionsEmpty(): TestResult = runTest {
        channel.editAttributes().apply()
        verify(exactly = 0) { mockBatchUpdateManager.addUpdate(subscriptions = any()) }
    }

    @Test
    public fun testEditAttributes(): TestResult = runTest {
        channel.editAttributes().removeAttribute("some attribute").apply()
        verify { mockJobDispatcher.dispatch(keepJob) }
        verify {
            mockBatchUpdateManager.addUpdate(
                attributes = listOf(
                    AttributeMutation.newRemoveAttributeMutation("some attribute", testClock.currentTimeMillis)
                )
            )
        }
    }

    @Test
    public fun testEditAttributesFeatureDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        channel.editAttributes().removeAttribute("some attribute").apply()
        verify(exactly = 0) { mockBatchUpdateManager.addUpdate(attributes = any()) }
    }

    @Test
    public fun testEditAttributesEmpty(): TestResult = runTest {
        channel.editAttributes().apply()
        verify(exactly = 0) { mockBatchUpdateManager.addUpdate(attributes = any()) }
    }

    @Test
    public fun testFetchSubscriptionLists(): TestResult = runTest {
        val result = Result.success(setOf("one", "two", "three"))
        coEvery {
            mockSubscriptions.fetchSubscriptionLists("some channel")
        } returns result

        channelIdFlow.tryEmit("some channel")
        assertEquals(result, channel.fetchSubscriptionLists())
    }

    @Test
    public fun testFetchSubscriptionListsFeatureDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        assertTrue(channel.fetchSubscriptionLists().isFailure)
    }

    @Test
    public fun testCraPayloadAndroid(): TestResult = runTest {
        every {
            mockLocaleManager.locale
        } returns Locale("shyriiwook", "KASHYYYK")

        channel.tags = setOf("cool_tag")

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setLanguage("shyriiwook")
            .setCountry("KASHYYYK")
            .setDeviceType("android")
            .setTags(true, setOf("cool_tag"))
            .setTimezone(TimeZone.getDefault().id)
            .setAppVersion(UAirship.getPackageInfo()!!.versionName)
            .setDeviceModel(Build.MODEL)
            .setApiVersion(Build.VERSION.SDK_INT)
            .setCarrier(tm.networkOperatorName)
            .setSdkVersion(UAirship.getVersion())
            .setIsActive(false)
            .build()

        var builder = ChannelRegistrationPayload.Builder()
        channelPayloadExtenders.forEach { builder = it.extend(builder) }
        assertEquals(expectedPayload, builder.build())
    }

    @Test
    public fun testCraPayloadAmazon(): TestResult = runTest {
        testConfig.platform = UAirship.AMAZON_PLATFORM

        every {
            mockLocaleManager.locale
        } returns Locale("en", "US")

        channel.tags = setOf("cool_tag")

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setLanguage("en")
            .setCountry("US")
            .setDeviceType("amazon")
            .setTags(true, setOf("cool_tag"))
            .setTimezone(TimeZone.getDefault().id)
            .setAppVersion(UAirship.getPackageInfo()!!.versionName)
            .setDeviceModel(Build.MODEL)
            .setApiVersion(Build.VERSION.SDK_INT)
            .setCarrier(tm.networkOperatorName)
            .setSdkVersion(UAirship.getVersion())
            .setIsActive(false)
            .build()

        var builder = ChannelRegistrationPayload.Builder()
        channelPayloadExtenders.forEach { builder = it.extend(builder) }
        assertEquals(expectedPayload, builder.build())
    }

    @Test
    public fun testCraPayloadAnalyticsDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS)

        every {
            mockLocaleManager.locale
        } returns Locale("en", "US")

        channel.tags = setOf("cool_tag")

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setLanguage("en")
            .setCountry("US")
            .setDeviceType("android")
            .setTags(true, setOf("cool_tag"))
            .setTimezone(TimeZone.getDefault().id)
            .setSdkVersion(UAirship.getVersion())
            .setIsActive(false)
            .build()

        var builder = ChannelRegistrationPayload.Builder()
        channelPayloadExtenders.forEach { builder = it.extend(builder) }
        assertEquals(expectedPayload, builder.build())
    }

    @Test
    public fun testCraPayloadAllFeaturesDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.FEATURE_ALL)

        every {
            mockLocaleManager.locale
        } returns Locale("en", "US")

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setDeviceType("android")
            .setTags(true, emptySet())
            .setIsActive(false)
            .build()

        var builder = ChannelRegistrationPayload.Builder()
        channelPayloadExtenders.forEach { builder = it.extend(builder) }
        assertEquals(expectedPayload, builder.build())
    }

    @Test
    public fun testCraPayloadActive(): TestResult = runTest {
        testActivityMonitor.foreground()

        every {
            mockLocaleManager.locale
        } returns Locale("en", "US")

        channel.tags = setOf("cool_tag")

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setLanguage("en")
            .setIsActive(true)
            .setCountry("US")
            .setDeviceType("android")
            .setTags(true, setOf("cool_tag"))
            .setTimezone(TimeZone.getDefault().id)
            .setAppVersion(UAirship.getPackageInfo()!!.versionName)
            .setDeviceModel(Build.MODEL)
            .setApiVersion(Build.VERSION.SDK_INT)
            .setCarrier(tm.networkOperatorName)
            .setSdkVersion(UAirship.getVersion())
            .build()

        var builder = ChannelRegistrationPayload.Builder()
        channelPayloadExtenders.forEach { builder = it.extend(builder) }
        assertEquals(expectedPayload, builder.build())
    }

    @Test
    public fun testDispatchDeviceUrlNull(): TestResult = runTest {
        testConfig.urlConfig = AirshipUrlConfig.newBuilder().build()
        channel.updateRegistration()
        verify(exactly = 0) { mockJobDispatcher.dispatch(any()) }
    }

    @Test
    public fun testPerformUpdate(): TestResult = runTest {
        every { mockRegistrar.channelId } returns "some channel id"
        coEvery { mockRegistrar.updateRegistration() } returns RegistrationResult.SUCCESS
        coEvery { mockBatchUpdateManager.uploadPending("some channel id") } returns true
        coEvery { mockBatchUpdateManager.hasPending } returns false

        assertEquals(JobResult.SUCCESS, channel.onPerformJob(mockk(), replaceJob))

        coVerify { mockBatchUpdateManager.uploadPending("some channel id") }
        coVerify { mockRegistrar.updateRegistration() }
    }

    @Test
    public fun testPerformUpdateChannelNeedsUpdate(): TestResult = runTest {
        every { mockRegistrar.channelId } returns "some channel id"
        coEvery { mockRegistrar.updateRegistration() } returns RegistrationResult.NEEDS_UPDATE
        coEvery { mockBatchUpdateManager.uploadPending("some channel id") } returns true
        coEvery { mockBatchUpdateManager.hasPending } returns false

        assertEquals(JobResult.SUCCESS, channel.onPerformJob(mockk(), keepJob))

        coVerify { mockBatchUpdateManager.uploadPending("some channel id") }
        coVerify { mockRegistrar.updateRegistration() }

        verify { mockJobDispatcher.dispatch(replaceJob) }
    }

    @Test
    public fun testPerformUpdateHasPending(): TestResult = runTest {
        every { mockRegistrar.channelId } returns "some channel id"
        coEvery { mockRegistrar.updateRegistration() } returns RegistrationResult.NEEDS_UPDATE
        coEvery { mockBatchUpdateManager.uploadPending("some channel id") } returns true
        coEvery { mockBatchUpdateManager.hasPending } returns true

        assertEquals(JobResult.SUCCESS, channel.onPerformJob(mockk(), keepJob))

        coVerify { mockBatchUpdateManager.uploadPending("some channel id") }
        coVerify { mockRegistrar.updateRegistration() }

        verify { mockJobDispatcher.dispatch(replaceJob) }
    }

    @Test
    public fun testPerformRegistrationFailed(): TestResult = runTest {
        every { mockRegistrar.channelId } returns "some channel id"
        coEvery { mockRegistrar.updateRegistration() } returns RegistrationResult.FAILED

        assertEquals(JobResult.FAILURE, channel.onPerformJob(mockk(), keepJob))

        coVerify(exactly = 0) { mockBatchUpdateManager.uploadPending("some channel id") }
        coVerify { mockRegistrar.updateRegistration() }
    }

    @Test
    public fun testPerformBatchUploadFailed(): TestResult = runTest {
        every { mockRegistrar.channelId } returns "some channel id"
        coEvery { mockRegistrar.updateRegistration() } returns RegistrationResult.SUCCESS
        coEvery { mockBatchUpdateManager.uploadPending("some channel id") } returns false
        coEvery { mockBatchUpdateManager.hasPending } returns false

        assertEquals(JobResult.FAILURE, channel.onPerformJob(mockk(), keepJob))

        coVerify { mockBatchUpdateManager.uploadPending("some channel id") }
        coVerify { mockRegistrar.updateRegistration() }
    }
}
