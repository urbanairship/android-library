package com.urbanairship.contacts

import com.urbanairship.PrivacyManager
import com.urbanairship.TestClock
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.http.RequestResult
import com.urbanairship.util.TaskSleeper
import com.urbanairship.util.minus
import com.urbanairship.util.plus
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ContactChannelsProviderTest {
    private val apiClient: ContactChannelsApiClient = mockk()
    private val taskSleeper: TaskSleeper = mockk {
        coEvery { sleep(any()) } coAnswers {
            val milliseconds = it.invocation.args.first() as Long
            delay(milliseconds.milliseconds)
        }
    }

    private val audienceOverridesUpdates = MutableStateFlow(AudienceOverrides.Contact())

    private val privacyManager = mockk<PrivacyManager> {
        coEvery { isEnabled(any()) } returns true
    }

    private val contactIdUpdate = MutableStateFlow<String?>(null)
    private val clock = TestClock().apply { currentTime = Instant.ofEpochMilli(0) }

    private val testDispatcher = StandardTestDispatcher()

    private val provider = ContactChannelsProvider(
        apiClient, privacyManager, contactIdUpdate.mapNotNull { it }, audienceOverridesUpdates, clock, taskSleeper, testDispatcher
    )

    @Test
    public fun testChannels(): TestResult = runTest(testDispatcher) {
        val responseChannels = listOf(
            makeRegisteredSmsChannel(),
            makeRegisteredSmsChannel(senderId = "second", channelId = "foo"),
            makeRegisteredEmailChannel()
        )

        coEvery { apiClient.fetch("some-contact-id") } returns makeRequestResult(responseChannels)

        val channelOverrides = listOf(
            ContactChannelMutation.Associate(makePendingSmsChannel()),
            ContactChannelMutation.Disassociated(makeRegisteredSmsChannel(senderId = "second", channelId = "foo")),
            ContactChannelMutation.Associate(makePendingEmailChannel())
        )
        audienceOverridesUpdates.value = AudienceOverrides.Contact(channels = channelOverrides)
        val expected = listOf(
            responseChannels[0], responseChannels[2],
            channelOverrides[0].channel, channelOverrides[2].channel,
        )

        provider.updates.test {
            ensureAllEventsConsumed()
            contactIdUpdate.value = "some-contact-id"
            assertEquals(expected, this.awaitItem().data.getOrThrow())
        }
    }
//
//    @Test
//    public fun testContactIdUpdate(): TestResult = runTest(testDispatcher) {
//        val fooResponseChannels = listOf(
//            makeRegisteredSmsChannel(),
//            makeRegisteredEmailChannel()
//        )
//
//        coEvery { apiClient.fetch("foo") } returns makeRequestResult(fooResponseChannels)
//
//        val fooChannelOverrides = listOf(
//            ContactChannelMutation.Associate(makePendingSmsChannel()),
//        )
//
//        val barResponseChannels = listOf(
//            makeRegisteredSmsChannel(),
//            makeRegisteredEmailChannel()
//        )
//
//        coEvery { apiClient.fetch("bar") } returns makeRequestResult(barResponseChannels)
//
//        val barChannelOverrides = listOf(
//            ContactChannelMutation.Associate(makePendingSmsChannel()),
//        )
//
//        coEvery { audienceOverridesProvider.contactOverrides("bar") } returns AudienceOverrides.Contact(
//            channels = barChannelOverrides
//        )
//
//        provider.contactChannels.test {
//            ensureAllEventsConsumed()
//
//            contactUpdates.value = ContactIdUpdate("foo", namedUserId = null, isStable = true, resolveDate = 0)
//            assertEquals(fooResponseChannels + fooChannelOverrides.map { it.channel }, this.awaitItem().getOrThrow())
//            ensureAllEventsConsumed()
//
//            contactUpdates.value = ContactIdUpdate("bar", namedUserId = null, isStable = false, resolveDate = 0)
//            ensureAllEventsConsumed()
//
//            contactUpdates.value = ContactIdUpdate("bar", namedUserId = null, isStable = true, resolveDate = 0)
//            assertEquals(barResponseChannels + barChannelOverrides.map { it.channel }, this.awaitItem().getOrThrow())
//            ensureAllEventsConsumed()
//
//        }
//    }
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    @Test
//    public fun testRefreshOnCadence(): TestResult = runTest(testDispatcher) {
//        val firstResponse = listOf(
//            makeRegisteredSmsChannel(),
//        )
//
//        val secondResponse = listOf(
//            makeRegisteredSmsChannel(),
//            makeRegisteredEmailChannel()
//        )
//
//        coEvery { apiClient.fetch("some-contact-id") } returnsMany listOf(
//            makeRequestResult(firstResponse),
//            makeRequestResult(secondResponse)
//        )
//
//        val channelOverrides = listOf(
//            ContactChannelMutation.Associate(makePendingSmsChannel()),
//            ContactChannelMutation.Associate(makePendingEmailChannel())
//        )
//
//        coEvery { audienceOverridesProvider.contactOverrides("some-contact-id") } returns AudienceOverrides.Contact(
//            channels = channelOverrides
//        )
//
//        provider.contactChannels.test {
//            ensureAllEventsConsumed()
//
//            contactUpdates.value = ContactIdUpdate("some-contact-id", namedUserId = null, isStable = true, resolveDate = 0)
//
//            assertEquals(firstResponse + channelOverrides.map { it.channel }, this.awaitItem().getOrThrow())
//            ensureAllEventsConsumed()
//
//            clock.currentTime += (10.minutes.inWholeMilliseconds - 1).milliseconds
//            advanceTimeBy(10.minutes.inWholeMilliseconds - 1)
//
//            ensureAllEventsConsumed()
//            clock.currentTime += (1).milliseconds
//            advanceTimeBy( 1)
//
//            assertEquals(secondResponse + channelOverrides.map { it.channel }, this.awaitItem().getOrThrow())
//            ensureAllEventsConsumed()
//        }
//    }
//
//    @Test
//    public fun testManualRefresh(): TestResult = runTest(testDispatcher) {
//        val firstResponse = listOf(
//            makeRegisteredSmsChannel(),
//        )
//
//        val secondResponse = listOf(
//            makeRegisteredSmsChannel(),
//            makeRegisteredEmailChannel()
//        )
//
//        coEvery { apiClient.fetch("some-contact-id") } returnsMany listOf(
//            makeRequestResult(firstResponse),
//            makeRequestResult(secondResponse)
//        )
//
//        val channelOverrides = listOf(
//            ContactChannelMutation.Associate(makePendingSmsChannel()),
//            ContactChannelMutation.Associate(makePendingEmailChannel())
//        )
//
//        coEvery { audienceOverridesProvider.contactOverrides("some-contact-id") } returns AudienceOverrides.Contact(
//            channels = channelOverrides
//        )
//
//        provider.contactChannels.test {
//            ensureAllEventsConsumed()
//
//            contactUpdates.value = ContactIdUpdate("some-contact-id", namedUserId = null, isStable = true, resolveDate = 0)
//
//            assertEquals(firstResponse + channelOverrides.map { it.channel }, this.awaitItem().getOrThrow())
//            ensureAllEventsConsumed()
//
//            provider.refresh()
//            assertEquals(secondResponse + channelOverrides.map { it.channel }, this.awaitItem().getOrThrow())
//            ensureAllEventsConsumed()
//        }
//    }
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    @Test
//    public fun testRefreshFailsIgnored(): TestResult = runTest(testDispatcher) {
//        val firstResponse = listOf(
//            makeRegisteredSmsChannel(),
//        )
//
//        coEvery { apiClient.fetch("some-contact-id") } returnsMany listOf(
//            makeRequestResult(firstResponse),
//            makeFailedRequest(),
//            makeRequestResult(firstResponse)
//        )
//
//        val channelOverrides = listOf(
//            ContactChannelMutation.Associate(makePendingSmsChannel()),
//            ContactChannelMutation.Associate(makePendingEmailChannel())
//        )
//
//        coEvery { audienceOverridesProvider.contactOverrides("some-contact-id") } returns AudienceOverrides.Contact(
//            channels = channelOverrides
//        )
//
//        provider.contactChannels.test {
//            ensureAllEventsConsumed()
//
//            contactUpdates.value = ContactIdUpdate("some-contact-id", namedUserId = null, isStable = true, resolveDate = 0)
//
//            assertEquals(firstResponse + channelOverrides.map { it.channel }, this.awaitItem().getOrThrow())
//            ensureAllEventsConsumed()
//
//            clock.currentTime += (10.minutes.inWholeMilliseconds).milliseconds
//            advanceTimeBy(10.minutes.inWholeMilliseconds)
//
//            assertEquals(firstResponse + channelOverrides.map { it.channel }, this.awaitItem().getOrThrow())
//        }
//    }
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    @Test
//    public fun testFirstFetchFailNotIgnored(): TestResult = runTest(testDispatcher) {
//        val secondResponse = listOf(
//            makeRegisteredSmsChannel(),
//        )
//
//        coEvery { apiClient.fetch("some-contact-id") } returnsMany listOf(
//            makeFailedRequest(),
//            makeRequestResult(secondResponse)
//        )
//
//        val channelOverrides = listOf(
//            ContactChannelMutation.Associate(makePendingSmsChannel()),
//            ContactChannelMutation.Associate(makePendingEmailChannel())
//        )
//
//        coEvery { audienceOverridesProvider.contactOverrides("some-contact-id") } returns AudienceOverrides.Contact(
//            channels = channelOverrides
//        )
//
//        provider.contactChannels.test {
//            ensureAllEventsConsumed()
//
//            contactUpdates.value = ContactIdUpdate("some-contact-id", namedUserId = null, isStable = true, resolveDate = 0)
//
//            assertTrue(this.awaitItem().isFailure)
//            ensureAllEventsConsumed()
//
//            clock.currentTime += (10.seconds.inWholeMilliseconds).milliseconds
//            advanceTimeBy(10.seconds)
//
//            assertEquals(secondResponse + channelOverrides.map { it.channel }, this.awaitItem().getOrThrow())
//        }
//    }
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    @Test
//    public fun testBackoff(): TestResult = runTest(testDispatcher) {
//        val response = listOf(
//            makeRegisteredSmsChannel(),
//        )
//
//        coEvery { apiClient.fetch("some-contact-id") } returnsMany listOf(
//            makeFailedRequest(),
//            makeFailedRequest(),
//            makeFailedRequest(),
//            makeFailedRequest(),
//            makeRequestResult(response),
//        )
//
//        val channelOverrides = listOf(
//            ContactChannelMutation.Associate(makePendingSmsChannel()),
//            ContactChannelMutation.Associate(makePendingEmailChannel())
//        )
//
//        coEvery { audienceOverridesProvider.contactOverrides("some-contact-id") } returns AudienceOverrides.Contact(
//            channels = channelOverrides
//        )
//
//        provider.contactChannels.test {
//            ensureAllEventsConsumed()
//
//            contactUpdates.value = ContactIdUpdate("some-contact-id", namedUserId = null, isStable = true, resolveDate = 0)
//            assertTrue(this.awaitItem().isFailure)
//
//            clock.currentTime += (90.seconds.inWholeMilliseconds).milliseconds
//            advanceTimeBy(90.seconds)
//            assertEquals(response + channelOverrides.map { it.channel }, this.awaitItem().getOrThrow())
//
//            ensureAllEventsConsumed()
//        }
//
//        coVerifyOrder {
//            taskSleeper.sleep(8.seconds)
//            taskSleeper.sleep(16.seconds)
//            taskSleeper.sleep(32.seconds)
//            taskSleeper.sleep(64.seconds)
//            taskSleeper.sleep(10.minutes)
//        }
//    }

    private fun makeRequestResult(channels: List<ContactChannel>): RequestResult<List<ContactChannel>> {
        return RequestResult(
            status = 200,
            value = channels,
            body = null,
            headers = emptyMap()
        )
    }

    private fun makeFailedRequest(): RequestResult<List<ContactChannel>> {
        return RequestResult(
            status = 400,
            value = null,
            body = null,
            headers = emptyMap()
        )
    }

    private fun makeRegisteredSmsChannel(
        channelId: String? = null,
        senderId: String? = null
    ): ContactChannel {
        return ContactChannel.Sms(
            ContactChannel.Sms.RegistrationInfo.Registered(
                channelId = channelId ?: UUID.randomUUID().toString(),
                maskedAddress = "masked!",
                senderId = senderId ?: UUID.randomUUID().toString(),
                isOptIn = true
            )
        )
    }

    private fun makePendingSmsChannel(address: String? = null, senderId: String? = null): ContactChannel {
        return ContactChannel.Sms(
            ContactChannel.Sms.RegistrationInfo.Pending(
                address = address ?: UUID.randomUUID().toString(),
                registrationOptions = SmsRegistrationOptions.options(senderId ?: UUID.randomUUID().toString())
            )
        )
    }

    private fun makeRegisteredEmailChannel(channelId: String? = null): ContactChannel {
        return ContactChannel.Email(
            ContactChannel.Email.RegistrationInfo.Registered(
                channelId = channelId ?: UUID.randomUUID().toString(),
                maskedAddress = "masked!",
            )
        )
    }

    private fun makePendingEmailChannel(address: String? = null): ContactChannel {
        return ContactChannel.Email(
            ContactChannel.Email.RegistrationInfo.Pending(
                address = address ?: UUID.randomUUID().toString(),
                registrationOptions = EmailRegistrationOptions.options(doubleOptIn = true)
            )
        )
    }
}

private val ContactChannelMutation.channel : ContactChannel?
    get() {
        return when(this) {
            is ContactChannelMutation.AssociateAnon -> null
            is ContactChannelMutation.Associate -> this.channel
            is ContactChannelMutation.Disassociated -> this.channel
        }
    }
