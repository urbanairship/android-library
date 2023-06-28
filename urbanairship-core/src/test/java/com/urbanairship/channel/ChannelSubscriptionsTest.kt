package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.http.RequestResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ChannelSubscriptionsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockClient = mockk<SubscriptionListApiClient>()
    private val testClock = TestClock()
    private val mockAudienceOverridesProvider = mockk<AudienceOverridesProvider>()

    private val subscriptions = ChannelSubscriptions(
        mockClient, mockAudienceOverridesProvider, testClock
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
    public fun testSubscriptions(): TestResult = runTest {
        coEvery { mockClient.getSubscriptionLists("some channel") } returns RequestResult(
            status = 200,
            value = setOf("one", "two", "three"),
            body = null,
            headers = null
        )

        coEvery {
            mockAudienceOverridesProvider.channelOverrides("some channel")
        } returns AudienceOverrides.Channel(
            subscriptions = listOf(
                SubscriptionListMutation.newSubscribeMutation("five", 100),
                SubscriptionListMutation.newUnsubscribeMutation("four", 100),
                SubscriptionListMutation.newUnsubscribeMutation("one", 100)
            )
        )

        val result = subscriptions.fetchSubscriptionLists("some channel")
        assertTrue(result.isSuccess)
        assertEquals(setOf("two", "three", "five"), result.getOrNull())
    }

    @Test
    public fun testSubscriptionsFails(): TestResult = runTest {
        coEvery { mockClient.getSubscriptionLists("some channel") } returns RequestResult(
            status = 400,
            value = null,
            body = null,
            headers = null
        )

        val result = subscriptions.fetchSubscriptionLists("some channel")
        assertTrue(result.isFailure)
    }

    @Test
    public fun testSubscriptionsChannelIdChange(): TestResult = runTest {
        coEvery { mockClient.getSubscriptionLists("some channel") } returns RequestResult(
            status = 200,
            value = setOf("one", "two", "three"),
            body = null,
            headers = null
        )

        coEvery { mockClient.getSubscriptionLists("some other channel") } returns RequestResult(
            status = 200,
            value = setOf("four", "five", "six"),
            body = null,
            headers = null
        )

        coEvery {
            mockAudienceOverridesProvider.channelOverrides("some channel")
        } returns AudienceOverrides.Channel(
            subscriptions = listOf(
                SubscriptionListMutation.newUnsubscribeMutation("one", 100)
            )
        )

        coEvery {
            mockAudienceOverridesProvider.channelOverrides("some other channel")
        } returns AudienceOverrides.Channel(
            subscriptions = listOf(
                SubscriptionListMutation.newUnsubscribeMutation("six", 100)
            )
        )

        assertEquals(setOf("two", "three"), subscriptions.fetchSubscriptionLists("some channel").getOrNull())
        coVerify(exactly = 1) { mockClient.getSubscriptionLists("some channel") }

        assertEquals(setOf("four", "five"), subscriptions.fetchSubscriptionLists("some other channel").getOrNull())
        coVerify(exactly = 1) { mockClient.getSubscriptionLists("some other channel") }
    }

    @Test
    public fun testSubscriptionCache(): TestResult = runTest {
        coEvery { mockClient.getSubscriptionLists("some channel") } returns RequestResult(
            status = 200,
            value = setOf("one", "two", "three"),
            body = null,
            headers = null
        )

        coEvery {
            mockAudienceOverridesProvider.channelOverrides("some channel")
        } returns AudienceOverrides.Channel(
            subscriptions = listOf(
                SubscriptionListMutation.newUnsubscribeMutation("one", 100)
            )
        )

        // From network
        assertEquals(setOf("two", "three"), subscriptions.fetchSubscriptionLists("some channel").getOrNull())
        coVerify(exactly = 1) { mockClient.getSubscriptionLists("some channel") }

        // Move clock up 1 millis before cache expires
        testClock.currentTimeMillis += 10 * 60 * 1000 - 1

        // Still cached
        assertEquals(setOf("two", "three"), subscriptions.fetchSubscriptionLists("some channel").getOrNull())
        coVerify(exactly = 1) { mockClient.getSubscriptionLists("some channel") }

        // expire
        testClock.currentTimeMillis += 1
        assertEquals(setOf("two", "three"), subscriptions.fetchSubscriptionLists("some channel").getOrNull())
        coVerify(exactly = 2) { mockClient.getSubscriptionLists("some channel") }
    }
}
