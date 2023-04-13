/* Copyright Airship and Contributors */
package com.urbanairship.channel

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestClock
import com.urbanairship.TestRequestSession
import com.urbanairship.config.AirshipUrlConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestException
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ChannelAuthApiClientTest {
    private val config = TestAirshipRuntimeConfig.newTestConfig()
    private val requestSession = TestRequestSession()
    private var clock = TestClock()

    private var nonce = "noncesense"

    private var client = ChannelAuthApiClient(config, requestSession, clock) {
        nonce
    }

    @Before
    public fun setup() {
        clock.currentTimeMillis = 100
        config.urlConfig = AirshipUrlConfig.newBuilder().setDeviceUrl("https://example.com").build()
    }

    @Test
    public fun test200Response() {
        requestSession.addResponse(
            200,
            jsonMapOf(
                "token" to "some token",
                "expires_in" to 300
            ).toString()
        )
        val response = client.getToken("some channel")

        val result = requireNotNull(response.result)
        assertEquals(result.identifier, "some channel")
        assertEquals(result.token, "some token")
        assertEquals(result.expirationTimeMS, 400)
    }

    @Test
    public fun testRequest() {
        requestSession.addResponse(
            200,
            jsonMapOf(
                "token" to "some token",
                "expires_in" to 300
            ).toString()
        )

        client.getToken("some channel")

        val expectedRequest = Request(
            url = Uri.parse("https://example.com/api/auth/device"),
            method = "GET",
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Channel-ID" to "some channel",
                "X-UA-Appkey" to config.configOptions.appKey,
                "X-UA-Nonce" to nonce,
                "X-UA-Timestamp" to DateUtils.createIso8601TimeStamp(100)
            ),
            auth = RequestAuth.BearerToken("B+vEEJqqBZY2N6pzviaQq96YrJFVQkvmc10rMnioado=\n")
        )

        assertEquals(expectedRequest, requestSession.lastRequest)
    }

    @Test(expected = RequestException::class)
    public fun testInvalidResponse() {
        client.getToken("some channel")
    }

    @Test
    public fun testFailed() {
        requestSession.addResponse(404, "{}")
        val response = client.getToken("some channel")
        assertEquals(404, response.status)
        assertEquals(null, response.result)
    }
}
