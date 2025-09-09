/* Copyright Airship and Contributors */
package com.urbanairship.push

import androidx.core.os.bundleOf
import androidx.core.util.Consumer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.PushProviders
import com.urbanairship.R
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestApplication
import com.urbanairship.Airship
import com.urbanairship.analytics.Analytics
import com.urbanairship.base.Supplier
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.ChannelRegistrationPayload
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.permission.OnPermissionStatusChangedListener
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionPromptFallback
import com.urbanairship.permission.PermissionRequestResult
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.push.notifications.NotificationActionButtonGroup
import app.cash.turbine.test
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [PushManager].
 */
@RunWith(AndroidJUnit4::class)
public class PushManagerTest {

    private var preferenceDataStore = PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext())
    private var privacyManager = PrivacyManager(preferenceDataStore, PrivacyManager.Feature.ALL)
    private val runtimeConfig = TestAirshipRuntimeConfig()

    private val mockPushProvider: PushProvider = mockk(relaxed = true) {
        every { deliveryType } returns PushProvider.DeliveryType.FCM
    }
    private val mockPushProviders: PushProviders = mockk {
        every { getBestProvider(any()) } returns mockPushProvider
        every { getProvider(any(), any()) } returns mockPushProvider
    }
    private val mockDispatcher: JobDispatcher = mockk(relaxed = true)
    private val mockAirshipChannel: AirshipChannel = mockk(relaxed = true)

    private val mockAnalytics: Analytics = mockk(relaxed = true)
    private var notificationStatus = PermissionStatus.NOT_DETERMINED
    private val mockPermissionManager: PermissionsManager = mockk(relaxed = true) {
        coEvery { suspendingCheckPermissionStatus(any()) } answers { notificationStatus }
    }
    private val pushProvidersSupplier = Supplier<PushProviders> { mockPushProviders }
    private val mockNotificationManager: AirshipNotificationManager = mockk {
        every { areNotificationsEnabled() } returns false
    }
    private val activityMonitor = TestActivityMonitor()

    @OptIn(ExperimentalCoroutinesApi::class)
    private var pushManager = PushManager(
        TestApplication.getApplication(),
        preferenceDataStore,
        runtimeConfig,
        privacyManager,
        pushProvidersSupplier,
        mockAirshipChannel,
        mockAnalytics,
        mockPermissionManager,
        mockDispatcher,
        mockNotificationManager,
        activityMonitor,
        dispatcher = UnconfinedTestDispatcher()
    )

    /**
     * Test init starts push registration if the registration token is not available.
     */
    @Test
    public fun testInit() {
        every { mockDispatcher.dispatch(any()) } answers {
            val job: JobInfo = firstArg()
            Assert.assertTrue(job.action == PushManager.ACTION_UPDATE_PUSH_REGISTRATION)
        }
        pushManager.init()

        verify { mockDispatcher.dispatch(any()) }
    }

    /**
     * Test delivery type changes will clear the previous token.
     */
    @Test
    public fun testInitClearsPushTokenOnDeliveryChange() {
        // Register for a token
        pushManager.init()
        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"

        pushManager.performPushRegistration(true)
        Assert.assertEquals("token", pushManager.pushToken)

        // Init to verify token does not clear if the delivery type is the same
        pushManager = PushManager(
            TestApplication.getApplication(),
            preferenceDataStore!!,
            runtimeConfig,
            privacyManager,
            pushProvidersSupplier,
            mockAirshipChannel,
            mockAnalytics,
            mockPermissionManager,
            mockDispatcher,
            mockNotificationManager,
            activityMonitor,
        )
        pushManager.init()
        Assert.assertEquals("token", pushManager.pushToken)

        // Change the delivery type, should clear the token on init
        every { mockPushProvider.deliveryType } returns PushProvider.DeliveryType.ADM
        pushManager = PushManager(
            TestApplication.getApplication(),
            preferenceDataStore,
            runtimeConfig,
            privacyManager,
            pushProvidersSupplier,
            mockAirshipChannel,
            mockAnalytics,
            mockPermissionManager,
            mockDispatcher,
            mockNotificationManager,
            activityMonitor
        )
        pushManager.init()
        Assert.assertNull(pushManager.pushToken)
    }

    /**
     * Test on registering for a push token.
     */
    @Test
    public fun testPushRegistration() {
        pushManager.init()
        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"
        pushManager.performPushRegistration(true)
        Assert.assertEquals("token", pushManager.pushToken)

        verify { mockAirshipChannel.updateRegistration() }
    }

    /**
     * Test push provider unavailable exceptions keep the token.
     */
    @Test
    public fun testPushProviderUnavailableException() {
        pushManager.init()
        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"
        pushManager.performPushRegistration(true)
        Assert.assertEquals("token", pushManager.pushToken)

        every { mockPushProvider.getRegistrationToken(any()) } throws PushProvider.PushProviderUnavailableException("test")
        pushManager.performPushRegistration(true)
        Assert.assertEquals("token", pushManager.pushToken)
    }

    /**
     * Test registration exceptions clear the token.
     */
    @Test
    public fun tesRegistrationException() {
        pushManager.init()
        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"
        pushManager.performPushRegistration(true)
        Assert.assertEquals("token", pushManager.pushToken)

        every { mockPushProvider.getRegistrationToken(any()) } throws PushProvider.RegistrationException("test", true)
        pushManager.performPushRegistration(true)
        Assert.assertNull(pushManager.pushToken)
    }

    /**
     * Test OptIn is only true if push and notifications are enabled and we have a push token.
     */
    @Test
    public fun testOptIn() {
        pushManager.init()

        // Enable and have permission
        every { mockNotificationManager.areNotificationsEnabled() } returns true
        pushManager.userNotificationsEnabled = true
        privacyManager.enable(PrivacyManager.Feature.PUSH)

        // Still needs a token
        Assert.assertFalse(pushManager.isOptIn)

        // Register for a token
        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"
        pushManager.performPushRegistration(true)

        Assert.assertTrue(pushManager.isOptIn)

        // Disable notifications
        pushManager.userNotificationsEnabled = false
        Assert.assertFalse(pushManager.isOptIn)

        pushManager.userNotificationsEnabled = true

        // Disable push privacy manager
        privacyManager.disable(PrivacyManager.Feature.PUSH)
        Assert.assertFalse(pushManager.isOptIn)

        privacyManager.enable(PrivacyManager.Feature.PUSH)

        // Revoke permission
        every { mockNotificationManager.areNotificationsEnabled() } returns false
        Assert.assertFalse(pushManager.isOptIn)
    }

    @Test
    public fun testGetPushProviderType() {
        pushManager.init()

        var deliveryType = PushProvider.DeliveryType.FCM
        every { mockPushProvider.deliveryType } answers { deliveryType }
        // FCM
        deliveryType = PushProvider.DeliveryType.FCM
        Assert.assertEquals(PushProviderType.FCM, pushManager.pushProviderType)

        // ADM
        deliveryType = PushProvider.DeliveryType.ADM
        Assert.assertEquals(PushProviderType.ADM, pushManager.pushProviderType)

        // HMS
        deliveryType = PushProvider.DeliveryType.HMS
        Assert.assertEquals(PushProviderType.HMS, pushManager.pushProviderType)
    }

    @Test
    public fun testGetPushProviderTypeNoProvider() {
        every { mockPushProviders.getBestProvider(any()) } returns null

        pushManager.init()

        Assert.assertEquals(PushProviderType.NONE, pushManager.pushProviderType)
    }

    /**
     * Test Airship notification action button groups are available
     */
    @Test
    public fun testUrbanAirshipNotificationActionButtonGroups() {
        val keys = ActionButtonGroupsParser.fromXml(
            ApplicationProvider.getApplicationContext(), R.xml.ua_notification_buttons
        ).keys
        Assert.assertTrue(keys.size > 0)

        for (key in keys) {
            Assert.assertNotNull(
                "Missing notification button group with ID: $key",
                pushManager.getNotificationActionGroup(key)
            )
        }
    }

    /**
     * Test trying to add a notification action button group with the reserved prefix
     */
    @Test
    public fun testAddingNotificationActionButtonGroupWithReservedPrefix() {
        pushManager.addNotificationActionButtonGroup(
            "ua_my_test_id", NotificationActionButtonGroup.newBuilder().build()
        )
        Assert.assertNull(
            "Should not be able to add groups with prefix ua_",
            pushManager.getNotificationActionGroup("ua_my_test_id")
        )
    }

    /**
     * Test trying to remove a notification action button group with the reserved prefix
     */
    @Test
    public fun testRemovingNotificationActionButtonGroupWithReservedPrefix() {
        val keys = ActionButtonGroupsParser.fromXml(
            ApplicationProvider.getApplicationContext(), R.xml.ua_notification_buttons
        ).keys

        for (key in keys) {
            pushManager.removeNotificationActionButtonGroup(key)
            Assert.assertNotNull(
                "Should not be able to remove notification button group with ID: $key",
                pushManager.getNotificationActionGroup(key)
            )
        }
    }

    /**
     * Test channel registration extender when push is opted in.
     */
    @Test
    public fun testChannelRegistrationExtenderOptedIn() {
        var extender: AirshipChannel.Extender.Blocking? = null
        every { mockAirshipChannel.addChannelRegistrationPayloadExtender(any()) } answers {
            extender = firstArg()
            Assert.assertNotNull(extender)
        }
        pushManager.init()

        verify { mockAirshipChannel.addChannelRegistrationPayloadExtender(any())}

        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"
        pushManager.performPushRegistration(true)
        pushManager.userNotificationsEnabled = true
        every { mockNotificationManager.areNotificationsEnabled() } returns true

        every { mockPushProvider.platform } returns Airship.Platform.ANDROID

        val builder = ChannelRegistrationPayload.Builder()
        val payload = extender?.extend(builder)?.build()

        val expected = ChannelRegistrationPayload.Builder()
            .setBackgroundEnabled(true)
            .setOptIn(true)
            .setPushAddress("token")
            .setDeliveryType(PushProvider.DeliveryType.FCM)
            .build()

        Assert.assertEquals(expected, payload)
    }

    /**
     * Test channel registration extender when push is opted out.
     */
    @Test
    public fun testChannelRegistrationExtenderOptedOut() {

        var extender: AirshipChannel.Extender.Blocking? = null
        every { mockAirshipChannel.addChannelRegistrationPayloadExtender(any()) } answers {
            extender = firstArg()
            Assert.assertNotNull(extender)
        }
        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns null

        pushManager.init()
        verify { mockAirshipChannel.addChannelRegistrationPayloadExtender(any()) }
        Assert.assertNotNull(extender)

        val builder = ChannelRegistrationPayload.Builder()
        val payload = extender?.extend(builder)?.build()

        val expected = ChannelRegistrationPayload.Builder()
            .setBackgroundEnabled(false)
            .setOptIn(false)
            .build()

        Assert.assertEquals(expected, payload)
    }

    @Test
    public fun testDeliveryTypeAndroidPlatform() {
        var extender: AirshipChannel.Extender.Blocking? = null
        every { mockAirshipChannel.addChannelRegistrationPayloadExtender(any()) } answers {
            extender = firstArg()
        }
        pushManager.init()
        verify { mockAirshipChannel.addChannelRegistrationPayloadExtender(any()) }
        Assert.assertNotNull(extender)

        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.platform } returns Airship.Platform.ANDROID
        every { mockPushProvider.deliveryType } returns PushProvider.DeliveryType.FCM
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"

        val builder = ChannelRegistrationPayload.Builder()

        val payload = extender?.extend(builder)?.build()

        val expected = ChannelRegistrationPayload.Builder()
            .setPushAddress("token")
            .setDeliveryType(PushProvider.DeliveryType.FCM)
            .setBackgroundEnabled(true)
            .build()

        Assert.assertEquals(expected, payload)
    }

    @Test
    public fun testAnalyticHeaders() {
        var delegate: Analytics.AnalyticsHeaderDelegate? = null
        every { mockAnalytics.addHeaderDelegate(any()) } answers {
            delegate = firstArg()
        }
        pushManager.init()
        verify { mockAnalytics.addHeaderDelegate(any()) }
        Assert.assertNotNull(delegate)

        val expectedHeaders = mapOf(
            "X-UA-Channel-Opted-In" to "false",
            "X-UA-Channel-Background-Enabled" to "false"
        )

        val headers = delegate?.onCreateAnalyticsHeaders()
        Assert.assertEquals(expectedHeaders, headers)
    }

    @Test
    public fun testOnPushReceived() {
        val message = PushMessage(bundleOf())

        val internalPushListener: PushListener = mockk(relaxed = true)
        val pushListener: PushListener = mockk(relaxed = true)
        pushManager.addInternalPushListener(internalPushListener)
        pushManager.addPushListener(pushListener)

        pushManager.onPushReceived(message, true)
        pushManager.onPushReceived(message, false)

        verify { pushListener.onPushReceived(message, true) }
        verify { internalPushListener.onPushReceived(message, true) }

        verify { pushListener.onPushReceived(message, false) }
        verify { internalPushListener.onPushReceived(message, false) }
    }

    @Test
    public fun testOnPushReceivedInternal() {
        val bundle = bundleOf(PushMessage.REMOTE_DATA_UPDATE_KEY to "true")
        val message = PushMessage(bundle)

        val internalPushListener: PushListener = mockk(relaxed = true)
        val pushListener: PushListener = mockk(relaxed = true)
        pushManager.addInternalPushListener(internalPushListener)
        pushManager.addPushListener(pushListener)

        pushManager.onPushReceived(message, false)
        verify { internalPushListener.onPushReceived(message, false) }
        verify { pushListener wasNot Called}
    }

    @Test
    public fun testOnNotificationPosted() {
        val bundle = bundleOf(PushMessage.REMOTE_DATA_UPDATE_KEY to "true")
        val message = PushMessage(bundle)

        val notificationListener: NotificationListener = mockk(relaxed = true) {
            every { onNotificationPosted(any()) } answers {
                val notificationInfo: NotificationInfo = firstArg()
                Assert.assertEquals(message, notificationInfo.message)
                Assert.assertEquals(100, notificationInfo.notificationId)
                Assert.assertEquals("neat", notificationInfo.notificationTag)
            }
        }
        pushManager.notificationListener = notificationListener

        pushManager.onNotificationPosted(message, 100, "neat")
        verify { notificationListener.onNotificationPosted(any()) }
    }

    @Test
    public fun testOnTokenChange() {
        pushManager.init()
        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"
        pushManager.performPushRegistration(true)
        Assert.assertEquals("token", pushManager.pushToken)
        verify { mockAirshipChannel.updateRegistration() }

        every { mockDispatcher.dispatch(any()) } answers {
            val info: JobInfo = firstArg()
            Assert.assertTrue(info.action == PushManager.ACTION_UPDATE_PUSH_REGISTRATION)
        }

        pushManager.onTokenChanged(mockPushProvider.javaClass, "some-other-token")
        Assert.assertNull(pushManager.pushToken)
        verify { mockDispatcher.dispatch(any()) }
    }

    @Test
    public fun testOnTokenChangeSameToken() {
        pushManager.init()
        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"
        pushManager.performPushRegistration(true)
        Assert.assertEquals("token", pushManager.pushToken)
        verify { mockAirshipChannel.updateRegistration() }

        every { mockDispatcher.dispatch(any()) } answers {
            val info: JobInfo = firstArg()
            Assert.assertTrue(info.action == PushManager.ACTION_UPDATE_PUSH_REGISTRATION)
        }

        pushManager.onTokenChanged(mockPushProvider.javaClass, "token")
        Assert.assertEquals("token", pushManager.pushToken)
        verify { mockDispatcher.dispatch(any()) }
    }

    @Test
    public fun testOnTokenChangeLegacy() {
        pushManager.init()
        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"
        pushManager.performPushRegistration(true)
        Assert.assertEquals("token", pushManager.pushToken)
        verify { mockAirshipChannel.updateRegistration() }

        every { mockDispatcher.dispatch(any()) } answers {
            val info: JobInfo = firstArg()
            Assert.assertTrue(info.action == PushManager.ACTION_UPDATE_PUSH_REGISTRATION)
        }

        pushManager.onTokenChanged(null, null)
        Assert.assertEquals("token", pushManager.pushToken)
        verify { mockDispatcher.dispatch(any()) }
    }

    @Test
    public fun testPermissionEnabler() {
        var consumer: Consumer<Permission>? = null
        every { mockPermissionManager.addAirshipEnabler(any()) } answers {
            consumer = firstArg()
        }
        pushManager.init()
        verify { mockPermissionManager.addAirshipEnabler(any()) }

        Assert.assertNotNull(consumer)

        privacyManager.disable(PrivacyManager.Feature.PUSH)
        pushManager.userNotificationsEnabled = false

        consumer?.accept(Permission.DISPLAY_NOTIFICATIONS)

        Assert.assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.PUSH))
        Assert.assertTrue(pushManager.userNotificationsEnabled)
        verify { mockAirshipChannel.updateRegistration() }
    }

    @Test
    public fun testForegroundChecksPermission() {
        pushManager.init()
        pushManager.onAirshipReady()
        this.notificationStatus = PermissionStatus.NOT_DETERMINED
        pushManager.userNotificationsEnabled = true

        activityMonitor.foreground()
        coVerify { mockPermissionManager.suspendingCheckPermissionStatus(Permission.DISPLAY_NOTIFICATIONS) }
    }

    @Test
    public fun testEnableNotificationsChecksPermission() {
        activityMonitor.foreground()

        this.notificationStatus = PermissionStatus.NOT_DETERMINED
        pushManager.userNotificationsEnabled = true
        coVerify { mockPermissionManager.suspendingCheckPermissionStatus(Permission.DISPLAY_NOTIFICATIONS) }
    }

    @Test
    public fun testEnableUserNotifications() {
        val consumer = TestConsumer<Boolean>()

        coEvery { mockPermissionManager.suspendingRequestPermission(
            permission = Permission.DISPLAY_NOTIFICATIONS,
            enableAirshipUsageOnGrant = false,
            fallback = PermissionPromptFallback.None
        ) } returns PermissionRequestResult.granted()

        pushManager.enableUserNotifications(consumer)

        Assert.assertTrue(consumer.lastResult == true)
        Assert.assertTrue(pushManager.userNotificationsEnabled)
    }

    @Test
    public fun testEnableUserNotificationsDenied() {
        val consumer = TestConsumer<Boolean>()

        coEvery { mockPermissionManager.suspendingRequestPermission(
            permission = Permission.DISPLAY_NOTIFICATIONS,
            enableAirshipUsageOnGrant = false,
            fallback = PermissionPromptFallback.None
        ) } returns PermissionRequestResult.denied(false)

        pushManager.enableUserNotifications(consumer)

        Assert.assertFalse(consumer.lastResult == true)
        Assert.assertTrue(pushManager.userNotificationsEnabled)
    }

    @Test
    public fun testEnableUserNotificationsFallback() {
        val consumer = TestConsumer<Boolean>()

        coEvery { mockPermissionManager.suspendingRequestPermission(
            permission = Permission.DISPLAY_NOTIFICATIONS,
            enableAirshipUsageOnGrant = false,
            fallback = PermissionPromptFallback.SystemSettings
        ) } returns PermissionRequestResult.granted()

        pushManager.enableUserNotifications(PermissionPromptFallback.SystemSettings, consumer)

        Assert.assertTrue(consumer.lastResult == true)
        Assert.assertTrue(pushManager.userNotificationsEnabled)
    }

    @Test
    public fun testPrivacyManagerEnablesNotifications() {
        pushManager.onAirshipReady()

        this.notificationStatus = PermissionStatus.NOT_DETERMINED

        pushManager.init()
        privacyManager.disable(PrivacyManager.Feature.PUSH)
        activityMonitor.foreground()
        pushManager.userNotificationsEnabled = true

        privacyManager.enable(PrivacyManager.Feature.PUSH)
        coVerify { mockPermissionManager.suspendingCheckPermissionStatus(Permission.DISPLAY_NOTIFICATIONS) }
    }

    @Test
    public fun testPermissionStatusChangesUpdatesChannelRegistration(): TestResult = runTest {

        val statusUpdates = MutableSharedFlow<Pair<Permission, PermissionStatus>>()
        every { mockPermissionManager.permissionStatusUpdates } returns statusUpdates

        pushManager.init()

        statusUpdates.emit(Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.DENIED)

        statusUpdates.emit(Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.GRANTED)
        statusUpdates.emit(Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.NOT_DETERMINED)
        statusUpdates.emit(Permission.LOCATION to PermissionStatus.GRANTED)

        verify(exactly = 3) { mockAirshipChannel.updateRegistration() }
    }

    @Test
    public fun testRequestPermissionWhenEnabled() {
        this.notificationStatus = PermissionStatus.DENIED

        pushManager.init()
        pushManager.onAirshipReady()

        privacyManager.enable(PrivacyManager.Feature.PUSH)
        activityMonitor.foreground()
        pushManager.userNotificationsEnabled = true

        clearMocks(mockPermissionManager, answers = false)

        pushManager.userNotificationsEnabled = true
        pushManager.userNotificationsEnabled = false
        pushManager.userNotificationsEnabled = true

        coVerify {
            mockPermissionManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS)
        }
    }

    @Test
    public fun testUpdateRegistrationWhenDisabled() {
        this.notificationStatus = PermissionStatus.DENIED
        pushManager.userNotificationsEnabled = true
        clearMocks(mockAirshipChannel)

        pushManager.userNotificationsEnabled = false
        verify { mockAirshipChannel.updateRegistration() }
    }

    @Test
    public fun testUpdateRegistrationAfterPrompt() {
        coEvery { mockPermissionManager.suspendingRequestPermission(any()) } returns PermissionRequestResult.granted()

        pushManager.init()
        privacyManager.enable(PrivacyManager.Feature.PUSH)
        activityMonitor.foreground()

        clearMocks(mockAirshipChannel)

        pushManager.userNotificationsEnabled = true
        verify { mockAirshipChannel.updateRegistration() }
    }

    @Test
    public fun testCheckPermissionAirshipReady() {
        this.notificationStatus = PermissionStatus.DENIED
        pushManager.init()
        privacyManager.enable(PrivacyManager.Feature.PUSH)
        activityMonitor.foreground()
        pushManager.userNotificationsEnabled = true
        clearMocks(mockPermissionManager, answers = false)

        coVerify(exactly = 0) { mockPermissionManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, any(), any()) }

        pushManager.onAirshipReady()

        coVerify(exactly = 1) { mockPermissionManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, any(), any()) }
    }

    @Test
    public fun testPromptNotificationPermissionOncePerEnable() {
        this.notificationStatus = PermissionStatus.DENIED
        pushManager.init()
        privacyManager.enable(PrivacyManager.Feature.PUSH)
        activityMonitor.foreground()
        pushManager.onAirshipReady()

        coVerify(exactly = 0) { mockPermissionManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, any(), any()) }

        pushManager.userNotificationsEnabled = true
        coVerify(exactly = 1) { mockPermissionManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, any(), any()) }

        activityMonitor.background()
        activityMonitor.foreground()

        coVerify(exactly = 1) { mockPermissionManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, any(), any()) }

        pushManager.userNotificationsEnabled = false
        pushManager.userNotificationsEnabled = true

        coVerify(exactly = 2) { mockPermissionManager.suspendingRequestPermission(Permission.DISPLAY_NOTIFICATIONS, any(), any()) }
    }

    @Test
    public fun testPromptNotificationPermissionDisabled() {
        runtimeConfig.setConfigOptions(
            AirshipConfigOptions.newBuilder()
                .setAppKey("appKey")
                .setAppSecret("appSecret")
                .setIsPromptForPermissionOnUserNotificationsEnabled(false)
                .build()
        )

        this.notificationStatus = PermissionStatus.DENIED
        pushManager.init()
        privacyManager.enable(PrivacyManager.Feature.PUSH)
        activityMonitor.foreground()
        pushManager.onAirshipReady()
        pushManager.userNotificationsEnabled = true
        verify(exactly = 0) { mockPermissionManager.requestPermission(Permission.DISPLAY_NOTIFICATIONS, any(), any(), any()) }
    }

    @Test
    public fun testPushStatus() {
        pushManager.init()
        privacyManager.enable(PrivacyManager.Feature.PUSH)
        pushManager.onAirshipReady()
        pushManager.userNotificationsEnabled = true

        every { mockNotificationManager.areNotificationsEnabled() } returns true
        every { mockPushProvider.isAvailable(any()) } returns true
        every { mockPushProvider.getRegistrationToken(any()) } returns "token"
        pushManager.performPushRegistration(true)

        Assert.assertEquals(
            PushNotificationStatus(
                isUserNotificationsEnabled = true,
                areNotificationsAllowed = true,
                isPushPrivacyFeatureEnabled = true,
                isPushTokenRegistered = true
            ), pushManager.pushNotificationStatus
        )
    }

    @Test
    public fun testPushStatusNoToken() {
        pushManager.init()
        privacyManager.enable(PrivacyManager.Feature.PUSH)
        pushManager.onAirshipReady()
        pushManager.userNotificationsEnabled = true

        every { mockNotificationManager.areNotificationsEnabled() } returns true

        Assert.assertEquals(
            PushNotificationStatus(
                isUserNotificationsEnabled = true,
                areNotificationsAllowed = true,
                isPushPrivacyFeatureEnabled = true,
                isPushTokenRegistered = false
            ), pushManager.pushNotificationStatus
        )
    }

    @Test
    public fun testPushStatusChangedListenerEnableNotifications() {
        val listener: PushNotificationStatusListener = mockk(relaxed = true)

        pushManager.userNotificationsEnabled = false
        pushManager.addNotificationStatusListener(listener)

        val consumer = TestConsumer<Boolean>()

        coEvery { mockPermissionManager.suspendingRequestPermission(
            permission = Permission.DISPLAY_NOTIFICATIONS,
            enableAirshipUsageOnGrant = false,
            fallback = PermissionPromptFallback.None
        ) } returns PermissionRequestResult.granted()

        pushManager.enableUserNotifications(consumer)

        Assert.assertTrue(consumer.lastResult == true)
        Assert.assertTrue(pushManager.userNotificationsEnabled)

        pushManager.enableUserNotifications { condition: Boolean? ->
            Assert.assertTrue(condition == true)
        }

        verify(exactly = 1) { listener.onChange(any()) }
    }

    @Test
    public fun testPushStatusChanges() {
        pushManager.init()
        privacyManager.enable(PrivacyManager.Feature.PUSH)
        pushManager.onAirshipReady()
        pushManager.userNotificationsEnabled = true

        var areNotificationsEnabled = true
        every { mockNotificationManager.areNotificationsEnabled() } answers { areNotificationsEnabled }

        val expected = PushNotificationStatus(
            isUserNotificationsEnabled = true,
            areNotificationsAllowed = true,
            isPushPrivacyFeatureEnabled = true,
            isPushTokenRegistered = false
        )
        Assert.assertEquals(expected, pushManager.pushNotificationStatus)

        areNotificationsEnabled = false
        Assert.assertEquals(
            PushNotificationStatus(
                isUserNotificationsEnabled = true,
                areNotificationsAllowed = false,
                isPushPrivacyFeatureEnabled = true,
                isPushTokenRegistered = false
            ), pushManager.pushNotificationStatus
        )

        areNotificationsEnabled = true
        pushManager.userNotificationsEnabled = false
        Assert.assertEquals(
            PushNotificationStatus(
                isUserNotificationsEnabled = false,
                areNotificationsAllowed = true,
                isPushPrivacyFeatureEnabled = true,
                isPushTokenRegistered = false
            ), pushManager.pushNotificationStatus
        )

        pushManager.userNotificationsEnabled = true
        privacyManager.disable(PrivacyManager.Feature.PUSH)
        Assert.assertEquals(
            PushNotificationStatus(
                isUserNotificationsEnabled = true,
                areNotificationsAllowed = true,
                isPushPrivacyFeatureEnabled = false,
                isPushTokenRegistered = false
            ), pushManager.pushNotificationStatus
        )
    }

    private class TestConsumer<T> : Consumer<T> {

        var lastResult: T? = null
        var results = mutableListOf<T>()

        override fun accept(value: T) {
            this.lastResult = value
            results.add(value)
        }
    }
}
