/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import android.net.Uri
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import com.urbanairship.util.DateUtils
import java.util.UUID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ContactChannelsApiClientTest {

    private var runtimeConfig: TestAirshipRuntimeConfig = TestAirshipRuntimeConfig(
        RemoteConfig(
            airshipConfig = RemoteAirshipConfig(
                deviceApiUrl = "https://example.com"
            )
        )
    )

    private val requestSession = TestRequestSession()

    private val contactId = UUID.randomUUID().toString()

    private var client = ContactChannelsApiClient(
        runtimeConfig, requestSession.toSuspendingRequestSession()
    )

    @Test
    public fun testFetch(): TestResult = runTest {
        requestSession.addResponse(
            200, """
            {
              "ok": true,
              "channels": [
                  {
                    "type": "sms",
                    "channel_id": "some-sms-channel",
                    "msisdn": "some-sms-masked-address",
                    "sender": "123456",
                    "opt_in": true
                  },
                  {
                    "type": "email",
                    "channel_id": "some-email-channel",
                    "email_address": "some-email-masked-address",
                    "commercial_opted_in": "${DateUtils.createIso8601TimeStamp(1000)}",
                    "commercial_opted_out": "${DateUtils.createIso8601TimeStamp(2000)}",
                    "transactional_opted_in": "${DateUtils.createIso8601TimeStamp(3000)}",
                    "transactional_opted_out": "${DateUtils.createIso8601TimeStamp(4000)}"
                   }
                ]
            }
            """
        )

        val expectedRequest = Request(
            url = Uri.parse("https://example.com/api/contacts/associated_types/$contactId"),
            method = "GET",
            auth = RequestAuth.ContactTokenAuth(contactId),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
            )
        )

        val expectedResultValue = listOf(
            ContactChannel.Sms(
                ContactChannel.Sms.RegistrationInfo.Registered(
                    channelId = "some-sms-channel",
                    maskedAddress = "some-sms-masked-address",
                    senderId = "123456",
                    isOptIn = true
                )
            ),
            ContactChannel.Email(
               ContactChannel.Email.RegistrationInfo.Registered(
                    channelId = "some-email-channel",
                    maskedAddress = "some-email-masked-address",
                    commercialOptedIn = 1000,
                    commercialOptedOut = 2000,
                    transactionalOptedIn = 3000,
                    transactionalOptedOut = 4000,
                )
            )
        )

        val result = client.fetch(contactId)

        assertEquals(200, result.status)
        assertEquals(expectedResultValue, result.value)
        assertEquals(expectedRequest, requestSession.requests[0])
    }

    @Test
    public fun testFetchParseError(): TestResult = runTest {
        requestSession.addResponse(
            200, """
            {
              "ok": true,
            """
        )

        val result = client.fetch(contactId)
        assertNull(result.status)
        assertNotNull(result.exception)
    }

    @Test
    public fun testFetchError(): TestResult = runTest {
        requestSession.addResponse(
            400, "nope"
        )

        val result = client.fetch(contactId)
        assertEquals(400, result.status)
        assertNull(result.value)
    }
}
