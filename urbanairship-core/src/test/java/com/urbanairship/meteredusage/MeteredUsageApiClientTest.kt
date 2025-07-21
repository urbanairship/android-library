package com.urbanairship.meteredusage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.UAirship
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import com.urbanairship.util.DateUtils
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MeteredUsageApiClientTest {
    private val testConfig = TestAirshipRuntimeConfig(
        RemoteConfig(
            airshipConfig = RemoteAirshipConfig(
                meteredUsageUrl = "https://test.metered.usage"
            )
        )
    )
    private val requestSession = TestRequestSession()

    private lateinit var client: MeteredUsageApiClient

    @Before
    public fun setUp() {
        client = MeteredUsageApiClient(testConfig, requestSession.toSuspendingRequestSession())
    }

    @Test
    public fun testEventUpload(): TestResult = runTest {
        val events = listOf(
            MeteredUsageEventEntity(
                eventId = "event1",
                entityId = "entity1",
                type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
                product = "product1",
                reportingContext = jsonMapOf("test" to "context").toJsonValue(),
                timestamp = 1L,
                contactId = "test-contact-id"),
            MeteredUsageEventEntity(
                eventId = "event2",
                entityId = "entity2",
                type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
                product = "product2",
                reportingContext = jsonMapOf("test2" to "context2").toJsonValue(),
                timestamp = 11L,
                contactId = "test-contact-id"),
            MeteredUsageEventEntity(
                eventId = "event3",
                entityId = "entity3",
                type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
                product = "product3",
                reportingContext = jsonMapOf("test3" to "context3").toJsonValue(),
                timestamp = 111L,
                contactId = "test-contact-id").withAnalyticsDisabled(),
        )

        val channelId = "test.channel.id"

        requestSession.addResponse(200)

        client.uploadEvents(events, channelId)

        val request = requestSession.lastRequest
        assertEquals("https://test.metered.usage/api/metered-usage", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals(RequestAuth.GeneratedAppToken, request.auth)
        assertEquals(mapOf(
            "X-UA-Lib-Version" to UAirship.getVersion(),
            "X-UA-Device-Family" to "android",
            "Content-Type" to "application/json",
            "X-UA-Channel-ID" to channelId,
            "Accept" to "application/vnd.urbanairship+json; version=3;"
        ), request.headers)

        assertEquals(jsonMapOf(
            "usage" to jsonListOf(
                jsonMapOf(
                    "product" to "product1",
                    "event_id" to "event1",
                    "reporting_context" to jsonMapOf("test" to "context"),
                    "occurred" to DateUtils.createIso8601TimeStamp(1),
                    "usage_type" to "iax_impression",
                    "entity_id" to "entity1",
                    "contact_id" to "test-contact-id"
                ),
                jsonMapOf(
                    "product" to "product2",
                    "event_id" to "event2",
                    "reporting_context" to jsonMapOf("test2" to "context2"),
                    "occurred" to DateUtils.createIso8601TimeStamp(11),
                    "usage_type" to "iax_impression",
                    "entity_id" to "entity2",
                    "contact_id" to "test-contact-id"
                ),
                jsonMapOf(
                    "product" to "product3",
                    "event_id" to "event3",
                    "usage_type" to "iax_impression",
                ),
            )
        ).toString(sortKeys = true), JsonValue.parseString(request.body?.content).toString(true))
    }
}
