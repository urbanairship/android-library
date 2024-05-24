package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ValidatorTest {
    private val msisdn = "1234567890"
    private val sender = "TestSender"
    private val payload = SmsValidationBody(sender, msisdn)
    private val config = TestAirshipRuntimeConfig()
    private val requestSession = TestRequestSession()
    private val testDispatcher = StandardTestDispatcher()

    private val apiClient = SmsValidatorAPIClientImpl(config, requestSession.toSuspendingRequestSession())
    private val smsValidator = SmsValidatorImpl(apiClient)

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
        config.updateRemoteConfig(
            RemoteConfig(
                airshipConfig = RemoteAirshipConfig(
                    deviceApiUrl = "https://example.com"
                )
            )
        )
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testValidateSms(): TestResult = runTest {
        requestSession.addResponse(200, """{ "ok": true, "valid": true }""")
        val isValid = smsValidator.validateSms(msisdn, sender)

        assertEquals(true, isValid)

        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals(
            "https://example.com/api/channels/sms/validate", requestSession.lastRequest.url.toString()
        )
        assertEquals(RequestAuth.GeneratedAppToken, requestSession.lastRequest.auth)
        assertEquals(requestSession.lastRequest.body, RequestBody.Json(payload))
    }

    @Test
    public fun testValidateSmsFailure(): TestResult = runTest {
        requestSession.addResponse(200, """"{ "ok": true, "valid": false }""")
        val isValid = smsValidator.validateSms(msisdn, sender)

        assertEquals(false, isValid)

        assertEquals("POST", requestSession.lastRequest.method)
        assertEquals(
            "https://example.com/api/channels/sms/validate", requestSession.lastRequest.url.toString()
        )
        assertEquals(RequestAuth.GeneratedAppToken, requestSession.lastRequest.auth)
        assertEquals(requestSession.lastRequest.body, RequestBody.Json(payload))
    }

    @Test
    public fun testValidateSmsValidResult(): TestResult = runTest {
        requestSession.addResponse(200, """{ "ok": true, "valid": true }""")
        assertEquals(true, smsValidator.validateSms(msisdn, sender))
    }

    @Test
    public fun testValidateSmsInvalidResult(): TestResult = runTest {
        requestSession.addResponse(200, """{ "ok": true, "valid": false }""")
        assertEquals(false, smsValidator.validateSms(msisdn, sender))

        requestSession.addResponse(200, """{ "ok": false, "valid": true }""")
        assertEquals(false, smsValidator.validateSms(msisdn, sender))

        requestSession.addResponse(200, """"{ "ok": false, "valid": false }""")
        assertEquals(false, smsValidator.validateSms(msisdn, sender))

        requestSession.addResponse(500, """{ "ok": true, "valid": true }""")
        assertEquals(false, smsValidator.validateSms(msisdn, sender))
    }

    @Test
    public fun testValidateSmsException(): TestResult = runTest {
        config.updateRemoteConfig(RemoteConfig())
        requestSession.addResponse(200, """{ "ok": true, "valid": true }""")
        val isValid = smsValidator.validateSms(msisdn, sender)

        assertEquals(false, isValid)
    }

    @Test
    public fun testCacheResult(): TestResult = runTest {
        requestSession.addResponse(200, """{ "ok": true, "valid": true }""")
        val isValidFirstCall = smsValidator.validateSms(msisdn, sender)
        assertEquals(true, isValidFirstCall)

        requestSession.addResponse(200, """{ "ok": true, "valid": false }""")
        val isValidSecondCall = smsValidator.validateSms(msisdn, sender)
        assertEquals(true, isValidSecondCall)
    }

    @Test
    public fun testListenerCalledWhenSet(): TestResult = runTest {
        val listener = mock(SmsValidationListener::class.java)
        smsValidator.listener = listener

        whenever(listener.validateSms(msisdn, sender)).thenReturn(true)

        val isValid = smsValidator.validateSms(msisdn, sender)

        verify(listener).validateSms(msisdn, sender)
        assertEquals(true, isValid)
    }
}
