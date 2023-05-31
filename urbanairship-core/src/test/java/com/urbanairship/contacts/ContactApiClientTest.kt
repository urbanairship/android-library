/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import android.net.Uri
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestClock
import com.urbanairship.TestRequestSession
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.config.AirshipUrlConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ContactApiClientTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"
    private val fakeContactId = "fake_contact_id"
    private val fakeEmail = "fake@email.com"
    private val fakeMsisdn = "123456789"
    private val fakeSenderId = "fake_sender_id"

    private val clock = TestClock()
    private var runtimeConfig: TestAirshipRuntimeConfig =
        TestAirshipRuntimeConfig.newTestConfig().also {
            it.urlConfig = AirshipUrlConfig.newBuilder().setDeviceUrl("https://example.com").build()
        }
    private val requestSession = TestRequestSession()

    private var client: ContactApiClient = ContactApiClient(
        runtimeConfig, requestSession.toSuspendingRequestSession(), clock
    )

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val usPacific = TimeZone.getTimeZone("US/Pacific")
        TimeZone.setDefault(usPacific)
    }

    @Test
    public fun testResolve(): TestResult = runTest {
        requestSession.addResponse(
            200, """
            {
              "ok": true,
              "contact": {
                "contact_id": "some contact id",
                "is_anonymous": true,
                "channel_association_timestamp": "2022-12-29T10:15:30.00"
              },
              "token": "some token",
              "token_expires_in": 3600000
            }
            """
        )

        val expectedRequestBody = jsonMapOf(
            "action" to jsonMapOf(
                "contact_id" to "some contact id", "type" to "resolve"
            ), "device_info" to jsonMapOf(
                "device_type" to "android"
            )
        )

        val expectedRequest = Request(
            url = Uri.parse("https://example.com/api/contacts/identify/v2"),
            method = "POST",
            body = RequestBody.Json(expectedRequestBody),
            auth = RequestAuth.GeneratedChannelToken(fakeChannelId),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
            )
        )

        val expectedResultValue = ContactApiClient.IdentityResult(
            contactId = "some contact id",
            isAnonymous = true,
            channelAssociatedDateMs = DateUtils.parseIso8601("2022-12-29T10:15:30.00"),
            token = "some token",
            tokenExpiryDateMs = clock.currentTimeMillis() + 3600000
        )

        val result = client.resolve(fakeChannelId, "some contact id")

        assertEquals(200, result.status)
        assertEquals(expectedResultValue, result.value)
        assertEquals(expectedRequest, requestSession.requests[0])
    }

    @Test
    public fun testIdentify(): TestResult = runTest {
        requestSession.addResponse(
            200, """
            {
              "ok": true,
              "contact": {
                "contact_id": "some contact id",
                "is_anonymous": false,
                "channel_association_timestamp": "2022-12-29T10:15:30.00"
              },
              "token": "some token",
              "token_expires_in": 3600000
            }
            """
        )

        val expectedRequestBody = jsonMapOf(
            "action" to jsonMapOf(
                "type" to "identify",
                "contact_id" to "some contact id",
                "named_user_id" to "some named user id",
            ), "device_info" to jsonMapOf(
                "device_type" to "android"
            )
        )

        val expectedRequest = Request(
            url = Uri.parse("https://example.com/api/contacts/identify/v2"),
            method = "POST",
            body = RequestBody.Json(expectedRequestBody),
            auth = RequestAuth.GeneratedChannelToken(fakeChannelId),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
            )
        )

        val expectedResultValue = ContactApiClient.IdentityResult(
            contactId = "some contact id",
            isAnonymous = false,
            channelAssociatedDateMs = DateUtils.parseIso8601("2022-12-29T10:15:30.00"),
            token = "some token",
            tokenExpiryDateMs = clock.currentTimeMillis() + 3600000
        )

        val result = client.identify(fakeChannelId, "some contact id", "some named user id")

        assertEquals(200, result.status)
        assertEquals(expectedResultValue, result.value)
        assertEquals(expectedRequest, requestSession.requests[0])
    }

    @Test
    public fun testReset(): TestResult = runTest {
        requestSession.addResponse(
            200, """
            {
              "ok": true,
              "contact": {
                "contact_id": "some contact id",
                "is_anonymous": true,
                "channel_association_timestamp": "2022-12-29T10:15:30.00"
              },
              "token": "some token",
              "token_expires_in": 3600000
            }
            """
        )

        val expectedRequestBody = jsonMapOf(
            "action" to jsonMapOf(
                "type" to "reset"
            ), "device_info" to jsonMapOf(
                "device_type" to "android"
            )
        )

        val expectedRequest = Request(
            url = Uri.parse("https://example.com/api/contacts/identify/v2"),
            method = "POST",
            body = RequestBody.Json(expectedRequestBody),
            auth = RequestAuth.GeneratedChannelToken(fakeChannelId),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
            )
        )

        val expectedResultValue = ContactApiClient.IdentityResult(
            contactId = "some contact id",
            isAnonymous = true,
            channelAssociatedDateMs = DateUtils.parseIso8601("2022-12-29T10:15:30.00"),
            token = "some token",
            tokenExpiryDateMs = clock.currentTimeMillis() + 3600000
        )

        val result = client.reset(fakeChannelId)

        assertEquals(200, result.status)
        assertEquals(expectedResultValue, result.value)
        assertEquals(expectedRequest, requestSession.requests[0])
    }

    @Test
    public fun testRegisterOpenChannel(): TestResult = runTest {
        // Register
        requestSession.addResponse(200, "{ \"ok\": true, \"channel_id\": \"fake_channel_id\"}")

        // Update
        requestSession.addResponse(200)

        val options = OpenChannelRegistrationOptions.options(
            "email", mapOf(
                "identifier_key" to "identifier_value"
            )
        )

        val result =
            client.registerOpen(fakeContactId, fakeEmail, options, Locale("en", "US"))
        assertEquals(200, result.status)

        val expectedResultValue = AssociatedChannel("fake_channel_id", ChannelType.OPEN)
        assertEquals(expectedResultValue, result.value)
        assertEquals(200, result.status)

        val expectedCreateRequest = Request(
            url = Uri.parse("https://example.com/api/channels/restricted/open/"),
            method = "POST",
            body = RequestBody.Json(
                """
                   {
                       "channel":{
                          "address":"fake@email.com",
                          "timezone":"US\/Pacific",
                          "opt_in":true,
                          "type":"open",
                          "locale_language":"en",
                          "open":{
                             "open_platform_name":"email",
                             "identifiers":{
                                "identifier_key":"identifier_value"
                             }
                          },
                          "locale_country":"US"
                       }
                   }
                """
            ),
            auth = RequestAuth.GeneratedAppToken,
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Appkey" to runtimeConfig.configOptions.appKey
            )
        )
        assertEquals(expectedCreateRequest, requestSession.requests[0])

        val expectedUpdateRequest = Request(
            url = Uri.parse("https://example.com/api/contacts/fake_contact_id"),
            method = "POST",
            body = RequestBody.Json(
                """
                      {
                       "associate":[
                          {
                             "channel_id":"fake_channel_id",
                             "device_type":"open"
                          }
                       ]
                    }
                """
            ),
            auth = RequestAuth.ContactTokenAuth(fakeContactId),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Appkey" to runtimeConfig.configOptions.appKey
            )
        )
        assertEquals(expectedUpdateRequest, requestSession.requests[1])
    }

    @Test
    public fun testRegisterEmail(): TestResult = runTest {
        // Register
        requestSession.addResponse(200, "{ \"ok\": true, \"channel_id\": \"fake_channel_id\"}")

        // Update
        requestSession.addResponse(200)

        val options = EmailRegistrationOptions.options(
            Date(clock.currentTimeMillis()), jsonMapOf(
                "properties_key" to "properties_value"
            ), false
        )

        val result = client.registerEmail(fakeContactId, fakeEmail, options, Locale("en", "US"))
        assertEquals(200, result.status)

        val expectedResultValue = AssociatedChannel("fake_channel_id", ChannelType.EMAIL)
        assertEquals(expectedResultValue, result.value)
        assertEquals(200, result.status)

        val expectedCreateRequest = Request(
            url = Uri.parse("https://example.com/api/channels/restricted/email/"),
            method = "POST",
            body = RequestBody.Json(
                """
                    {
                       "channel":{
                          "type":"email",
                          "transactional_opted_in":"${DateUtils.createIso8601TimeStamp(clock.currentTimeMillis())}",
                          "address":fake@email.com,
                          "timezone":"US\/Pacific",
                          "locale_language":"en",
                          "locale_country":"US"
                       },
                       "properties":{
                          "properties_key":"properties_value"},
                       "opt_in_mode":"classic"
                    }
                """
            ),
            auth = RequestAuth.GeneratedAppToken,
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Appkey" to runtimeConfig.configOptions.appKey
            )
        )
        assertEquals(expectedCreateRequest, requestSession.requests[0])

        val expectedUpdateRequest = Request(
            url = Uri.parse("https://example.com/api/contacts/fake_contact_id"),
            method = "POST",
            body = RequestBody.Json(
                """
                      {
                       "associate":[
                          {
                             "channel_id":"fake_channel_id",
                             "device_type":"email"
                          }
                       ]
                    }
                """
            ),
            auth = RequestAuth.ContactTokenAuth(fakeContactId),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Appkey" to runtimeConfig.configOptions.appKey
            )
        )
        assertEquals(expectedUpdateRequest, requestSession.requests[1])
    }

    @Test
    public fun testRegisterSms(): TestResult = runTest {
        // Register
        requestSession.addResponse(200, "{ \"ok\": true, \"channel_id\": \"fake_channel_id\"}")

        // Update
        requestSession.addResponse(200)

        val options = SmsRegistrationOptions.options(fakeSenderId)

        val result = client.registerSms(fakeContactId, fakeMsisdn, options, Locale("en", "US"))
        assertEquals(200, result.status)

        val expectedResultValue = AssociatedChannel("fake_channel_id", ChannelType.SMS)
        assertEquals(expectedResultValue, result.value)
        assertEquals(200, result.status)

        val expectedCreateRequest = Request(
            url = Uri.parse("https://example.com/api/channels/restricted/sms/"),
            method = "POST",
            body = RequestBody.Json(
                """
                    {
                      "msisdn":"$fakeMsisdn",
                      "sender":"$fakeSenderId",
                      "timezone":"US\/Pacific",
                      "locale_language":"en",
                      "locale_country":"US"
                    }
                """
            ),
            auth = RequestAuth.GeneratedAppToken,
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Appkey" to runtimeConfig.configOptions.appKey
            )
        )
        assertEquals(expectedCreateRequest, requestSession.requests[0])

        val expectedUpdateRequest = Request(
            url = Uri.parse("https://example.com/api/contacts/fake_contact_id"),
            method = "POST",
            body = RequestBody.Json(
                """
                      {
                       "associate":[
                          {
                             "channel_id":"fake_channel_id",
                             "device_type":"sms"
                          }
                       ]
                    }
                """
            ),
            auth = RequestAuth.ContactTokenAuth(fakeContactId),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Appkey" to runtimeConfig.configOptions.appKey
            )
        )
        assertEquals(expectedUpdateRequest, requestSession.requests[1])
    }

    @Test
    public fun testRegisterChannelFails(): TestResult = runTest {
        // Register
        requestSession.addResponse(400)

        val options = SmsRegistrationOptions.options(fakeSenderId)
        val result = client.registerSms(fakeContactId, fakeMsisdn, options, Locale("en", "US"))
        assertEquals(400, result.status)
        assertNull(result.value)
    }

    @Test
    public fun testUpdate(): TestResult = runTest {
        // Update
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
            ScopedSubscriptionListMutation.newSubscribeMutation(
                "burgers", Scope.APP, 100
            ), ScopedSubscriptionListMutation.newUnsubscribeMutation(
                "burritos", Scope.SMS, 100
            )
        )

        val result = client.update(fakeContactId, tags, attributes, subscriptions)
        assertEquals(200, result.status)

        val expectedRequest = Request(
            url = Uri.parse("https://example.com/api/contacts/fake_contact_id"),
            method = "POST",
            body = RequestBody.Json(
                """
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
                             "scope":"app",
                             "timestamp":"1970-01-01T00:00:00"
                          },
                          {
                             "action":"unsubscribe",
                             "list_id":"burritos",
                             "scope":"sms",
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
            ),
            auth = RequestAuth.ContactTokenAuth(fakeContactId),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Appkey" to runtimeConfig.configOptions.appKey
            )
        )

        assertEquals(expectedRequest, requestSession.requests[0])
    }

    @Test
    public fun testAssociateChannel(): TestResult = runTest {
        // Update
        requestSession.addResponse(200)

        val result = client.associatedChannel(fakeContactId, fakeChannelId, ChannelType.OPEN)

        val expectedResultValue = AssociatedChannel(fakeChannelId, ChannelType.OPEN)
        assertEquals(expectedResultValue, result.value)
        assertEquals(200, result.status)

        val expectedUpdateRequest = Request(
            url = Uri.parse("https://example.com/api/contacts/fake_contact_id"),
            method = "POST",
            body = RequestBody.Json(
                """
                      {
                       "associate":[
                          {
                             "channel_id":"$fakeChannelId",
                             "device_type":"open"
                          }
                       ]
                    }
                """
            ),
            auth = RequestAuth.ContactTokenAuth(fakeContactId),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Appkey" to runtimeConfig.configOptions.appKey
            )
        )
        assertEquals(expectedUpdateRequest, requestSession.requests[0])
    }
}
