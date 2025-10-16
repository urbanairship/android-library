package com.urbanairship.deferred

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestRequestSession
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DeferredApiClientTest {

    internal lateinit var apiClient: DeferredApiClient
    internal val runtimeConfig: AirshipRuntimeConfig = mockk()
    internal val requestSession = TestRequestSession()

    @Before
    public fun setup() {
        every { runtimeConfig.platform } returns Platform.ANDROID
        apiClient = DeferredApiClient(runtimeConfig, requestSession.toSuspendingRequestSession())
    }

    @Test
    public fun testFullRequest(): TestResult = runTest {
        val channelId = "test-channel-id"
        val contactId = "test-contact-id"

        val userLocale: Locale = mockk()
        every { userLocale.country } returns "test-country"
        every { userLocale.language } returns "test-language"

        val stateOverrides = StateOverrides(
            appVersionName = "1.2.3",
            sdkVersion = "test-sdk",
            notificationOptIn = true,
            locale = userLocale
        )

        val triggerContext = DeferredTriggerContext(
            type = "unit-test", goal = 12.3, event = JsonValue.wrap("event")
        )

        val tagOverrides = listOf(
            TagGroupsMutation.newAddTagsMutation("group-add", setOf("tag-1", "tag-2")),
            TagGroupsMutation.newRemoveTagsMutation("group-remove", setOf("tag-3", "tag-4")),
            TagGroupsMutation.newSetTagsMutation("group-set", setOf("tag-5", "tag-6"))
        )

        val currentDate = Date().time
        val attributesOverrides = listOf(
            AttributeMutation.newSetAttributeMutation(
                "test-key", JsonValue.wrap("attr-value"), currentDate
            )
        )

        val expectedRequest = Request(
            url = Uri.parse("https://example.com"),
            method = "POST",
            auth = RequestAuth.ChannelTokenAuth(channelId),
            headers = mapOf("Accept" to "application/vnd.urbanairship+json; version=3;"),
            body = RequestBody.Json(
                jsonMapOf(
                    "platform" to "android",
                    "channel_id" to channelId,
                    "contact_id" to contactId,
                    "state_overrides" to jsonMapOf(
                        "app_version" to "1.2.3",
                        "sdk_version" to "test-sdk",
                        "notification_opt_in" to true,
                        "locale_language" to "test-language",
                        "locale_country" to "test-country"
                    ),
                    "trigger" to jsonMapOf(
                        "type" to "unit-test", "goal" to 12.3, "event" to "event"
                    ),
                    "tag_overrides" to listOf(
                        jsonMapOf("add" to mapOf("group-add" to setOf("tag-1", "tag-2"))),
                        jsonMapOf("remove" to mapOf("group-remove" to setOf("tag-3", "tag-4"))),
                        jsonMapOf("set" to mapOf("group-set" to setOf("tag-5", "tag-6")))
                    ),
                    "attribute_overrides" to listOf(
                        mapOf(
                            "value" to "attr-value",
                            "timestamp" to DateUtils.createIso8601TimeStamp(currentDate),
                            "key" to "test-key",
                            "action" to "set"
                        )
                    )
                )
            )
        )

        requestSession.addResponse(
            statusCode = 200, body = "{\"result\": \"test-ok\"}"
        )

        val response = apiClient.resolve(
            uri = expectedRequest.url!!,
            channelId = channelId,
            contactId = contactId,
            stateOverrides = stateOverrides,
            audienceOverrides = AudienceOverrides.Channel(
                tags = tagOverrides, attributes = attributesOverrides
            ),
            triggerContext = triggerContext
        )

        assertEquals(expectedRequest, requestSession.lastRequest)
        assertEquals(200, response.status)
        assertEquals("test-ok", response.value?.optMap()?.get("result")?.string)
    }

    @Test
    public fun testMinimalRequest(): TestResult = runTest {
        val channelId = "test-channel-id"

        val stateOverrides = StateOverrides(
            appVersionName = "1.2.3",
            sdkVersion = "test-sdk",
            notificationOptIn = true,
            locale = null
        )

        val expectedRequest = Request(
            url = Uri.parse("https://example.com"),
            method = "POST",
            auth = RequestAuth.ChannelTokenAuth(channelId),
            headers = mapOf("Accept" to "application/vnd.urbanairship+json; version=3;"),
            body = RequestBody.Json(
                jsonMapOf(
                    "platform" to "android",
                    "channel_id" to channelId,
                    "state_overrides" to jsonMapOf(
                        "app_version" to "1.2.3",
                        "sdk_version" to "test-sdk",
                        "notification_opt_in" to true,
                    )
                )
            )
        )

        requestSession.addResponse(
            statusCode = 200, body = "{\"result\": \"test-ok\"}"
        )

        val response = apiClient.resolve(
            uri = expectedRequest.url!!,
            channelId = channelId,
            contactId = null,
            stateOverrides = stateOverrides,
            audienceOverrides = null,
            triggerContext = null
        )

        assertEquals(expectedRequest, requestSession.lastRequest)
        assertEquals(200, response.status)
        assertEquals("test-ok", response.value?.optMap()?.get("result")?.string)
    }
}
