/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ChannelApiClientTest {

    private val payload = ChannelRegistrationPayload.Builder().build()
    private val config = TestAirshipRuntimeConfig()
    private val requestSession = TestRequestSession()
    private val testDispatcher = StandardTestDispatcher()

    private val client = ChannelApiClient(config, requestSession)

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
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
    public fun testCreate(): TestResult = runTest {
        requestSession.addResponse(200, "{ \"ok\": true, \"channel_id\": \"someChannelId\"}")
        val response = client.createChannel(payload)
        val result = requireNotNull(response.value)

        assertEquals(
            Channel(
                "someChannelId", "https://example.com/api/channels/someChannelId"
            ), result
        )

        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals(
            "https://example.com/api/channels/", requestSession.lastRequest.url.toString()
        )
        assertEquals(RequestAuth.GeneratedAppToken, requestSession.lastRequest.auth)
        assertEquals(requestSession.lastRequest.body, RequestBody.Json(payload))
    }

    @Test
    public fun testCreateNullUrl(): TestResult = runTest {
        config.updateRemoteConfig(RemoteConfig())
        requestSession.addResponse(200, "{ \"ok\": true, \"channel_id\": \"someChannelId\"}")
        val response = client.createChannel(payload)
        assertNotNull(response.exception)
    }

    @Test
    public fun testUpdate(): TestResult = runTest {
        requestSession.addResponse(200)
        val response = client.updateChannel("someChannelId", payload)
        val result = requireNotNull(response.value)

        assertEquals(
            Channel(
                "someChannelId", "https://example.com/api/channels/someChannelId"
            ), result
        )

        assertEquals("PUT", requestSession.lastRequest.method)
        assertEquals(RequestAuth.ChannelTokenAuth("someChannelId"), requestSession.lastRequest.auth)
        assertEquals(
            "https://example.com/api/channels/someChannelId", requestSession.lastRequest.url.toString()
        )
        assertEquals(requestSession.lastRequest.body, RequestBody.Json(payload))
    }

    @Test
    public fun testUpdateNullUrl(): TestResult = runTest {
        config.updateRemoteConfig(RemoteConfig())
        requestSession.addResponse(200, "{ \"ok\": true, \"channel_id\": \"someChannelId\"}")
        val response = client.updateChannel("someChannelId", payload)
        assertNotNull(response.exception)
    }
}
