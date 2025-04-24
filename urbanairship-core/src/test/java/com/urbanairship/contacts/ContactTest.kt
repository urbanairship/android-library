/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestClock
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.ChannelRegistrationPayload
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.http.RequestResult
import com.urbanairship.inputvalidation.AirshipInputValidation
import com.urbanairship.json.JsonValue
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import com.urbanairship.remoteconfig.ContactConfig
import com.urbanairship.remoteconfig.RemoteConfig
import com.urbanairship.util.TaskSleeper
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ContactTest {

    private val testDispatcher = StandardTestDispatcher()
    private val config = TestAirshipRuntimeConfig()

    private val mockChannel = mockk<AirshipChannel>(relaxUnitFun = true) {
        every { id } returns "test channel id"
    }
    private val mockSubscriptionListApiClient = mockk<SubscriptionListApiClient>()
    private val mockAudienceOverridesProvider = mockk<AudienceOverridesProvider>(relaxUnitFun = true) {
        every { updates } returns MutableStateFlow(0U)
        coEvery { contactOverrides(any()) } returns AudienceOverrides.Contact()
    }

    private val contactChannelFlow = MutableSharedFlow<Result<List<ContactChannel>>>()
    private val mockChannelsContactProvider = mockk<ContactChannelsProvider>(relaxUnitFun = true) {
        every { updates } returns contactChannelFlow.asSharedFlow()
    }

    private val conflictEvents = Channel<ConflictEvent>(Channel.UNLIMITED)
    private val currentNamedUserIdUpdates = MutableStateFlow<String?>(null)
    private val contactIdUpdates = MutableStateFlow<ContactIdUpdate?>(null)

    private val mockContactManager = mockk<ContactManager>(relaxUnitFun = true) {
        every { this@mockk.contactIdUpdates } returns this@ContactTest.contactIdUpdates
        every { this@mockk.conflictEvents } returns this@ContactTest.conflictEvents
        every { this@mockk.currentNamedUserIdUpdates } returns this@ContactTest.currentNamedUserIdUpdates
    }

    private val mockSmsValidator: AirshipInputValidation.Validator = mockk(relaxUnitFun = true)

    private val testActivityMonitor = TestActivityMonitor()
    private val testClock = TestClock()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferenceDataStore = PreferenceDataStore.inMemoryStore(context)
    private val privacyManager = PrivacyManager(preferenceDataStore, PrivacyManager.Feature.ALL)
    private val pushListeners = mutableListOf<PushListener>()
    private val mockPushManager: PushManager = mockk {
        every { this@mockk.addInternalPushListener(capture(pushListeners)) } just runs
    }

    private val contact: Contact by lazy {
        Contact(
            context = context,
            preferenceDataStore = preferenceDataStore,
            config = config,
            privacyManager = privacyManager,
            airshipChannel = mockChannel,
            audienceOverridesProvider = mockAudienceOverridesProvider,
            activityMonitor = testActivityMonitor,
            clock = testClock,
            contactManager = mockContactManager,
            smsValidator = mockSmsValidator,
            pushManager = mockPushManager,
            subscriptionsProvider = SubscriptionsProvider(
                apiClient = mockSubscriptionListApiClient,
                privacyManager = privacyManager,
                stableContactIdUpdates = mockContactManager.stableContactIdUpdates,
                overrideUpdates = mockAudienceOverridesProvider.contactUpdates(mockContactManager.stableContactIdUpdates),
                clock = testClock,
                taskSleeper = TaskSleeper.default,
                dispatcher = testDispatcher
            ),
            contactChannelsProvider = mockChannelsContactProvider,
            subscriptionListDispatcher = testDispatcher
        )
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testChannelCreated(): TestResult = runTest {
        val listeners = mutableListOf<AirshipChannelListener>()
        every {
            mockChannel.addChannelListener(capture(listeners))
        } just runs

        every { mockChannel.addChannelRegistrationPayloadExtender(any()) } just runs

        // Init
        contact

        assertEquals(1, listeners.count())
        listeners.forEach { it.onChannelCreated("some channel id") }

        verify { mockContactManager.addOperation(ContactOperation.Resolve) }
    }

    @Test
    public fun testExtendChannelRegistration(): TestResult = runTest {
        val extenders = mutableListOf<AirshipChannel.Extender>()
        every {
            mockChannel.addChannelRegistrationPayloadExtender(capture(extenders))
        } just runs

        // init
        contact

        coEvery {
            mockContactManager.stableContactIdUpdate()
        } returns ContactIdUpdate(
            contactId = "some stable not verified id",
            namedUserId = null,
            isStable = true,
            resolveDateMs = testClock.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10) - 1)

        coEvery {
            mockContactManager.stableContactIdUpdate(testClock.currentTimeMillis())
        } returns ContactIdUpdate(
            contactId = "some contact id",
            namedUserId = null,
            isStable = true,
            resolveDateMs = testClock.currentTimeMillis)

        coEvery { mockContactManager.lastContactId } returns "some contact id"

        var builder = ChannelRegistrationPayload.Builder()
        extenders.forEach {
            builder = when (it) {
                is AirshipChannel.Extender.Suspending -> it.extend(builder)
                is AirshipChannel.Extender.Blocking -> it.extend(builder)
                else -> { builder }
            }
        }

        assertEquals("some contact id", builder.build().contactId)
    }

    @Test
    public fun testExtendChannelRegistrationAlreadyVerifiedContactId(): TestResult = runTest {
        val extenders = mutableListOf<AirshipChannel.Extender>()
        every {
            mockChannel.addChannelRegistrationPayloadExtender(capture(extenders))
        } just runs

        // init
        contact

        coEvery {
            mockContactManager.stableContactIdUpdate()
        } returns ContactIdUpdate("some stable verified id", null, true, testClock.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10) + 1)

        coEvery { mockContactManager.lastContactId } returns "some stable verified id"

        var builder = ChannelRegistrationPayload.Builder()
        extenders.forEach {
            builder = when (it) {
                is AirshipChannel.Extender.Suspending -> it.extend(builder)
                is AirshipChannel.Extender.Blocking -> it.extend(builder)
                else -> { builder }
            }
        }

        assertEquals("some stable verified id", builder.build().contactId)
    }

    @Test
    public fun testExtendChannelRegistrationConfigurableResolve(): TestResult = runTest {
        config.updateRemoteConfig(
            RemoteConfig(
                contactConfig = ContactConfig(channelRegistrationMaxResolveAgeMs = 99)
            )
        )

        val extenders = mutableListOf<AirshipChannel.Extender>()
        every {
            mockChannel.addChannelRegistrationPayloadExtender(capture(extenders))
        } just runs

        // init
        contact

        coEvery {
            mockContactManager.stableContactIdUpdate()
        } returns ContactIdUpdate("some stable not verified id", null, true, testClock.currentTimeMillis() - 100)

        coEvery {
            mockContactManager.stableContactIdUpdate(testClock.currentTimeMillis())
        } returns ContactIdUpdate("some stable verified id", null, true, testClock.currentTimeMillis)

        coEvery { mockContactManager.lastContactId } returns "some stable verified id"

        var builder = ChannelRegistrationPayload.Builder()
        extenders.forEach {
            builder = when (it) {
                is AirshipChannel.Extender.Suspending -> it.extend(builder)
                is AirshipChannel.Extender.Blocking -> it.extend(builder)
                else -> { builder }
            }
        }

        assertEquals("some stable verified id", builder.build().contactId)
    }

    @Test
    public fun testExtendNullChannelRegistration(): TestResult = runTest {
        every { mockChannel.id } returns null
        every { mockContactManager.lastContactId } returns "last contact id"

        val extenders = mutableListOf<AirshipChannel.Extender>()
        every {
            mockChannel.addChannelRegistrationPayloadExtender(capture(extenders))
        } just runs

        // init
        contact

        var builder = ChannelRegistrationPayload.Builder()
        extenders.forEach {
            builder = when (it) {
                is AirshipChannel.Extender.Suspending -> it.extend(builder)
                is AirshipChannel.Extender.Blocking -> it.extend(builder)
                else -> { builder }
            }
        }

        assertEquals("last contact id", builder.build().contactId)
    }

    @Test
    public fun testForegroundResolves(): TestResult = runTest {
        // init
        contact

        var count = 0
        every { mockContactManager.addOperation(ContactOperation.Resolve) } answers {
            count++
        }

        testActivityMonitor.foreground()

        assertEquals(1, count)

        testActivityMonitor.background()
        testActivityMonitor.foreground()

        assertEquals(1, count)

        // Almost 1 hour
        testClock.currentTimeMillis += 1 * 60 * 60 * 1000 - 1

        testActivityMonitor.background()
        testActivityMonitor.foreground()

        assertEquals(1, count)

        testClock.currentTimeMillis += 1
        testActivityMonitor.foreground()

        assertEquals(2, count)
    }

    @Test
    public fun testForegroundRefreshesContactChannels(): TestResult = runTest {
        // init
        contact
        verify(exactly = 0) { mockChannelsContactProvider.refresh() }

        testActivityMonitor.foreground()
        verify { mockChannelsContactProvider.refresh() }
    }

    @Test
    public fun testPushRefreshesContact(): TestResult = runTest {
        // init
        contact
        val mockPush = mockk<PushMessage> {
            every { this@mockk.containsKey("com.urbanairship.contact.update") } returns true
        }
        pushListeners.forEach { it.onPushReceived(mockPush, false) }
        verify { mockChannelsContactProvider.refresh() }
    }

    @Test
    public fun testForegroundResolvesRemoteConfig(): TestResult = runTest {
        // init
        contact

        var count = 0
        every { mockContactManager.addOperation(ContactOperation.Resolve) } answers {
            count++
        }

        testActivityMonitor.foreground()

        assertEquals(1, count)

        testActivityMonitor.background()
        testActivityMonitor.foreground()

        assertEquals(1, count)

        config.updateRemoteConfig(
            RemoteConfig(
                contactConfig = ContactConfig(foregroundIntervalMs = 100)
            )
        )

        testClock.currentTimeMillis += 99
        testActivityMonitor.background()
        testActivityMonitor.foreground()

        assertEquals(1, count)

        testClock.currentTimeMillis += 1
        testActivityMonitor.foreground()

        assertEquals(2, count)
    }

    @Test
    public fun testContactIdChangesUpdatesRegistration(): TestResult = runTest {
        var count = 0
        every { mockChannel.updateRegistration() }.answers {
            count++
        }

        // init
        contact
        assertEquals(0, count)
        testDispatcher.scheduler.advanceUntilIdle()

        contactIdUpdates.tryEmit(ContactIdUpdate("some contact id", null, true, 0))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, count)

        // Different update, same contact id
        contactIdUpdates.tryEmit(ContactIdUpdate("some contact id", null, true, 0))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, count)

        contactIdUpdates.tryEmit(ContactIdUpdate("some  other contact id", null, false, 0))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, count)
    }

    @Test
    public fun testReset(): TestResult = runTest {
        contact.reset()
        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.Reset) }
    }

    @Test
    public fun testResetContactsDisabled(): TestResult = runTest {
        // init
        contact

        // Privacy manager change will trigger a reset
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
        verify(exactly = 1) { mockContactManager.resetIfNeeded() }

        contact.reset()
        verify(exactly = 1) { mockContactManager.resetIfNeeded() }
    }

    @Test
    public fun testIdentify(): TestResult = runTest {
        contact.identify("some named user id")
        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.Identify("some named user id")) }
    }

    @Test
    public fun testIdentifyContactsDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
        contact.identify("some named user id")
        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Identify("some named user id")) }
    }

    @Test
    public fun testNotifyRemoteLogin(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.CONTACTS)
        contact.notifyRemoteLogin()
        verify(exactly = 1) {
            mockContactManager.addOperation(match<ContactOperation.Verify> {
                it.required && it.dateMs == testClock.currentTimeMillis
            })
        }
    }

    @Test
    public fun testEditTags(): TestResult = runTest {
        contact.editTagGroups().setTag("some group", "some tag").apply()

        val expectedMutations = listOf(
            TagGroupsMutation.newSetTagsMutation("some group", setOf("some tag"))
        )

        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.Update(tags = expectedMutations)) }
        verify { mockAudienceOverridesProvider.notifyPendingChanged() }
    }

    @Test
    public fun testEditTagsContactDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
        contact.editTagGroups().setTag("some group", "some tag").apply()

        val expectedMutations = listOf(
            TagGroupsMutation.newSetTagsMutation("some group", setOf("some tag"))
        )
        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(tags = expectedMutations)) }
        verify(exactly = 0) { mockAudienceOverridesProvider.notifyPendingChanged() }
    }

    @Test
    public fun testEditTagsTagsAndAttributesDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.CONTACTS)
        contact.editTagGroups().setTag("some group", "some tag").apply()

        val expectedMutations = listOf(
            TagGroupsMutation.newSetTagsMutation("some group", setOf("some tag"))
        )
        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(tags = expectedMutations)) }
        verify(exactly = 0) { mockAudienceOverridesProvider.notifyPendingChanged() }
    }

    @Test
    public fun testEditAttributes(): TestResult = runTest {
        contact.editAttributes().setAttribute("some attribute", "some value").apply()

        val expectedMutations = listOf(
            AttributeMutation.newSetAttributeMutation(
                "some attribute", JsonValue.wrapOpt("some value"), testClock.currentTimeMillis()
            )
        )

        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.Update(attributes = expectedMutations)) }
        verify(exactly = 1) { mockAudienceOverridesProvider.notifyPendingChanged() }
    }

    @Test
    public fun testEditAttributesContactDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
        contact.editAttributes().setAttribute("some attribute", "some value").apply()

        val expectedMutations = listOf(
            AttributeMutation.newSetAttributeMutation(
                "some attribute", JsonValue.wrapOpt("some value"), testClock.currentTimeMillis()
            )
        )

        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(attributes = expectedMutations)) }
    }

    @Test
    public fun testEditAttributesTagsAndAttributesDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.CONTACTS)
        contact.editAttributes().setAttribute("some attribute", "some value").apply()

        val expectedMutations = listOf(
            AttributeMutation.newSetAttributeMutation(
                "some attribute", JsonValue.wrapOpt("some value"), testClock.currentTimeMillis()
            )
        )

        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(attributes = expectedMutations)) }
        verify(exactly = 0) { mockAudienceOverridesProvider.notifyPendingChanged() }

    }

    @Test
    public fun testEditSubscriptions(): TestResult = runTest {
        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply()

        val expectedMutations = listOf(
            ScopedSubscriptionListMutation.newSubscribeMutation(
                "some list", Scope.APP, testClock.currentTimeMillis()
            )
        )

        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.Update(subscriptions = expectedMutations)) }
        verify(exactly = 1) { mockAudienceOverridesProvider.notifyPendingChanged() }

    }

    @Test
    public fun testEditSubscriptionsContactDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)
        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply()

        val expectedMutations = listOf(
            ScopedSubscriptionListMutation.newSubscribeMutation(
                "some list", Scope.APP, testClock.currentTimeMillis()
            )
        )

        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(subscriptions = expectedMutations)) }
        verify(exactly = 0) { mockAudienceOverridesProvider.notifyPendingChanged() }

    }

    @Test
    public fun testEditSubscriptionsTagsAndAttributesDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.CONTACTS)
        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply()

        val expectedMutations = listOf(
            ScopedSubscriptionListMutation.newSubscribeMutation(
                "some list", Scope.APP, testClock.currentTimeMillis()
            )
        )

        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(subscriptions = expectedMutations)) }
        verify(exactly = 0) { mockAudienceOverridesProvider.notifyPendingChanged() }

    }

    @Test
    public fun testRegisterEmail(): TestResult = runTest {
        val address = "some address"
        val options = EmailRegistrationOptions.options(null, null, true)

        contact.registerEmail(address, options)

        verify(exactly = 1) {
            mockContactManager.addOperation(
                ContactOperation.RegisterEmail(
                    address, options
                )
            )
        }

        verify(exactly = 1) { mockAudienceOverridesProvider.notifyPendingChanged() }
    }

    @Test
    public fun testRegisterEmailContactsDisabled(): TestResult = runTest {
        every { mockPushManager.addInternalPushListener(any()) } just runs

        privacyManager.setEnabledFeatures(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)

        val address = "some address"
        val options = EmailRegistrationOptions.options(null, null, true)

        contact.registerEmail(address, options)

        verify(exactly = 0) {
            mockContactManager.addOperation(
                ContactOperation.RegisterEmail(
                    address, options
                )
            )
        }
        verify(exactly = 0) { mockAudienceOverridesProvider.notifyPendingChanged() }

    }

    @Test
    public fun testRegisterSms(): TestResult = runTest {
        val address = "some address"
        val options = SmsRegistrationOptions.options("some sender id")

        contact.registerSms(address, options)

        verify(exactly = 1) {
            mockContactManager.addOperation(
                ContactOperation.RegisterSms(
                    address, options
                )
            )
        }
        verify(exactly = 1) { mockAudienceOverridesProvider.notifyPendingChanged() }

    }

    @Test
    public fun testRegisterSmsContactsDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)

        val address = "some address"
        val options = SmsRegistrationOptions.options("some sender id")

        contact.registerSms(address, options)

        verify(exactly = 0) {
            mockContactManager.addOperation(
                ContactOperation.RegisterSms(
                    address, options
                )
            )
        }
        verify(exactly = 0) { mockAudienceOverridesProvider.notifyPendingChanged() }
    }

    @Test
    public fun testRegisterOpen(): TestResult = runTest {
        val address = "some address"
        val options = OpenChannelRegistrationOptions.options("some platform")

        contact.registerOpenChannel(address, options)

        verify(exactly = 1) {
            mockContactManager.addOperation(
                ContactOperation.RegisterOpen(
                    address, options
                )
            )
        }
        verify(exactly = 1) { mockAudienceOverridesProvider.notifyPendingChanged() }

    }

    @Test
    public fun testRegisterOpenContactsDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)

        val address = "some address"
        val options = OpenChannelRegistrationOptions.options("some platform")

        contact.registerOpenChannel(address, options)

        verify(exactly = 0) {
            mockContactManager.addOperation(
                ContactOperation.RegisterOpen(
                    address, options
                )
            )
        }
        verify(exactly = 0) { mockAudienceOverridesProvider.notifyPendingChanged() }

    }

    @Test
    public fun testAssociateChannel(): TestResult = runTest {
        contact.associateChannel("channel id", ChannelType.OPEN)

        verify(exactly = 1) {
            mockContactManager.addOperation(
                ContactOperation.AssociateChannel(
                    "channel id", ChannelType.OPEN
                )
            )
        }
    }

    @Test
    public fun testAssociateChannelContactsDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)

        contact.associateChannel("channel id", ChannelType.OPEN)

        verify(exactly = 0) {
            mockContactManager.addOperation(
                ContactOperation.AssociateChannel(
                    "channel id", ChannelType.OPEN
                )
            )
        }
    }

    @Test
    public fun testConflicts(): TestResult = runTest {
        val mockConflictListener = mockk<ContactConflictListener>(relaxed = true)
        contact.contactConflictListener = mockConflictListener
        testDispatcher.scheduler.advanceUntilIdle()

        val event = ConflictEvent(
            conflictingNameUserId = "some named user id"
        )

        conflictEvents.trySend(event)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { mockConflictListener.onConflict(event) }
    }

    @Test
    public fun testMigrate(): TestResult = runTest {
        val namedUserId = UUID.randomUUID().toString()
        val tags = listOf(
            TagGroupsMutation.newSetTagsMutation("group", setOf("tag")),
            TagGroupsMutation.newAddTagsMutation("some-other-group", setOf("some-tag"))
        )

        val attributes = listOf(
            AttributeMutation.newRemoveAttributeMutation("some-attribute", 100),
            AttributeMutation.newSetAttributeMutation(
                "cool", JsonValue.wrap("story"), 100
            )
        )

        preferenceDataStore.put(Contact.LEGACY_NAMED_USER_ID_KEY, namedUserId)
        preferenceDataStore.put(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY, JsonValue.wrap(tags))
        preferenceDataStore.put(
            Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY, JsonValue.wrap(attributes)
        )

        // init
        contact

        verify { mockContactManager.addOperation(ContactOperation.Identify(namedUserId)) }
        verify {
            mockContactManager.addOperation(
                ContactOperation.Update(
                    tags = tags, attributes = attributes
                )
            )
        }
    }

    @Test
    public fun testMigrateTagsAndAttributesDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.CONTACTS)

        val namedUserId = UUID.randomUUID().toString()
        val tags = listOf(
            TagGroupsMutation.newSetTagsMutation("group", setOf("tag")),
            TagGroupsMutation.newAddTagsMutation("some-other-group", setOf("some-tag"))
        )

        val attributes = listOf(
            AttributeMutation.newRemoveAttributeMutation("some-attribute", 100),
            AttributeMutation.newSetAttributeMutation(
                "cool", JsonValue.wrap("story"), 100
            )
        )

        preferenceDataStore.put(Contact.LEGACY_NAMED_USER_ID_KEY, namedUserId)
        preferenceDataStore.put(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY, JsonValue.wrap(tags))
        preferenceDataStore.put(
            Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY, JsonValue.wrap(attributes)
        )

        // init
        contact

        verify { mockContactManager.addOperation(ContactOperation.Identify(namedUserId)) }
        verify(exactly = 0) {
            mockContactManager.addOperation(
                ContactOperation.Update(
                    tags = tags, attributes = attributes
                )
            )
        }
    }

    @Test
    public fun testMigrateContactsDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)

        val namedUserId = UUID.randomUUID().toString()
        val tags = listOf(
            TagGroupsMutation.newSetTagsMutation("group", setOf("tag")),
            TagGroupsMutation.newAddTagsMutation("some-other-group", setOf("some-tag"))
        )

        val attributes = listOf(
            AttributeMutation.newRemoveAttributeMutation("some-attribute", 100),
            AttributeMutation.newSetAttributeMutation(
                "cool", JsonValue.wrap("story"), 100
            )
        )

        preferenceDataStore.put(Contact.LEGACY_NAMED_USER_ID_KEY, namedUserId)
        preferenceDataStore.put(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY, JsonValue.wrap(tags))
        preferenceDataStore.put(
            Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY, JsonValue.wrap(attributes)
        )

        // init
        contact

        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Identify(namedUserId)) }
        verify(exactly = 0) {
            mockContactManager.addOperation(
                ContactOperation.Update(
                    tags = tags, attributes = attributes
                )
            )
        }
    }

    @Test
    public fun testFetchSubscriptions(): TestResult = runTest {
        contactIdUpdates.tryEmit(ContactIdUpdate("stable contact id", null, true, 0))
        val networkResult = RequestResult(
            status = 200,
            value = mapOf(
                "foo" to setOf(Scope.APP, Scope.SMS),
                "bar" to setOf(Scope.EMAIL)
            ),
            body = null,
            headers = emptyMap()
        )

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists(any()) } returns networkResult

        contact.subscriptions.test {
            assertEquals(networkResult.value, awaitItem().getOrThrow())
            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testFetchSubscriptionsCache(): TestResult = runTest {
        contactIdUpdates.tryEmit(ContactIdUpdate("stable contact id", null, true, 0))
        val networkResult = RequestResult(
            status = 200,
            value = mapOf(
                "foo" to setOf(Scope.APP, Scope.SMS),
                "bar" to setOf(Scope.EMAIL)
            ),
            body = null,
            headers = emptyMap()
        )

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("stable contact id") } returns networkResult

        contact.subscriptions.test {
            assertEquals(networkResult.value, awaitItem().getOrThrow())
            ensureAllEventsConsumed()
        }

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("stable contact id") } throws IllegalStateException()
        // Advance time by half of the max cache age.
        testClock.currentTimeMillis += 5.minutes.inWholeMilliseconds
        advanceTimeBy(5.minutes)

        contact.subscriptions.test {
            assertEquals(networkResult.value, awaitItem().getOrThrow())
            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testFetchSubscriptionsError(): TestResult = runTest {
        contactIdUpdates.tryEmit(ContactIdUpdate("stable contact id", null, true, 0))

        val networkResult = RequestResult<Map<String, Set<Scope>>>(
            status = 404, value = null, body = null, headers = emptyMap()
        )

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("stable contact id") } returns networkResult
        assertTrue(contact.fetchSubscriptionLists().isFailure)
    }

    @Test
    public fun testFetchSubscriptionsIgnoresCacheContactIdChanges(): TestResult = runTest {
        coEvery { mockContactManager.stableContactIdUpdate() } returnsMany listOf(
            ContactIdUpdate("first", null, true, 0),
            ContactIdUpdate("second", null, true, 1)
        )

        val firstResult = RequestResult(
            status = 200, value = mapOf(
                "foo" to setOf(Scope.APP, Scope.SMS), "bar" to setOf(Scope.EMAIL)
            ), body = null, headers = emptyMap()
        )
        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("first") } returns firstResult

        val secondResult = RequestResult(
            status = 200, value = mapOf(
                "baz" to setOf(Scope.EMAIL)
            ), body = null, headers = emptyMap()
        )
        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("second") } returns secondResult

        contact.subscriptions.test {
            contactIdUpdates.emit(ContactIdUpdate("first", null, true, 0))
            assertEquals(firstResult.value, awaitItem().getOrThrow())

            contactIdUpdates.emit(ContactIdUpdate("second", null, true, 1))
            assertEquals(secondResult.value, awaitItem().getOrThrow())

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testFetchSubscriptionListCacheTime(): TestResult = runTest {
        coEvery { mockContactManager.stableContactIdUpdate() } returns ContactIdUpdate("some id", null, true, 0)

        val firstResult = RequestResult(
            status = 200, value = mapOf(
                "foo" to setOf(Scope.APP, Scope.SMS), "bar" to setOf(Scope.EMAIL)
            ), body = null, headers = emptyMap()
        )
        val secondResult = RequestResult(
            status = 200, value = mapOf(
                "baz" to setOf(Scope.EMAIL)
            ), body = null, headers = emptyMap()
        )

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("some id") } returnsMany listOf(
            firstResult, secondResult
        )

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("second") } returns secondResult

        contact.subscriptions.test {
            contactIdUpdates.emit(ContactIdUpdate("some id", null, true, 0))

            assertEquals(firstResult.value, awaitItem().getOrThrow())

            // expire cache
            testClock.currentTimeMillis += 10 * 60 * 1000

            contactIdUpdates.emit(ContactIdUpdate("second", null, true, 1))
            assertEquals(secondResult.value, awaitItem().getOrThrow())

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testFetchSubscriptionListOverrides(): TestResult = runTest {
        val overrides = AudienceOverrides.Contact(
            subscriptions = listOf(
                ScopedSubscriptionListMutation.newSubscribeMutation(
                    "some list",
                    Scope.SMS,
                    testClock.currentTimeMillis()
                ),
                ScopedSubscriptionListMutation.newSubscribeMutation(
                    "foo",
                    Scope.WEB,
                    testClock.currentTimeMillis()
                ),
                ScopedSubscriptionListMutation.newUnsubscribeMutation(
                    "bar",
                    Scope.EMAIL,
                    testClock.currentTimeMillis()
                ),
                ScopedSubscriptionListMutation.newUnsubscribeMutation(
                    "bar",
                    Scope.APP,
                    testClock.currentTimeMillis()
                ),
            )
        )

        val contactUpdate = ContactIdUpdate("some id", null, true, 0)
        val updateFlow = MutableStateFlow<ContactIdUpdate?>(null)
        coEvery { mockContactManager.stableContactIdUpdate() } returns contactUpdate
        coEvery { mockAudienceOverridesProvider.contactOverrides("some id") } returns overrides
        coEvery { mockContactManager.contactIdUpdates } returns updateFlow

        val networkResult = RequestResult(
            status = 200, value = mapOf(
                "foo" to setOf(Scope.APP, Scope.SMS),
                "bar" to setOf(Scope.EMAIL)
            ), body = null, headers = emptyMap()
        )

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("some id") } answers {
            networkResult
        }

        val expected = mapOf(
            "foo" to setOf(Scope.WEB, Scope.APP, Scope.SMS),
            "some list" to setOf(Scope.SMS)
        )

        updateFlow.emit(contactUpdate)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = contact.fetchSubscriptionLists()
        assertEquals(expected, result.getOrThrow())
    }

    @Test
    public fun testResendOptIn(): TestResult = runTest {
        val channel = ContactChannel.Email(
            ContactChannel.Email.RegistrationInfo.Pending(
                address = "email@email.email",
                registrationOptions = EmailRegistrationOptions.options(null, null, true)
            )
        )
        contact.resendDoubleOptIn(channel)
        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.Resend(channel)) }
    }

    @Test
    public fun testResendOptInContactsDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.Feature.CONTACTS)
        val channel = ContactChannel.Email(
            ContactChannel.Email.RegistrationInfo.Pending(
                address = "email@email.email",
                registrationOptions = EmailRegistrationOptions.options(null, null, true)
            )
        )
        contact.resendDoubleOptIn(channel)
        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Resend(channel)) }
    }

    @Test
    public fun testDisassociate(): TestResult = runTest {
        val channel = ContactChannel.Email(
            ContactChannel.Email.RegistrationInfo.Pending(
                address = "email@email.email",
                registrationOptions = EmailRegistrationOptions.options(null, null, true)
            )
        )
        contact.disassociateChannel(channel)
        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.DisassociateChannel(channel, true)) }
        verify(exactly = 1) { mockAudienceOverridesProvider.notifyPendingChanged() }
    }

    @Test
    public fun testDisassociateOptOutFalse(): TestResult = runTest {
        val channel = ContactChannel.Email(
            ContactChannel.Email.RegistrationInfo.Pending(
                address = "email@email.email",
                registrationOptions = EmailRegistrationOptions.options(null, null, true)
            )
        )
        contact.disassociateChannel(channel, false)
        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.DisassociateChannel(channel, false)) }
        verify(exactly = 1) { mockAudienceOverridesProvider.notifyPendingChanged() }
    }

    @Test
    public fun testDisassociateContactsDisabled(): TestResult = runTest {
        privacyManager.disable(PrivacyManager.Feature.CONTACTS)
        val channel = ContactChannel.Email(
            ContactChannel.Email.RegistrationInfo.Pending(
                address = "email@email.email",
                registrationOptions = EmailRegistrationOptions.options(null, null, true)
            )
        )
        contact.disassociateChannel(channel)
        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.DisassociateChannel(channel, true)) }
        verify(exactly = 0) { mockAudienceOverridesProvider.notifyPendingChanged() }
    }

    @Test
    public fun testValidateSms(): TestResult = runTest {
        coEvery { mockSmsValidator.validate(any()) } answers {
            AirshipInputValidation.Result.Valid("value")
        } andThen AirshipInputValidation.Result.Invalid

        val address = "some address"
        val sender = "some sender"

        assertTrue(contact.validateSms(address, sender))
        assertFalse(contact.validateSms(address, sender))

        coVerify(exactly = 2) { mockSmsValidator.validate(any()) }
    }
}
