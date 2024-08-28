/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.http.RequestAuth.ChannelTokenAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestException
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InboxApiClientTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val mockChannel = mockk<AirshipChannel> {}
    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val user = User(dataStore, mockChannel)

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
        // Set a valid user
        user.setUser("fakeUserId", "password")
    }

    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testUpdateMessagesSucceeds() {
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

        val (status, result) = inboxApiClient.fetchMessages(user, "channelId", "some last modified")

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
    @Test(expected = RequestException::class)
    @Throws(RequestException::class)
    public fun testNullUrlUpdateMessages() {
        runtimeConfig.updateRemoteConfig(RemoteConfig())
        inboxApiClient.fetchMessages(user, "channelId", null)
    }

    @Test
    @Throws(JsonException::class, RequestException::class)
    public fun testSyncDeletedMessageStateSucceeds() {
        requestSession.addResponse(200)
        val reportings = listOf(
            JsonValue.parseString("""{"message_id":"testId1"}"""),
            JsonValue.parseString("""{"message_id":"testId2"}""")
        )
        val expectedJsonMap = jsonMapOf("messages" to JsonValue.wrapOpt(reportings))

        val (status) = inboxApiClient.syncDeletedMessageState(user, "channelId", reportings)

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/fakeUserId/messages/delete/", requestSession.lastRequest.url.toString())
        assertEquals("channelId", requestSession.lastRequest.headers["X-UA-Channel-ID"])
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals(RequestBody.Json(expectedJsonMap), requestSession.lastRequest.body)
    }

    @Test(expected = RequestException::class)
    @Throws(RequestException::class)
    public fun testNullUrlSyncDeletedMessageState() {
        runtimeConfig.updateRemoteConfig(RemoteConfig())
        inboxApiClient.syncDeletedMessageState(user, "channelId", emptyList())
    }

    @Test
    @Throws(JsonException::class, RequestException::class)
    public fun testSyncReadMessageStateSucceeds() {
        requestSession.addResponse(200)
        val reportings = listOf(
            JsonValue.parseString("""{"message_id":"testId1"}"""),
            JsonValue.parseString("""{"message_id":"testId2"}""")
        )
        val expectedJsonMap = jsonMapOf("messages" to JsonValue.wrapOpt(reportings))

        val (status) = inboxApiClient.syncReadMessageState(user, "channelId", reportings)

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/fakeUserId/messages/unread/", requestSession.lastRequest.url.toString())
        assertEquals("channelId", requestSession.lastRequest.headers["X-UA-Channel-ID"])
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals(RequestBody.Json(expectedJsonMap), requestSession.lastRequest.body)
    }

    @Test(expected = RequestException::class)
    @Throws(RequestException::class)
    public fun testNullUrlSyncReadMessageState() {
        runtimeConfig.updateRemoteConfig(RemoteConfig())
        inboxApiClient.syncReadMessageState(user, "channelId", emptyList())
    }

    @Test
    @Throws(RequestException::class)
    public fun testCreateUserAndroidChannelsSucceeds() {
        requestSession.addResponse(
            200,
            """{ "user_id": "someUserId", "password": "someUserToken" }"""
        )
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM)

        val (status, userCredentials) = inboxApiClient.createUser("channelId")

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
    @Throws(RequestException::class)
    public fun testCreateUserAmazonChannelsSucceeds() {
        requestSession.addResponse(
            200,
            """{ "user_id": "someUserId", "password": "someUserToken" }"""
        )
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM)

        val (status, userCredentials) = inboxApiClient.createUser("channelId")

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/", requestSession.lastRequest.url.toString())
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals("{\"amazon_channels\":[\"channelId\"]}", requestSession.lastRequest.body!!.content)
        assertEquals("someUserId", userCredentials.username)
        assertEquals("someUserToken", userCredentials.password)
    }

    @Test(expected = RequestException::class)
    @Throws(RequestException::class)
    public fun testNullUrlCreateUser() {
        runtimeConfig.updateRemoteConfig(RemoteConfig())
        runtimeConfig.setPlatform(0)
        inboxApiClient.createUser("channelId")
    }

    @Test
    @Throws(RequestException::class)
    public fun testUpdateUserAndroidChannelsSucceeds() {
        requestSession.addResponse(200)
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM)

        val (status) = inboxApiClient.updateUser(user, "channelId")

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/fakeUserId/", requestSession.lastRequest.url.toString())
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals("{\"android_channels\":{\"add\":[\"channelId\"]}}", requestSession.lastRequest.body!!.content)
    }

    @Test
    @Throws(RequestException::class)
    public fun testUpdateUserAmazonChannelsSucceeds() {
        requestSession.addResponse(200)
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM)

        val (status) = inboxApiClient.updateUser(user, "channelId")

        assertEquals(200, status)
        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals("https://example.com/api/user/fakeUserId/", requestSession.lastRequest.url.toString())
        assertEquals("application/vnd.urbanairship+json; version=3;", requestSession.lastRequest.headers["Accept"])
        assertEquals("{\"amazon_channels\":{\"add\":[\"channelId\"]}}", requestSession.lastRequest.body!!.content)
    }

    @Test(expected = RequestException::class)
    @Throws(RequestException::class)
    public fun testNullUrlUpdateUser() {
        runtimeConfig.updateRemoteConfig(RemoteConfig())
        runtimeConfig.setPlatform(0)
        inboxApiClient.updateUser(user, "channelId")
    }
}
