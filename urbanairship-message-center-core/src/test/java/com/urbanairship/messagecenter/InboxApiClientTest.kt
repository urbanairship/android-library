/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Platform
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.http.RequestAuth.ChannelTokenAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestException
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class InboxApiClientTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userCredentials = UserCredentials("fakeUserId", "password")

    private val requestSession = TestRequestSession()
    private val runtimeConfig = TestAirshipRuntimeConfig(
        RemoteConfig(
            RemoteAirshipConfig(
                "https://remote-data",
                "https://example.com",
                "https://wallet",
                "https://analytics",
                "https://metered-usage"
            )
        )
    )

    private val inboxApiClient = InboxApiClient(runtimeConfig, requestSession)

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testUpdateMessagesSucceeds(): TestResult = runTest {
        @Language("JSON")
        val responseBody = """
            {
              "messages": [
                {
                  "message_id": "some_mesg_id",
                  "message_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/",
                  "message_body_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/",
                  "message_read_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/",
                  "unread": true,
                  "message_sent": "2010-09-05 12:13 -0000",
                  "title": "Message title",
                  "extra": {
                    "some_key": "some_value"
                  },
                  "content_type": "text/html",
                  "content_size": "128"
                }
              ]
            }
            """.trimIndent()

        requestSession.addResponse(200, responseBody)

        val (status, result) = inboxApiClient.fetchMessages(userCredentials, "channelId", "some last modified")

        assertEquals(200, status)
        assertEquals("GET", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/fakeUserId/messages/", requestSession.lastRequest.url.toString())
        assertEquals("channelId", requestSession.lastRequest.headers["X-UA-Channel-ID"])
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals("some last modified", requestSession.lastRequest.headers["If-Modified-Since"])
        assertEquals(JsonValue.parseString(responseBody).optMap().opt("messages").list, result)
    }

    /**
     * Test update messages with null URL.
     */
    @Test
    public fun testNullUrlUpdateMessages(): TestResult = runTest {
        runtimeConfig.updateRemoteConfig(RemoteConfig())
        val result = inboxApiClient.fetchMessages(userCredentials, "channelId", null)

        assertFalse(result.isSuccessful)
        assertNotNull(result.exception)
    }

    @Test
    @Throws(JsonException::class, RequestException::class)
    public fun testSyncDeletedMessageStateSucceeds(): TestResult = runTest {
        requestSession.addResponse(200)
        val reportings = listOf(
            JsonValue.parseString("""{"message_id":"testId1"}"""),
            JsonValue.parseString("""{"message_id":"testId2"}""")
        )
        val expectedJsonMap = jsonMapOf("messages" to JsonValue.wrapOpt(reportings))

        val (status) = inboxApiClient.syncDeletedMessageState(userCredentials, "channelId", reportings)

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/fakeUserId/messages/delete/", requestSession.lastRequest.url.toString())
        assertEquals("channelId", requestSession.lastRequest.headers["X-UA-Channel-ID"])
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals(RequestBody.Json(expectedJsonMap), requestSession.lastRequest.body)
    }

    @Test
    public fun testNullUrlSyncDeletedMessageState(): TestResult = runTest {
        runtimeConfig.updateRemoteConfig(RemoteConfig())
        val result = inboxApiClient.syncDeletedMessageState(userCredentials, "channelId", emptyList())

        assertFalse(result.isSuccessful)
        assertNotNull(result.exception)
    }

    @Test
    public fun testSyncReadMessageStateSucceeds(): TestResult = runTest {
        requestSession.addResponse(200)
        val reportings = listOf(
            JsonValue.parseString("""{"message_id":"testId1"}"""),
            JsonValue.parseString("""{"message_id":"testId2"}""")
        )
        val expectedJsonMap = jsonMapOf("messages" to JsonValue.wrapOpt(reportings))

        val (status) = inboxApiClient.syncReadMessageState(userCredentials, "channelId", reportings)

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/fakeUserId/messages/unread/", requestSession.lastRequest.url.toString())
        assertEquals("channelId", requestSession.lastRequest.headers["X-UA-Channel-ID"])
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals(RequestBody.Json(expectedJsonMap), requestSession.lastRequest.body)
    }

    @Test
    public fun testNullUrlSyncReadMessageState(): TestResult = runTest {
        runtimeConfig.updateRemoteConfig(RemoteConfig())
        val result = inboxApiClient.syncReadMessageState(userCredentials, "channelId", emptyList())

        assertFalse(result.isSuccessful)
        assertNotNull(result.exception)

        advanceUntilIdle()
    }

    @Test
    public fun testCreateUserAndroidChannelsSucceeds(): TestResult = runTest {
        requestSession.addResponse(
            200,
            """{ "user_id": "someUserId", "password": "someUserToken" }"""
        )
        runtimeConfig.setPlatform(Platform.ANDROID)

        val (status, userCredentials) = inboxApiClient.createUser("channelId")
        requireNotNull(userCredentials)

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/", requestSession.lastRequest.url.toString())
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals("{\"android_channels\":[\"channelId\"]}", requestSession.lastRequest.body!!.content)
        assertEquals(ChannelTokenAuth("channelId"), requestSession.lastRequest.auth)
        assertEquals("someUserId", userCredentials.username)
        assertEquals("someUserToken", userCredentials.password)
    }

    @Test
    public fun testCreateUserAmazonChannelsSucceeds(): TestResult = runTest {
        requestSession.addResponse(
            200,
            """{ "user_id": "someUserId", "password": "someUserToken" }"""
        )
        runtimeConfig.setPlatform(Platform.AMAZON)

        val (status, userCredentials) = inboxApiClient.createUser("channelId")
        requireNotNull(userCredentials)

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/", requestSession.lastRequest.url.toString())
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals("{\"amazon_channels\":[\"channelId\"]}", requestSession.lastRequest.body!!.content)
        assertEquals("someUserId", userCredentials.username)
        assertEquals("someUserToken", userCredentials.password)
    }

    @Test
    public fun testNullUrlCreateUser(): TestResult = runTest {
        runtimeConfig.updateRemoteConfig(RemoteConfig())
        runtimeConfig.setPlatform(Platform.UNKNOWN)
        val result = inboxApiClient.createUser("channelId")
        assertNotNull(result.exception)
    }

    @Test
    public fun testUpdateUserAndroidChannelsSucceeds(): TestResult = runTest {
        requestSession.addResponse(200)
        runtimeConfig.setPlatform(Platform.ANDROID)

        val (status) = inboxApiClient.updateUser(userCredentials, "channelId")

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/fakeUserId/", requestSession.lastRequest.url.toString())
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals("{\"android_channels\":{\"add\":[\"channelId\"]}}", requestSession.lastRequest.body!!.content)
    }

    @Test
    public fun testUpdateUserAmazonChannelsSucceeds(): TestResult = runTest {
        requestSession.addResponse(200)
        runtimeConfig.setPlatform(Platform.AMAZON)

        val (status) = inboxApiClient.updateUser(userCredentials, "channelId")

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/fakeUserId/", requestSession.lastRequest.url.toString())
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals("{\"amazon_channels\":{\"add\":[\"channelId\"]}}", requestSession.lastRequest.body!!.content)
    }

    @Test
    public fun testNullUrlUpdateUser(): TestResult = runTest {
        runtimeConfig.updateRemoteConfig(RemoteConfig())
        runtimeConfig.setPlatform(Platform.UNKNOWN)
        val result = inboxApiClient.updateUser(userCredentials, "channelId")
        assertNotNull(result.exception)
    }
}
