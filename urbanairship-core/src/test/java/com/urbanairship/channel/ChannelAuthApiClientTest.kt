/* Copyright Airship and Contributors */
package com.urbanairship.channel

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestClock
import com.urbanairship.TestRequestSession
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ChannelAuthApiClientTest {
    private val config = TestAirshipRuntimeConfig()
    private val requestSession = TestRequestSession()
    private var clock = TestClock()
    private val testDispatcher = StandardTestDispatcher()

    private var client = ChannelAuthApiClient(config, requestSession, clock)

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
        clock.currentTimeMillis = 100
        config.updateRemoteConfig(
            RemoteConfig(
                airshipConfig = RemoteAirshipConfig(
                    deviceApiUrl = "https://example.com"
                )
            )
        )
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun test200Response(): TestResult = runTest {
        requestSession.addResponse(
            200,
            jsonMapOf(
                "token" to "some token",
                "expires_in" to 300
            ).toString()
        )
        val response = client.getToken("some channel")

        val result = requireNotNull(response.value)
        assertEquals(result.identifier, "some channel")
        assertEquals(result.token, "some token")
        assertEquals(result.expirationDateMillis, 400)
    }

    @Test
    public fun testRequest(): TestResult = runTest {
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
            auth = RequestAuth.GeneratedChannelToken("some channel")
        )

        assertEquals(expectedRequest, requestSession.lastRequest)
    }

    @Test
    public fun testInvalidResponse(): TestResult = runTest {
        client.getToken("some channel")
    }

    @Test
    public fun testFailed(): TestResult = runTest {
        requestSession.addResponse(404, "{}")
        val response = client.getToken("some channel")
        assertEquals(404, response.status)
        assertEquals(null, response.value)
    }
}
