/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.api

import com.urbanairship.BaseTestCase
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequest
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.SubscriptionListMutation.newSubscribeMutation
import com.urbanairship.channel.SubscriptionListMutation.newUnsubscribeMutation
import com.urbanairship.channel.TagGroupsMutation.newAddTagsMutation
import com.urbanairship.channel.TagGroupsMutation.newSetTagsMutation
import com.urbanairship.config.AirshipUrlConfig
import com.urbanairship.http.RequestFactory
import com.urbanairship.json.JsonValue
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

public class ChannelBulkUpdateApiClientTest : BaseTestCase() {

    private lateinit var client: ChannelBulkUpdateApiClient
    private lateinit var testRequest: TestRequest

    @Before
    public fun setup() {
        testRequest = TestRequest()
        val config = TestAirshipRuntimeConfig.newTestConfig().apply {
            urlConfig = AirshipUrlConfig.newBuilder()
                .setDeviceUrl("https://example.com")
                .build()
        }
        client = ChannelBulkUpdateApiClient(config, object : RequestFactory() {
            override fun createRequest(): TestRequest = testRequest
        })
    }

    @Test
    public fun testBulkUpdateRequestSuccess() {
        testRequest.responseStatus = 200

        val payload = ChannelBulkUpdateRequest(channelId = CHANNEL_ID)

        val response = client.update(CHANNEL_ID)

        assertEquals("PUT", testRequest.requestMethod)
        assertEquals("https://example.com/api/channels/sdk/batch/$CHANNEL_ID?platform=android", testRequest.url.toString())
        assertEquals(payload.toJsonValue().toString(), testRequest.requestBody)
        assertEquals(200, response.status)
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
        val CHANNEL_ID = "channelId"

        @Language("JSON")
        val EXPECTED_BATCH_UPDATE_JSON = """
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
