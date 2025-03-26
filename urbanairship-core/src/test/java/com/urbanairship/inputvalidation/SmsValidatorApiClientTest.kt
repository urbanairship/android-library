/* Copyright Airship and Contributors */

package com.urbanairship.inputvalidation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.UAirship
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import java.util.UUID
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class SmsValidatorApiClientTest {
    private val testConfig = TestAirshipRuntimeConfig(
        RemoteConfig(
            airshipConfig = RemoteAirshipConfig(
                deviceApiUrl = "https://device-api.urbanairship.com/"
            )
        )
    )
    private val requestSession = TestRequestSession()
    private lateinit var client: SmsValidatorApiClient

    @Before
    public fun setUp() {
        client = SmsValidatorApiClient(testConfig, requestSession.toSuspendingRequestSession())
    }

    @Test
    public fun testResponseParsing(): TestResult = runTest {
        requestSession.addResponse(200, "{\"status\": \"ok\", \"valid\": true, \"msisdn\": \"test-value\"}")

        var response = client.validateSmsWithSender("msisdn", "sender")
        assertEquals(SmsValidatorApiClient.Result.Valid("test-value"), response.value)

        requestSession.addResponse(200, "{\"valid\": false}")
        response = client.validateSmsWithSender("msisdn", "sender")
        assertEquals(SmsValidatorApiClient.Result.Invalid, response.value)

        requestSession.addResponse(200, "{\"valid\": true}")
        response = client.validateSmsWithSender("msisdn", "sender")
        assertEquals(null, response.value)

        requestSession.addResponse(200, "")
        response = client.validateSmsWithSender("msisdn", "sender")
        assertEquals(null, response.value)

        requestSession.addResponse(400, "")
        response = client.validateSmsWithSender("msisdn", "sender")
        assertEquals(null, response.value)
    }

    @Test
    public fun testSendSMSWithSender(): TestResult = runTest {
        val msisdn = UUID.randomUUID().toString()
        val sender = UUID.randomUUID().toString()

        requestSession.addResponse(200)

        client.validateSmsWithSender(msisdn, sender)

        val request = requestSession.lastRequest
        assertEquals("https://device-api.urbanairship.com/api/channels/sms/format", request.url.toString())
        assertEquals(mapOf(
            "Accept" to  "application/vnd.urbanairship+json; version=3;",
            "Content-Type" to "application/json",
            "X-UA-Lib-Version" to UAirship.getVersion(),
            "X-UA-Device-Family" to "android"
        ), request.headers)
        assertEquals("POST", request.method)
        assertEquals(RequestAuth.GeneratedAppToken, request.auth)
        assertEquals(RequestBody.Json(jsonMapOf(
            "msisdn" to msisdn,
            "sender" to sender
        )), request.body)
    }

    @Test
    public fun testSendSMSWithPrefix(): TestResult = runTest {
        val msisdn = UUID.randomUUID().toString()
        val prefix = UUID.randomUUID().toString()

        requestSession.addResponse(200)

        client.validateSmsWithPrefix(msisdn, prefix)

        val request = requestSession.lastRequest
        assertEquals("https://device-api.urbanairship.com/api/channels/sms/format", request.url.toString())
        assertEquals(mapOf(
            "Accept" to  "application/vnd.urbanairship+json; version=3;",
            "Content-Type" to "application/json",
            "X-UA-Lib-Version" to UAirship.getVersion(),
            "X-UA-Device-Family" to "android"
        ), request.headers)
        assertEquals("POST", request.method)
        assertEquals(RequestAuth.GeneratedAppToken, request.auth)
        assertEquals(RequestBody.Json(jsonMapOf(
            "msisdn" to msisdn,
            "prefix" to prefix
        )), request.body)
    }
}