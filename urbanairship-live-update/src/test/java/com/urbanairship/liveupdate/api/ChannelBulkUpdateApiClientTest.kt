/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.SubscriptionListMutation.newSubscribeMutation
import com.urbanairship.channel.SubscriptionListMutation.newUnsubscribeMutation
import com.urbanairship.channel.TagGroupsMutation.newAddTagsMutation
import com.urbanairship.channel.TagGroupsMutation.newSetTagsMutation
import com.urbanairship.config.AirshipUrlConfig
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ChannelBulkUpdateApiClientTest {

    private val config = TestAirshipRuntimeConfig.newTestConfig()
    private val requestSession = TestRequestSession()
    private val testDispatcher = StandardTestDispatcher()

    private val client: ChannelBulkUpdateApiClient =
        ChannelBulkUpdateApiClient(config, requestSession.toSuspendingRequestSession())

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
    public fun testBulkUpdateRequestSuccess(): TestResult = runTest {
        requestSession.addResponse(200)

        val payload = ChannelBulkUpdateRequest(channelId = CHANNEL_ID)

        val response = client.update(CHANNEL_ID)

        assertNull(response.exception)
        assertEquals(200, response.status)

        val request = requestSession.lastRequest
        assertEquals("PUT", request.method)
        assertEquals("https://example.com/api/channels/sdk/batch/$CHANNEL_ID?platform=android", request.url.toString())
        assertEquals(payload.toJsonValue().toString(), request.body?.content)
    }

    @Test
    public fun testChannelBulkUpdateRequestPayload() {
        val payload = ChannelBulkUpdateRequest(
            channelId = CHANNEL_ID,
            subscriptionLists = listOf(
                newSubscribeMutation("intriguing_ideas", 0L),
                newUnsubscribeMutation("animal_facts", 1L)
            ),
            tagGroups = listOf(
                newAddTagsMutation("group1", setOf("tag1")),
                newAddTagsMutation("group2", setOf("tag3")),
                newSetTagsMutation("group2", setOf("tag4")),
            ),
            attributes = listOf(
                AttributeMutation.newSetAttributeMutation("position", JsonValue.wrap("LF"), 2L),
                AttributeMutation.newRemoveAttributeMutation("minor_league", 3L),
            )
        )
        val expected = JsonValue.parseString(EXPECTED_BATCH_UPDATE_JSON)
        assertEquals(expected, payload.toJsonValue())
    }

    private companion object {
        private const val CHANNEL_ID = "channelId"

        @Language("JSON")
        private val EXPECTED_BATCH_UPDATE_JSON = """
            {
              "attributes": [
                {
                  "action": "set",
                  "value": "LF",
                  "key": "position",
                  "timestamp": "1970-01-01T00:00:00"
                },
                {
                  "action": "remove",
                  "key": "minor_league",
                  "timestamp": "1970-01-01T00:00:00"
                }
              ],
              "subscription_lists": [
                {
                  "action": "subscribe",
                  "list_id": "intriguing_ideas",
                  "timestamp": "1970-01-01T00:00:00"
                },
                {
                  "action": "unsubscribe",
                  "list_id": "animal_facts",
                  "timestamp": "1970-01-01T00:00:00"
                }
              ],
              "tags": [
                {
                  "set": {
                    "group2": [
                      "tag4"
                    ]
                  }
                },
                {
                  "add": {
                    "group1": [
                      "tag1"
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
