/* Copyright Airship and Contributors */
package com.urbanairship.analytics.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.http.RequestAuth.ChannelTokenAuth
import com.urbanairship.http.RequestBody.GzippedJson
import com.urbanairship.http.RequestException
import com.urbanairship.json.JsonValue
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class EventApiClientTest {

    private val validEvent = JsonValue.parseString("{\"some\":\"json\"}")
    private val invalidEvent = JsonValue.NULL

    private val events = mutableListOf(validEvent)
    private val requestSession = TestRequestSession()
    private var runtimeConfig = TestAirshipRuntimeConfig(
        remoteConfig =  RemoteConfig(
            airshipConfig = RemoteAirshipConfig(
                remoteDataUrl = null,
                deviceApiUrl = null,
                walletUrl = null,
                analyticsUrl = "http://example.com"
            )
        )
    )
    private val client = EventApiClient(runtimeConfig, requestSession)

    /**
     * Test sending a correct request that succeeds
     */
    @Test
    public fun testSendEventsSucceed() {
        requestSession.addResponse(200, "")

        val response = client.sendEvents("some channel", events, emptyMap())

        Assert.assertEquals(200, response.status)
        Assert.assertEquals("", response.body)
        Assert.assertEquals("POST", requestSession.lastRequest.method)
        Assert.assertEquals("http://example.com/warp9/", requestSession.lastRequest.url.toString())
        Assert.assertEquals(GzippedJson(JsonValue.wrapOpt(events)), requestSession.lastRequest.body)
        Assert.assertEquals(ChannelTokenAuth("some channel"), requestSession.lastRequest.auth)
    }

    /**
     * Test sending a request with a null URL will return an exception
     */
    public fun testNullUrl() {
        runtimeConfig = TestAirshipRuntimeConfig(RemoteConfig())
        val result = client.sendEvents("some channel", events, emptyMap())
        assert(result.exception is RequestException)
    }

    /**
     * Test sending null or empty events returns an empty response.
     */
    @Test
    public fun testSendEmptyEvents() {
        requestSession.addResponse(200, "")
        events.clear()

        val response = client.sendEvents("some channel", events, emptyMap())

        Assert.assertEquals(200, response.status)
        Assert.assertEquals("", response.body)
        Assert.assertEquals("POST", requestSession.lastRequest.method)
        Assert.assertEquals("http://example.com/warp9/", requestSession.lastRequest.url.toString())
    }

    /**
     * This verifies all required and most optional headers.
     */
    @Test
    public fun testRequestHeaders() {
        requestSession.addResponse(200, "")

        val headers = mapOf("foo" to "bar")

        val response = client.sendEvents("some channel", events, headers)

        val requestHeaders = requestSession.lastRequest.headers

        Assert.assertEquals(200, response.status)
        Assert.assertEquals("", response.body)
        Assert.assertEquals("POST", requestSession.lastRequest.method)
        Assert.assertEquals("http://example.com/warp9/", requestSession.lastRequest.url.toString())
        Assert.assertEquals("bar", requestHeaders["foo"])
    }

    /**
     * Verify we return a response even if the Json is malformated
     */
    @Test
    public fun testWrongJson() {
        requestSession.addResponse(200, "")

        events.clear()
        events.add(invalidEvent)
        val response = client.sendEvents("some channel", events, emptyMap())
        Assert.assertEquals(200, response.status)
        Assert.assertEquals("", response.body)
        Assert.assertEquals("POST", requestSession.lastRequest.method)
        Assert.assertEquals("http://example.com/warp9/", requestSession.lastRequest.url.toString())
    }
}
