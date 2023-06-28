/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestClock
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.base.Extender
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.ChannelRegistrationPayload
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.http.RequestResult
import com.urbanairship.json.JsonValue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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
public class ContactTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockChannel = mockk<AirshipChannel>(relaxed = true)
    private val mockSubscriptionListApiClient = mockk<SubscriptionListApiClient>()
    private val mockAudienceOverridesProvider = mockk<AudienceOverridesProvider>(relaxed = true)

    private val conflictEvents = Channel<ConflictEvent>(Channel.UNLIMITED)
    private val currentNamedUserIdUpdates = MutableStateFlow<String?>(null)
    private val contactIdUpdates = MutableStateFlow<ContactIdUpdate?>(null)

    private val mockContactManager = mockk<ContactManager>(relaxed = true) {
        every { this@mockk.contactIdUpdates } returns this@ContactTest.contactIdUpdates
        every { this@mockk.conflictEvents } returns this@ContactTest.conflictEvents
        every { this@mockk.currentNamedUserIdUpdates } returns this@ContactTest.currentNamedUserIdUpdates
    }

    private val testActivityMonitor = TestActivityMonitor()
    private val testClock = TestClock()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferenceDataStore = PreferenceDataStore.inMemoryStore(context)
    private val privacyManager = PrivacyManager(preferenceDataStore, PrivacyManager.FEATURE_ALL)

    private val contact: Contact by lazy {
        Contact(
            context,
            preferenceDataStore,
            privacyManager,
            mockChannel,
            mockAudienceOverridesProvider,
            testActivityMonitor,
            testClock,
            mockSubscriptionListApiClient,
            mockContactManager,
            testDispatcher
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
        val extenders = mutableListOf<Extender<ChannelRegistrationPayload.Builder>>()
        every {
            mockChannel.addChannelRegistrationPayloadExtender(capture(extenders))
        } just runs

        // init
        contact

        every {
            mockContactManager.lastContactId
        } returns "some contact id"

        var builder = ChannelRegistrationPayload.Builder()
        extenders.forEach {
            builder = it.extend(builder)
        }

        assertEquals("some contact id", builder.build().contactId)
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

        // Almost 24 hours
        testClock.currentTimeMillis += 24 * 60 * 60 * 1000 - 1

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

        contactIdUpdates.tryEmit(ContactIdUpdate("some contact id", true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, count)

        // Different update, same contact id
        contactIdUpdates.tryEmit(ContactIdUpdate("some contact id", true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, count)

        contactIdUpdates.tryEmit(ContactIdUpdate("some  other contact id", false))
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
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.Reset) }

        contact.reset()
        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.Reset) }
    }

    @Test
    public fun testIdentify(): TestResult = runTest {
        contact.identify("some named user id")
        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.Identify("some named user id")) }
    }

    @Test
    public fun testIdentifyContactsDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        contact.identify("some named user id")
        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Identify("some named user id")) }
    }

    @Test
    public fun testEditTags(): TestResult = runTest {
        contact.editTagGroups().setTag("some group", "some tag").apply()

        val expectedMutations = listOf(
            TagGroupsMutation.newSetTagsMutation("some group", setOf("some tag"))
        )

        verify(exactly = 1) { mockContactManager.addOperation(ContactOperation.Update(tags = expectedMutations)) }
    }

    @Test
    public fun testEditTagsContactDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        contact.editTagGroups().setTag("some group", "some tag").apply()

        val expectedMutations = listOf(
            TagGroupsMutation.newSetTagsMutation("some group", setOf("some tag"))
        )
        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(tags = expectedMutations)) }
    }

    @Test
    public fun testEditTagsTagsAndAttributesDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_CONTACTS)
        contact.editTagGroups().setTag("some group", "some tag").apply()

        val expectedMutations = listOf(
            TagGroupsMutation.newSetTagsMutation("some group", setOf("some tag"))
        )
        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(tags = expectedMutations)) }
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
    }

    @Test
    public fun testEditAttributesContactDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
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
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_CONTACTS)
        contact.editAttributes().setAttribute("some attribute", "some value").apply()

        val expectedMutations = listOf(
            AttributeMutation.newSetAttributeMutation(
                "some attribute", JsonValue.wrapOpt("some value"), testClock.currentTimeMillis()
            )
        )

        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(attributes = expectedMutations)) }
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
    }

    @Test
    public fun testEditSubscriptionsContactDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)
        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply()

        val expectedMutations = listOf(
            ScopedSubscriptionListMutation.newSubscribeMutation(
                "some list", Scope.APP, testClock.currentTimeMillis()
            )
        )

        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(subscriptions = expectedMutations)) }
    }

    @Test
    public fun testEditSubscriptionsTagsAndAttributesDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_CONTACTS)
        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply()

        val expectedMutations = listOf(
            ScopedSubscriptionListMutation.newSubscribeMutation(
                "some list", Scope.APP, testClock.currentTimeMillis()
            )
        )

        verify(exactly = 0) { mockContactManager.addOperation(ContactOperation.Update(subscriptions = expectedMutations)) }
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
    }

    @Test
    public fun testRegisterEmailContactsDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)

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
    }

    @Test
    public fun testRegisterSmsContactsDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)

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
    }

    @Test
    public fun testRegisterOpenContactsDisabled(): TestResult = runTest {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)

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
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)

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
        val mockConflictListener = mockk<ContactConflictListener>()
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
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_CONTACTS)

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
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)

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
        coEvery { mockContactManager.stableContactId() } returns "stable contact id"

        val networkResult = RequestResult(
            status = 200, value = mapOf(
                "foo" to setOf(Scope.APP, Scope.SMS), "bar" to setOf(Scope.EMAIL)
            ), body = null, headers = emptyMap()
        )

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("stable contact id") } returns networkResult

        val subscriptions = contact.fetchSubscriptionLists()
        assertEquals(networkResult.value, subscriptions.getOrThrow())
    }

    @Test
    public fun testFetchSubscriptionsCache(): TestResult = runTest {
        coEvery { mockContactManager.stableContactId() } returns "stable contact id"

        val networkResult = RequestResult(
            status = 200, value = mapOf(
                "foo" to setOf(Scope.APP, Scope.SMS), "bar" to setOf(Scope.EMAIL)
            ), body = null, headers = emptyMap()
        )

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("stable contact id") } returns networkResult
        assertEquals(networkResult.value, contact.fetchSubscriptionLists().getOrThrow())

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("stable contact id") } throws IllegalStateException()
        assertEquals(networkResult.value, contact.fetchSubscriptionLists().getOrThrow())
    }

    @Test
    public fun testFetchSubscriptionsError(): TestResult = runTest {
        coEvery { mockContactManager.stableContactId() } returns "stable contact id"

        val networkResult = RequestResult<Map<String, Set<Scope>>>(
            status = 404, value = null, body = null, headers = emptyMap()
        )

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("stable contact id") } returns networkResult
        assertTrue(contact.fetchSubscriptionLists().isFailure)
    }

    @Test
    public fun testFetchSubscriptionsIgnoresCacheContactIdChanges(): TestResult = runTest {
        coEvery { mockContactManager.stableContactId() } returnsMany listOf("first", "second")

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

        assertEquals(firstResult.value, contact.fetchSubscriptionLists().getOrThrow())
        assertEquals(secondResult.value, contact.fetchSubscriptionLists().getOrThrow())
    }

    @Test
    public fun testFetchSubscriptionListCacheTime(): TestResult = runTest {
        coEvery { mockContactManager.stableContactId() } returns "some id"

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

        assertEquals(firstResult.value, contact.fetchSubscriptionLists().getOrThrow())

        // almost 10 minutes
        testClock.currentTimeMillis += 10 * 60 * 1000 - 1
        assertEquals(firstResult.value, contact.fetchSubscriptionLists().getOrThrow())

        // expire cache
        testClock.currentTimeMillis += 1
        assertEquals(secondResult.value, contact.fetchSubscriptionLists().getOrThrow())
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

        coEvery { mockContactManager.stableContactId() } returns "some id"
        coEvery { mockAudienceOverridesProvider.contactOverrides("some id") } returns overrides

        val networkResult = RequestResult(
            status = 200, value = mapOf(
                "foo" to setOf(Scope.APP, Scope.SMS),
                "bar" to setOf(Scope.EMAIL)
            ), body = null, headers = emptyMap()
        )

        coEvery { mockSubscriptionListApiClient.getSubscriptionLists("some id") } returns networkResult

        val expected = mapOf(
            "foo" to setOf(Scope.WEB, Scope.APP, Scope.SMS),
            "some list" to setOf(Scope.SMS)
        )

        assertEquals(expected, contact.fetchSubscriptionLists().getOrThrow())
    }
}
