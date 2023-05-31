/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.UAirship
import com.urbanairship.config.AirshipUrlConfig
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ChannelBatchUpdateApiClientTest {

    private val config = TestAirshipRuntimeConfig.newTestConfig()
    private val requestSession = TestRequestSession()
    private val testDispatcher = StandardTestDispatcher()

    private val client =
        ChannelBatchUpdateApiClient(config, requestSession.toSuspendingRequestSession())

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
        config.urlConfig = AirshipUrlConfig.newBuilder().setDeviceUrl("https://example.com").build()
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testUpdate(): TestResult = runTest {
        requestSession.addResponse(200)

        val tags = listOf(
            TagGroupsMutation.newAddTagsMutation("some other group", setOf("add tag")),
            TagGroupsMutation.newRemoveTagsMutation("some other group", setOf("remove tag")),
            TagGroupsMutation.newSetTagsMutation("some group", setOf("set tag"))
        )

        val attributes = listOf(
            AttributeMutation.newSetAttributeMutation(
                "name", JsonValue.wrapOpt("Bob"), 100
            ), AttributeMutation.newSetAttributeMutation(
                "last_name", JsonValue.wrapOpt("Loblaw"), 200
            )
        )

        val subscriptions = listOf(
            SubscriptionListMutation.newSubscribeMutation("burgers", 100),
            SubscriptionListMutation.newUnsubscribeMutation("burritos", 100)
        )

        val expectedBody = """
                    {
                       "attributes":[
                          {
                             "action":"set",
                             "value":"Bob",
                             "key":"name",
                             "timestamp":"1970-01-01T00:00:00"
                          },
                          {
                             "action":"set",
                             "value":"Loblaw",
                             "key":"last_name",
                             "timestamp":"1970-01-01T00:00:00"
                          }
                       ],
                       "subscription_lists":[
                          {
                             "action":"subscribe",
                             "list_id":"burgers",
                             "timestamp":"1970-01-01T00:00:00"
                          },
                          {
                             "action":"unsubscribe",
                             "list_id":"burritos",
                             "timestamp":"1970-01-01T00:00:00"
                          }
                       ],
                       "tags":{
                          "add":{
                             "some other group":[
                                "add tag"
                             ]
                          },
                          "set":{
                             "some group":[
                                "set tag"
                             ]
                          },
                          "remove":{
                             "some other group":[
                                "remove tag"
                             ]
                          }
                       }
                    }
                """

        val response = client.update("someChannelId", tags, attributes, subscriptions)

        assertNull(response.exception)
        assertEquals(200, response.status)
        assertEquals("PUT", requestSession.lastRequest.method)
        assertEquals(
            "https://example.com/api/channels/sdk/batch/someChannelId?platform=android", requestSession.lastRequest.url.toString()
        )
        assertEquals(RequestAuth.ChannelTokenAuth("someChannelId"), requestSession.lastRequest.auth)
        assertEquals(requestSession.lastRequest.body, RequestBody.Json(expectedBody))
    }

    @Test
    public fun testEmptyStripped(): TestResult = runTest {
        requestSession.addResponse(200)

        val response = client.update("someChannelId", emptyList(), emptyList(), emptyList())

        assertNull(response.exception)
        assertEquals(200, response.status)
        assertEquals("PUT", requestSession.lastRequest.method)
        assertEquals(
            "https://example.com/api/channels/sdk/batch/someChannelId?platform=android", requestSession.lastRequest.url.toString()
        )
        assertEquals(requestSession.lastRequest.body, RequestBody.Json("{}"))
    }

    @Test
    public fun testAndroidPlatform(): TestResult = runTest {
        config.platform = UAirship.ANDROID_PLATFORM
        requestSession.addResponse(200)
        client.update("someChannelId", emptyList(), emptyList(), emptyList())
        assertEquals(
            "https://example.com/api/channels/sdk/batch/someChannelId?platform=android", requestSession.lastRequest.url.toString()
        )
    }

    @Test
    public fun testAmazonPlatform(): TestResult = runTest {
        config.platform = UAirship.AMAZON_PLATFORM
        requestSession.addResponse(200)
        client.update("someChannelId", emptyList(), emptyList(), emptyList())
        assertEquals(
            "https://example.com/api/channels/sdk/batch/someChannelId?platform=amazon", requestSession.lastRequest.url.toString()
        )
    }
}
