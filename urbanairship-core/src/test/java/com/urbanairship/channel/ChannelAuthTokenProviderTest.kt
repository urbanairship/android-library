/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.http.AuthToken
import com.urbanairship.http.RequestResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ChannelAuthTokenProviderTest {

    private var channelId: String = "some channel"
    private val client = mockk<ChannelAuthApiClient>()
    private val clock: TestClock = TestClock()

    private val testDispatcher = StandardTestDispatcher()

    private var authProvider = ChannelAuthTokenProvider(client, clock) {
        channelId
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Before
    public fun setup() {
        clock.currentTimeMillis = 1000
    }

    @Test
    public fun testGetToken(): TestResult = runTest {
        coEvery { client.getToken(channelId) } returns RequestResult(
            status = 200, value = AuthToken(
                channelId, "some token", clock.currentTimeMillis() + 1000
            ), body = null, headers = null
        )

        val token = authProvider.fetchToken(channelId).getOrThrow()
        assertEquals(token, "some token")

        coVerify { client.getToken(channelId) }
        confirmVerified(client)
    }

    @Test
    public fun testGetTokenCache(): TestResult = runTest {
        // Populate cache
        coEvery { client.getToken(channelId) } returns RequestResult(
            status = 200, value = AuthToken(
                channelId, "some token", clock.currentTimeMillis() + 100000
            ), body = null, headers = null
        )

        // Advance clock to before we expire it (expiration - 30 seconds) cache
        clock.currentTimeMillis += 70000

        var token = authProvider.fetchToken(channelId)
        assertEquals(token.getOrNull(), "some token")

        token = authProvider.fetchToken(channelId)
        assertEquals(token.getOrNull(), "some token")

        // For first call
        coVerify(exactly = 1) { client.getToken(channelId) }
        confirmVerified(client)

        // Expire it
        clock.currentTimeMillis += 1
        token = authProvider.fetchToken(channelId)
        assertEquals(token.getOrNull(), "some token")

        // For second call
        coVerify(exactly = 2) { client.getToken(channelId) }
        confirmVerified(client)
    }

    @Test
    public fun testGetTokenFailed(): TestResult = runTest {
        coEvery { client.getToken(channelId) } returns RequestResult(IllegalArgumentException("neat"))
        assertNotNull(authProvider.fetchToken(channelId).exceptionOrNull())
    }

    @Test
    public fun testGetTokenStaleChannelId(): TestResult = runTest {
        coEvery { client.getToken(channelId) } returns RequestResult(
            status = 200, value = AuthToken(
                channelId, "some token", clock.currentTimeMillis() + 100000
            ), body = null, headers = null
        )

        assertNotNull(authProvider.fetchToken("some other channel").exceptionOrNull())
    }

    @Test
    public fun testGetTokenChannelIdChanges(): TestResult = runTest {
        coEvery { client.getToken(channelId) } returns RequestResult(
            status = 200, value = AuthToken(
                channelId, "some token for $channelId", clock.currentTimeMillis() + 100000
            ), body = null, headers = null
        )

        var token = authProvider.fetchToken(channelId)
        assertEquals(token.getOrNull(), "some token for some channel")

        channelId = "some other channel"
        coEvery { client.getToken(channelId) } returns RequestResult(
            status = 200, value = AuthToken(
                channelId, "some token for $channelId", clock.currentTimeMillis() + 100000
            ), body = null, headers = null
        )

        token = authProvider.fetchToken(channelId)
        assertEquals(token.getOrNull(), "some token for some other channel")

        coVerify { client.getToken("some channel") }
        coVerify { client.getToken("some other channel") }
        confirmVerified(client)
    }

    @Test
    public fun testExpireToken(): TestResult = runTest {
        // Populate cache
        coEvery { client.getToken(channelId) } returns RequestResult(
            status = 200, value = AuthToken(
                channelId, "some token", clock.currentTimeMillis() + 100000
            ), body = null, headers = null
        )
        assertEquals(authProvider.fetchToken(channelId).getOrNull(), "some token")

        authProvider.expireToken("some token")
        assertEquals(authProvider.fetchToken(channelId).getOrNull(), "some token")

        coVerify(exactly = 2) { client.getToken(channelId) }
        confirmVerified(client)
    }

    @Test
    public fun testExpireWrongToken(): TestResult = runTest {
        // Populate cache
        coEvery { client.getToken(channelId) } returns RequestResult(
            status = 200, value = AuthToken(
                channelId, "some token", clock.currentTimeMillis() + 100000
            ), body = null, headers = null
        )
        assertEquals(authProvider.fetchToken(channelId).getOrNull(), "some token")

        authProvider.expireToken("some other token")
        assertEquals(authProvider.fetchToken(channelId).getOrNull(), "some token")

        coVerify(exactly = 1) { client.getToken(channelId) }
        confirmVerified(client)
    }
}
