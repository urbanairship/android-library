/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.http.AuthToken
import com.urbanairship.http.RequestException
import com.urbanairship.http.Response
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ChannelAuthTokenProviderTest {
    private var channelId: String = "some channel"
    private val client = mockk<ChannelAuthApiClient>()
    private val clock: TestClock = TestClock()

    private var authProvider = ChannelAuthTokenProvider(client, clock) {
        channelId
    }

    @Before
    public fun setup() {
        clock.currentTimeMillis = 1000
    }

    @Test
    public fun testGetToken() {
        every { client.getToken(channelId) } returns Response(
            status = 200,
            result = AuthToken(
                channelId,
                "some token",
                clock.currentTimeMillis() + 1000
            )
        )

        val token = authProvider.fetchToken(channelId)
        assertEquals(token, "some token")

        verify { client.getToken(channelId) }
        confirmVerified(client)
    }

    @Test
    public fun testGetTokenCache() {
        // Populate cache
        every { client.getToken(channelId) } returns Response(
            status = 200,
            result = AuthToken(
                channelId,
                "some token",
                clock.currentTimeMillis() + 100000
            )
        )

        // Advance clock to before we expire it (expiration - 30 seconds) cache
        clock.currentTimeMillis += 70000

        var token = authProvider.fetchToken(channelId)
        assertEquals(token, "some token")

        token = authProvider.fetchToken(channelId)
        assertEquals(token, "some token")

        // For first call
        verify(exactly = 1) { client.getToken(channelId) }
        confirmVerified(client)

        // Expire it
        clock.currentTimeMillis += 1
        token = authProvider.fetchToken(channelId)
        assertEquals(token, "some token")

        // For second call
        verify(exactly = 2) { client.getToken(channelId) }
        confirmVerified(client)
    }

    @Test(expected = RequestException::class)
    public fun testGetTokenFailed() {
        every { client.getToken(channelId) } throws RequestException("Neat")
        authProvider.fetchToken(channelId)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun testGetTokenStaleChannelId() {
        every { client.getToken(channelId) } returns Response(
            status = 200,
            result = AuthToken(
                channelId,
                "some token",
                clock.currentTimeMillis() + 100000
            )
        )

        authProvider.fetchToken("some other channel id")
    }

    @Test
    public fun testGetTokenChannelIdChanges() {
        every { client.getToken(channelId) } returns Response(
            status = 200,
            result = AuthToken(
                channelId,
                "some token for $channelId",
                clock.currentTimeMillis() + 100000
            )
        )

        var token = authProvider.fetchToken(channelId)
        assertEquals(token, "some token for some channel")

        channelId = "some other channel"
        every { client.getToken(channelId) } returns Response(
            status = 200,
            result = AuthToken(
                channelId,
                "some token for $channelId",
                clock.currentTimeMillis() + 100000
            )
        )

        token = authProvider.fetchToken(channelId)
        assertEquals(token, "some token for some other channel")

        verify { client.getToken("some channel") }
        verify { client.getToken("some other channel") }
        confirmVerified(client)
    }

    @Test
    public fun testExpireToken() {
        // Populate cache
        every { client.getToken(channelId) } returns Response(
            status = 200,
            result = AuthToken(
                channelId,
                "some token",
                clock.currentTimeMillis() + 100000
            )
        )
        assertEquals(authProvider.fetchToken(channelId), "some token")

        authProvider.expireToken("some token")
        assertEquals(authProvider.fetchToken(channelId), "some token")

        verify(exactly = 2) { client.getToken(channelId) }
        confirmVerified(client)
    }

    @Test
    public fun testExpireWrongToken() {
        // Populate cache
        every { client.getToken(channelId) } returns Response(
            status = 200,
            result = AuthToken(
                channelId,
                "some token",
                clock.currentTimeMillis() + 100000
            )
        )
        assertEquals(authProvider.fetchToken(channelId), "some token")

        authProvider.expireToken("some other token")
        assertEquals(authProvider.fetchToken(channelId), "some token")

        verify(exactly = 1) { client.getToken(channelId) }
        confirmVerified(client)
    }
}
