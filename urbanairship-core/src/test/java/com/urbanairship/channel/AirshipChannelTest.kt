/* Copyright Airship and Contributors */
package com.urbanairship.channel

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestClock
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.locale.LocaleManager
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import com.urbanairship.util.AutoRefreshingDataProvider
import com.urbanairship.util.LocaleCompat
import java.util.Locale
import java.util.TimeZone
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class AirshipChannelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferenceDataStore = PreferenceDataStore.inMemoryStore(context)

    private val localeChange = MutableSharedFlow<Locale>()

    private val channelIdFlow = MutableStateFlow<String?>(null)

    private var testConfig: TestAirshipRuntimeConfig = TestAirshipRuntimeConfig(
        RemoteConfig(
            airshipConfig = RemoteAirshipConfig(
                deviceApiUrl = "https://example.com"
            )
        )
    )
    private val privacyManager = PrivacyManager(preferenceDataStore, PrivacyManager.Feature.ALL, dispatcher = UnconfinedTestDispatcher())
    private val testDispatcher = StandardTestDispatcher()
    private val mockLocaleManager = mockk<LocaleManager>() {
        every { localeUpdates } returns localeChange
    }

    private val mockRegistrar = mockk<ChannelRegistrar>(relaxed = true) {
        every { this@mockk.channelIdFlow } returns this@AirshipChannelTest.channelIdFlow
        every { this@mockk.channelId } returns null
    }

    private val mockBatchUpdateManager = mockk<ChannelBatchUpdateManager>(relaxed = true)
    private val mockJobDispatcher = mockk<JobDispatcher>(relaxed = true)
    private val mockSubscriptions = mockk<ChannelSubscriptions>(relaxed = false)
    private val subscriptionsProviderFlow = MutableSharedFlow<AutoRefreshingDataProvider.IdentifiableResult<Set<String>>>(replay = 1)
    private val mockSubscriptionsProvider = mockk<SubscriptionsProvider>(relaxed = false) {
        coEvery {this@mockk.updates} returns subscriptionsProviderFlow
    }
    private val mockPermissionsManager: PermissionsManager = mockk()
    private var configuredPermissions = mapOf<Permission, PermissionStatus>()

    private val testClock = TestClock()
    private val testActivityMonitor = TestActivityMonitor()

    private val replaceJob = JobInfo.newBuilder()
        .setAction("ACTION_UPDATE_CHANNEL")
        .setNetworkAccessRequired(true)
        .setScope(AirshipChannel::class.java.name)
        .setConflictStrategy(JobInfo.ConflictStrategy.REPLACE)
        .build()

    private val keepJob = JobInfo.newBuilder()
        .setAction("ACTION_UPDATE_CHANNEL")
        .setNetworkAccessRequired(true)
        .setScope(AirshipChannel::class.java.name)
        .setConflictStrategy(JobInfo.ConflictStrategy.KEEP)
        .build()

    private val channel = AirshipChannel(
        context,
        preferenceDataStore,
        testConfig,
        privacyManager,
        mockPermissionsManager,
        mockLocaleManager,
        mockBatchUpdateManager,
        mockRegistrar,
        mockSubscriptionsProvider,
        testActivityMonitor,
        mockJobDispatcher,
        testClock,
        testDispatcher
    )

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { mockPermissionsManager.configuredPermissions } answers {
            configuredPermissions.keys
        }

        coEvery { mockPermissionsManager.checkPermissionStatus(any()) } answers {
            configuredPermissions[firstArg()] ?: PermissionStatus.NOT_DETERMINED
        }

        coEvery { mockRegistrar.payloadBuilder } coAnswers {
            { channel.buildCraPayload() }
        }
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
        localeChange.emit(Locale.ENGLISH)
        verify { mockJobDispatcher.dispatch(keepJob) }
    }

    @Test
    public fun testUrlConfigUpdatedUpdatesRegistration(): TestResult = runTest {
        testConfig.updateRemoteConfig(RemoteConfig(airshipConfig = RemoteAirshipConfig(
            deviceApiUrl = "https://somethingelse.com"
        )))
        verify { mockJobDispatcher.dispatch(keepJob) }
    }

    @Test
    public fun testPrivacyManagerChangeUpdatesRegistration(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.Feature.ANALYTICS)
        verify { mockJobDispatcher.dispatch(keepJob) }
    }

    @Test
    public fun testDisableTagsClearsPendingUpdates(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
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

        privacyManager.disable(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
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
        privacyManager.disable(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
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
    public fun testEditTagsClosure(): TestResult = runTest {
        channel.editTags { addTag("foo") }
        verify { mockJobDispatcher.dispatch(keepJob) }
        assertEquals(channel.tags, setOf("foo"))
    }

    @Test
    public fun testEditTagsFeatureDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
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
    public fun testEditTagGroupsClosure(): TestResult = runTest {
        channel.editTagGroups { addTags("some group", setOf("foo")) }
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
        privacyManager.disable(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
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
    public fun testEditSubscriptionsClosure(): TestResult = runTest {
        channel.editSubscriptionLists { subscribe("some list") }
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
        privacyManager.disable(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
        channel.editSubscriptionLists().subscribe("some list").apply()
        verify(exactly = 0) { mockBatchUpdateManager.addUpdate(subscriptions = any()) }
    }

    @Test
    public fun testEditSubscriptionsEmpty(): TestResult = runTest {
        channel.editAttributes().apply()
        verify(exactly = 0) { mockBatchUpdateManager.addUpdate(subscriptions = any()) }
    }

    @Test
    public fun testTrackLiveUpdate(): TestResult = runTest {
        val liveUpdateMutation = LiveUpdateMutation.Remove("name", testClock.currentTimeMillis)
        channel.trackLiveUpdateMutation(liveUpdateMutation)
        verify { mockJobDispatcher.dispatch(keepJob) }
        verify {
            mockBatchUpdateManager.addUpdate(
                liveUpdates = listOf(
                    liveUpdateMutation
                )
            )
        }
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
    public fun testEditAttributesClosure(): TestResult = runTest {
        channel.editAttributes { removeAttribute("some attribute") }
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
        privacyManager.disable(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
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

        subscriptionsProviderFlow.tryEmit(
            AutoRefreshingDataProvider.IdentifiableResult(
                identifier = "some channel",
                data = Result.success(setOf("one", "two", "three"))
            )
        )
        channelIdFlow.tryEmit("some channel")

        assertEquals(result, channel.fetchSubscriptionLists())
    }

    private val versionName: String? = context.packageManager.getPackageInfo(context.packageName, 0)?.versionName

    @Test
    public fun testCraPayloadAndroid(): TestResult = runTest {
        every {
            mockLocaleManager.locale
        } returns LocaleCompat.of("shyriiwook", "KASHYYYK")

        configuredPermissions = mapOf(
            Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.DENIED,
            Permission.LOCATION to PermissionStatus.GRANTED
        )

        channel.tags = setOf("cool_tag")

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setLanguage("shyriiwook")
            .setCountry("KASHYYYK")
            .setDeviceType(ChannelRegistrationPayload.DeviceType.ANDROID)
            .setTags(true, setOf("cool_tag"))
            .setTimezone(TimeZone.getDefault().id)
            .setAppVersion(versionName)
            .setDeviceModel(Build.MODEL)
            .setApiVersion(Build.VERSION.SDK_INT)
            .setSdkVersion(Airship.version)
            .setIsActive(false)
            .setPermissions(mapOf(
                "location" to "granted",
                "display_notifications" to "denied"
            ))
            .build()

        val payload = channel.buildCraPayload()
        assertEquals(expectedPayload, payload)
    }

    @Test
    public fun testCraPayloadAmazon(): TestResult = runTest {
        testConfig.setPlatform(Platform.AMAZON)

        every {
            mockLocaleManager.locale
        } returns LocaleCompat.of("en", "US")

        channel.tags = setOf("cool_tag")

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setLanguage("en")
            .setCountry("US")
            .setDeviceType(ChannelRegistrationPayload.DeviceType.AMAZON)
            .setTags(true, setOf("cool_tag"))
            .setTimezone(TimeZone.getDefault().id)
            .setAppVersion(versionName)
            .setDeviceModel(Build.MODEL)
            .setApiVersion(Build.VERSION.SDK_INT)
            .setSdkVersion(Airship.version)
            .setIsActive(false)
            .build()

        val payload = channel.buildCraPayload()
        assertEquals(expectedPayload, payload)
    }

    @Test
    public fun testCraPayloadAnalyticsDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.Feature.ANALYTICS)

        every {
            mockLocaleManager.locale
        } returns LocaleCompat.of("en", "US")

        channel.tags = setOf("cool_tag")

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setLanguage("en")
            .setCountry("US")
            .setDeviceType(ChannelRegistrationPayload.DeviceType.ANDROID)
            .setTags(true, setOf("cool_tag"))
            .setTimezone(TimeZone.getDefault().id)
            .setSdkVersion(Airship.version)
            .setIsActive(false)
            .build()

        val payload = channel.buildCraPayload()
        assertEquals(expectedPayload, payload)
    }

    @Test
    public fun testCraPayloadAllFeaturesDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.Feature.ALL)

        every {
            mockLocaleManager.locale
        } returns LocaleCompat.of("en", "US")

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setDeviceType(ChannelRegistrationPayload.DeviceType.ANDROID)
            .setTags(true, emptySet())
            .setIsActive(false)
            .build()

        val payload = channel.buildCraPayload()
        assertEquals(expectedPayload, payload)
    }

    @Test
    public fun testCraPayloadTagsDisabled(): TestResult = runTest {
        every {
            mockLocaleManager.locale
        } returns LocaleCompat.of("shyriiwook", "KASHYYYK")

        configuredPermissions = mapOf(
            Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.DENIED,
            Permission.LOCATION to PermissionStatus.GRANTED
        )

        privacyManager.disable(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
        channel.tags = setOf("cool_tag")

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setLanguage("shyriiwook")
            .setCountry("KASHYYYK")
            .setDeviceType(ChannelRegistrationPayload.DeviceType.ANDROID)
            .setTags(true, emptySet())
            .setTimezone(TimeZone.getDefault().id)
            .setAppVersion(versionName)
            .setDeviceModel(Build.MODEL)
            .setApiVersion(Build.VERSION.SDK_INT)
            .setSdkVersion(Airship.version)
            .setIsActive(false)
            .build()

        val payload = channel.buildCraPayload()
        assertEquals(expectedPayload, payload)
    }

    @Test
    public fun testCraPayloadActive(): TestResult = runTest {
        testActivityMonitor.foreground()

        every {
            mockLocaleManager.locale
        } returns LocaleCompat.of("en", "US")

        channel.tags = setOf("cool_tag")

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setLanguage("en")
            .setIsActive(true)
            .setCountry("US")
            .setDeviceType(ChannelRegistrationPayload.DeviceType.ANDROID)
            .setTags(true, setOf("cool_tag"))
            .setTimezone(TimeZone.getDefault().id)
            .setAppVersion(versionName)
            .setDeviceModel(Build.MODEL)
            .setApiVersion(Build.VERSION.SDK_INT)
            .setSdkVersion(Airship.version)
            .build()

        val payload = channel.buildCraPayload()
        assertEquals(expectedPayload, payload)
    }

    @Test
    public fun testCraPayloadMinify(): TestResult = runTest {
        every {
            mockLocaleManager.locale
        } returns LocaleCompat.of("shyriiwook", "KASHYYYK")

        configuredPermissions = mapOf(
            Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.DENIED,
            Permission.LOCATION to PermissionStatus.GRANTED
        )

        channel.tags = setOf("cool_tag")

        val expectedPayload = ChannelRegistrationPayload.Builder()
            .setLanguage("shyriiwook")
            .setCountry("KASHYYYK")
            .setDeviceType(ChannelRegistrationPayload.DeviceType.ANDROID)
            .setTags(true, setOf("cool_tag"))
            .setTimezone(TimeZone.getDefault().id)
            .setAppVersion(versionName)
            .setDeviceModel(Build.MODEL)
            .setApiVersion(Build.VERSION.SDK_INT)
            .setSdkVersion(Airship.version)
            .setIsActive(false)
            .setPermissions(mapOf(
                "location" to "granted",
                "display_notifications" to "denied"
            ))
            .build()

        val payload = channel.buildCraPayload()
        assertEquals(expectedPayload, payload)

        configuredPermissions = mapOf(
            Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.GRANTED,
            Permission.LOCATION to PermissionStatus.GRANTED
        )

        val minimized = channel.buildCraPayload().minimizedPayload(payload)
        val expectedMinimized = ChannelRegistrationPayload.Builder()
            .setDeviceType(ChannelRegistrationPayload.DeviceType.ANDROID)
            .setPermissions(mapOf(
                "location" to "granted",
                "display_notifications" to "granted"
            ))
            .build()

        assertEquals(expectedMinimized, minimized)
    }

    @Test
    public fun testDispatchDeviceUrlNull(): TestResult = runTest {
        testConfig.updateRemoteConfig(RemoteConfig())
        channel.updateRegistration()
        verify(exactly = 0) { mockJobDispatcher.dispatch(any()) }
    }

    @Test
    public fun testPerformUpdate(): TestResult = runTest {
        every { mockRegistrar.channelId } returns "some channel id"
        coEvery { mockRegistrar.updateRegistration() } returns RegistrationResult.SUCCESS
        coEvery { mockBatchUpdateManager.uploadPending("some channel id") } returns true
        coEvery { mockBatchUpdateManager.hasPending } returns false

        assertEquals(JobResult.SUCCESS, channel.onPerformJob( replaceJob))

        coVerify { mockBatchUpdateManager.uploadPending("some channel id") }
        coVerify { mockRegistrar.updateRegistration() }
    }

    @Test
    public fun testPerformUpdateChannelNeedsUpdate(): TestResult = runTest {
        every { mockRegistrar.channelId } returns "some channel id"
        coEvery { mockRegistrar.updateRegistration() } returns RegistrationResult.NEEDS_UPDATE
        coEvery { mockBatchUpdateManager.uploadPending("some channel id") } returns true
        coEvery { mockBatchUpdateManager.hasPending } returns false

        assertEquals(JobResult.SUCCESS, channel.onPerformJob(keepJob))

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

        assertEquals(JobResult.SUCCESS, channel.onPerformJob(keepJob))

        coVerify { mockBatchUpdateManager.uploadPending("some channel id") }
        coVerify { mockRegistrar.updateRegistration() }

        verify { mockJobDispatcher.dispatch(replaceJob) }
    }

    @Test
    public fun testPerformRegistrationFailed(): TestResult = runTest {
        every { mockRegistrar.channelId } returns "some channel id"
        coEvery { mockRegistrar.updateRegistration() } returns RegistrationResult.FAILED

        assertEquals(JobResult.FAILURE, channel.onPerformJob(keepJob))

        coVerify(exactly = 0) { mockBatchUpdateManager.uploadPending("some channel id") }
        coVerify { mockRegistrar.updateRegistration() }
    }

    @Test
    public fun testPerformBatchUploadFailed(): TestResult = runTest {
        every { mockRegistrar.channelId } returns "some channel id"
        coEvery { mockRegistrar.updateRegistration() } returns RegistrationResult.SUCCESS
        coEvery { mockBatchUpdateManager.uploadPending("some channel id") } returns false
        coEvery { mockBatchUpdateManager.hasPending } returns false

        assertEquals(JobResult.FAILURE, channel.onPerformJob(keepJob))

        coVerify { mockBatchUpdateManager.uploadPending("some channel id") }
        coVerify { mockRegistrar.updateRegistration() }
    }
}
