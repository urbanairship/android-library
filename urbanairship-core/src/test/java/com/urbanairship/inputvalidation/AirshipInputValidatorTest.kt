/* Copyright Airship and Contributors */

package com.urbanairship.inputvalidation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PendingResult
import com.urbanairship.channel.SmsValidationHandler
import com.urbanairship.http.RequestResult
import java.util.UUID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AirshipInputValidatorTest {
    private val mockApiClient: SmsValidatorApiInterface = mockk()
    private lateinit var defaultValidator: AirshipInputValidation.Validator

    @Before
    public fun setUp() {
        defaultValidator = DefaultInputValidator(mockApiClient)
    }

    @Test
    public fun testValidEmailAddresses(): TestResult = runTest {
        listOf(
            "simple@example.com",
            "very.common@example.com",
            "disposable.style.email.with+symbol@example.com",
            "other.email-with-hyphen@example.com",
            "fully-qualified-domain@example.com",
            "user.name+tag+sorting@example.com",
            "x@y.z",
            "user123@domain.com",
            "user.name@domain.com",
            "a@domain.com",
            "user@sub.domain.com",
            "user-name@domain.com",
            "user.@domain.com",
            ".user@domain.com",
            "user@.domain.com",
            "user@domain..com",
            "user..name@domain.com",
            "user+name@domain.com",
            "user!#$%&'*+-/=?^_`{|}~@domain.com"
        ).forEach {
            val result = defaultValidator.validate(
                request = AirshipInputValidation.Request.ValidateEmail(
                    email = AirshipInputValidation.Request.Email(it)
                )
            )

            when(result) {
                AirshipInputValidation.Result.Invalid -> fail("Failed to validate $it")
                is AirshipInputValidation.Result.Valid -> assertEquals(it, result.address)
            }
        }
    }

    @Test
    public fun testInvalidEmailAddresses(): TestResult = runTest {
        listOf(
            "user",
            "user ",
            "",
            "user@",
            "user@domain",
            "user @domain.com",
            "user@ domain.com",
            "us er@domain.com",
            "user@do main.com",
            "user@domain.com.",
            "user@domain@example.com"
        ).forEach {
            val result = defaultValidator.validate(
                request = AirshipInputValidation.Request.ValidateEmail(
                    email = AirshipInputValidation.Request.Email(it)
                )
            )

            when(result) {
                AirshipInputValidation.Result.Invalid -> {}
                is AirshipInputValidation.Result.Valid -> fail("False valid value $it")
            }
        }
    }

    @Test
    public fun testEmailFormatting(): TestResult = runTest {
        mapOf(
            " user@domain.com" to "user@domain.com",
            "user@domain.com   " to "user@domain.com",
            "      user@domain.com   " to "user@domain.com",
            "\n      user@domain.com   \n" to "user@domain.com"
        ).forEach {
            val result = defaultValidator.validate(
                request = AirshipInputValidation.Request.ValidateEmail(
                    email = AirshipInputValidation.Request.Email(it.key)
                )
            )

            when(result) {
                AirshipInputValidation.Result.Invalid -> fail("Failed to validate $it")
                is AirshipInputValidation.Result.Valid -> {
                    assertEquals(it.value, result.address)
                }
            }
        }
    }

    @Test
    public fun testEmailOverride(): TestResult = runTest {
        val overrideValue = UUID.randomUUID().toString()

        val originalRequest = AirshipInputValidation.Request.ValidateEmail(
            email = AirshipInputValidation.Request.Email("test@email.com")
        )

        val validator = DefaultInputValidator(
            apiClient = mockk(),
            overrides = object : AirshipValidationOverride{
                override fun getOverrides(
                    request: AirshipInputValidation.Request
                ): PendingResult<AirshipInputValidation.Override> {
                    assertEquals(originalRequest, request)

                    return PendingResult<AirshipInputValidation.Override>().apply {
                        setResult(AirshipInputValidation.Override.Replace(
                            result = AirshipInputValidation.Result.Valid(overrideValue)
                        ))
                    }
                }
            }
        )

        assertEquals(AirshipInputValidation.Result.Valid(overrideValue), validator.validate(originalRequest))
    }

    @Test
    public fun testEmailOverrideFallback(): TestResult = runTest {
        val originalRequest = AirshipInputValidation.Request.ValidateEmail(
            email = AirshipInputValidation.Request.Email(" some-valid@email.com ")
        )

        val validator = DefaultInputValidator(
            apiClient = mockk(),
            overrides = object : AirshipValidationOverride {
                override fun getOverrides(request: AirshipInputValidation.Request): PendingResult<AirshipInputValidation.Override> {
                    assertEquals(request, originalRequest)
                    return PendingResult<AirshipInputValidation.Override>().apply {
                        setResult(AirshipInputValidation.Override.UseDefault)
                    }
                }
            }
        )

        assertEquals(AirshipInputValidation.Result.Valid("some-valid@email.com"), validator.validate(originalRequest))
    }

    @Test
    public fun testSMSValidationWithSenderID(): TestResult = runTest {
        val request = defaultSmsRequest

        coEvery { mockApiClient.validateSmsWithSender(any(), any()) } answers {
            assertEquals("555555555", firstArg())
            assertEquals("some sender", secondArg())

            RequestResult(
                status = 200,
                value = SmsValidatorApiClient.Result.Valid("+1555555555"),
                headers = emptyMap(),
                body = null
            )
        }

        assertEquals(AirshipInputValidation.Result.Valid("+1555555555"), defaultValidator.validate(request))

        coVerify(exactly = 1) { mockApiClient.validateSmsWithSender(any(), any()) }
    }

    @Test
    public fun testSMSValidationWithPrefix(): TestResult = runTest {
        val request = AirshipInputValidation.Request.ValidateSms(
            sms = AirshipInputValidation.Request.Sms(
                rawInput = "555555555",
                validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Prefix("+1")
            )
        )

        coEvery { mockApiClient.validateSmsWithPrefix(any(), any()) } answers {
            assertEquals("555555555", firstArg())
            assertEquals("+1", secondArg())

            RequestResult(
                status = 200,
                value = SmsValidatorApiClient.Result.Valid("+1555555555"),
                headers = emptyMap(),
                body = null
            )
        }

        assertEquals(AirshipInputValidation.Result.Valid("+1555555555"), defaultValidator.validate(request))
        coVerify(exactly = 1) { mockApiClient.validateSmsWithPrefix(any(), any()) }
    }

    @Test
    public fun testSMSValidationWith400Response(): TestResult = runTest {
        val request = defaultSmsRequest

        coEvery { mockApiClient.validateSmsWithSender(any(), any()) } answers {
            RequestResult(
                status = (400..499).random(),
                value = null,
                headers = emptyMap(),
                body = null
            )
        }

        assertEquals(AirshipInputValidation.Result.Invalid, defaultValidator.validate(request))
        coVerify { mockApiClient.validateSmsWithSender(any(), any()) }
    }

    @Test
    public fun testSMSValidationWith500Response(): TestResult = runTest {
        val request = defaultSmsRequest

        coEvery { mockApiClient.validateSmsWithSender(any(), any()) } answers {
            RequestResult(
                status = (500..599).random(),
                value = null,
                headers = emptyMap(),
                body = null
            )
        }

        try {
            defaultValidator.validate(request)
            fail()
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    public fun testSMSValidationWith200ResponseNoResult(): TestResult = runTest {
        val request = defaultSmsRequest

        coEvery { mockApiClient.validateSmsWithSender(any(), any()) } answers {
            RequestResult(
                status = 200,
                value = null,
                headers = emptyMap(),
                body = null
            )
        }

        try {
            defaultValidator.validate(request)
            fail()
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    public fun testValidationHints(): TestResult = runTest {
        coEvery { mockApiClient.validateSmsWithSender(any(), any()) } answers {
            RequestResult(
                status = 200,
                value = SmsValidatorApiClient.Result.Valid("+1555555555"),
                headers = emptyMap(),
                body = null
            )
        }

        val compareResult: suspend (input: String, result: AirshipInputValidation.Result) -> Unit = { input, result ->
            val request = AirshipInputValidation.Request.ValidateSms(
                sms = AirshipInputValidation.Request.Sms(
                    rawInput = input,
                    validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Sender(
                        senderId = "some sender"
                    ),
                    validationHints = AirshipInputValidation.Request.Sms.ValidationHints(
                        minDigits = 4,
                        maxDigits = 6
                    )
                )
            )

            assertEquals(result, defaultValidator.validate(request))
        }

        (0..3)
            .map(::randomNumber)
            .forEach { number ->
                compareResult(number, AirshipInputValidation.Result.Invalid)
            }

        (4..6)
            .map(::randomNumber)
            .forEach { number ->
                compareResult(number, AirshipInputValidation.Result.Valid("+1555555555"))
            }

        (7..10)
            .map(::randomNumber)
            .forEach { number ->
                compareResult(number, AirshipInputValidation.Result.Invalid)
            }

        val request = AirshipInputValidation.Request.ValidateSms(
            sms = AirshipInputValidation.Request.Sms(
                rawInput = "a1b2c3d4b5e6",
                validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Sender(
                    senderId = "some sender"
                ),
                validationHints = AirshipInputValidation.Request.Sms.ValidationHints(
                    minDigits = 4,
                    maxDigits = 6
                )
            )
        )
        assertEquals(
            AirshipInputValidation.Result.Valid("+1555555555"),
            defaultValidator.validate(request)
        )
    }

    @Test
    public fun testSMSOverride(): TestResult = runTest {
        val request = defaultSmsRequest

        val validator = DefaultInputValidator(
            apiClient = mockApiClient,
            overrides = object : AirshipValidationOverride {
                override fun getOverrides(request: AirshipInputValidation.Request): PendingResult<AirshipInputValidation.Override> {
                    return PendingResult<AirshipInputValidation.Override>().apply {
                        setResult(AirshipInputValidation.Override.Replace(
                            AirshipInputValidation.Result.Valid("some other result")
                        ))
                    }
                }

            }
        )

        assertEquals(
            AirshipInputValidation.Result.Valid("some other result"),
            validator.validate(request)
        )
    }

    @Test
    public fun testSMSOverrideFallback(): TestResult = runTest {
        val request = defaultSmsRequest

        val validator = DefaultInputValidator(
            apiClient = mockApiClient,
            overrides = object : AirshipValidationOverride {
                override fun getOverrides(request: AirshipInputValidation.Request): PendingResult<AirshipInputValidation.Override> {
                    return PendingResult<AirshipInputValidation.Override>().apply {
                        setResult(AirshipInputValidation.Override.UseDefault)
                    }
                }
            }
        )

        coEvery { mockApiClient.validateSmsWithSender(any(), any()) } answers {
            RequestResult(
                status = 200,
                value = SmsValidatorApiClient.Result.Valid("api result"),
                headers = emptyMap(),
                body = null
            )
        }

        assertEquals(
            AirshipInputValidation.Result.Valid("api result"),
            validator.validate(request)
        )

        coVerify(exactly = 1) { mockApiClient.validateSmsWithSender(any(), any()) }
    }

    @Test
    public fun testSMSLegacyDelegate(): TestResult = runTest {
        val mockLegacyDelegate: SmsValidationHandler = mockk()
        defaultValidator.setLegacySmsDelegate(mockLegacyDelegate)

        coEvery { mockLegacyDelegate.validateSms(any(), any()) } answers {
            assertEquals("15558675309", firstArg())
            assertEquals("some sender", secondArg())

            true
        }

        listOf(
            "1 555 867 5309",
            "1.555.867.5309",
            "1-555-867-5309",
            "5 5 5 8 6  7 5309"
        )
            .map { number ->
                AirshipInputValidation.Request.ValidateSms(
                    sms = AirshipInputValidation.Request.Sms(
                        rawInput = number,
                        validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Sender(
                            senderId = "some sender",
                            prefix = "+1"
                        )
                    )
                )
            }
            .map { defaultValidator.validate(it) }
            .forEach { result ->
                assertEquals(AirshipInputValidation.Result.Valid("15558675309"), result)
            }

        coVerify(exactly = 4) { mockLegacyDelegate.validateSms(any(), any()) }
    }

    @Test
    public fun testLegacySMSDelegateInvalidates(): TestResult = runTest {
        val request = AirshipInputValidation.Request.ValidateSms(
            sms = AirshipInputValidation.Request.Sms(
                rawInput = "123456",
                validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Sender(
                    senderId = "some sender",
                    prefix = "+1"
                )
            )
        )

        val legacyDelegate: SmsValidationHandler = mockk()
        defaultValidator.setLegacySmsDelegate(legacyDelegate)

        coEvery { legacyDelegate.validateSms(any(), any()) } answers {
            assertEquals("123456", firstArg())
            assertEquals("some sender", secondArg())

            false
        }

        assertEquals(AirshipInputValidation.Result.Invalid, defaultValidator.validate(request))
        coVerify(exactly = 1) { legacyDelegate.validateSms(any(), any()) }
    }

    @Test
    public fun testLegacySMSDelegateNoPrefix(): TestResult = runTest {
        val request = AirshipInputValidation.Request.ValidateSms(
            sms = AirshipInputValidation.Request.Sms(
                rawInput = "123456",
                validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Sender(
                    senderId = "some sender"
                )
            )
        )

        val legacyDelegate: SmsValidationHandler = mockk()
        defaultValidator.setLegacySmsDelegate(legacyDelegate)

        coEvery { legacyDelegate.validateSms(any(), any()) } answers {
            assertEquals("123456", firstArg())
            assertEquals("some sender", secondArg())

            false
        }

        assertEquals(AirshipInputValidation.Result.Invalid, defaultValidator.validate(request))
        coVerify(exactly = 1) { legacyDelegate.validateSms(any(), any()) }
    }

    @Test
    public fun testLegacySMSDelegatePrefix(): TestResult = runTest {
        val request = AirshipInputValidation.Request.ValidateSms(
            sms = AirshipInputValidation.Request.Sms(
                rawInput = "123456",
                validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Prefix(
                    prefix = "+1"
                )
            )
        )

        val legacyDelegate: SmsValidationHandler = mockk()
        defaultValidator.setLegacySmsDelegate(legacyDelegate)

        coEvery { legacyDelegate.validateSms(any(), any()) } returns false

        coEvery { mockApiClient.validateSmsWithPrefix(any(), any()) } answers {
            assertEquals("123456", firstArg())
            assertEquals("+1", secondArg())

            RequestResult(
                status = 200,
                value = SmsValidatorApiClient.Result.Valid("api result"),
                headers = emptyMap(),
                body = null
            )
        }

        assertEquals(
            AirshipInputValidation.Result.Valid("api result"),
            defaultValidator.validate(request)
        )

        coVerify(exactly = 0) { legacyDelegate.validateSms(any(), any()) }
    }

    private val defaultSmsRequest: AirshipInputValidation.Request = AirshipInputValidation.Request.ValidateSms(
        sms = AirshipInputValidation.Request.Sms(
            rawInput = "555555555",
            validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Sender(
                senderId = "some sender"
            )
        )
    )

    private fun randomNumber(length: Int): String {
        val digits = "0123456789"
        var result = ""
        (0..<length).forEach { _ -> result += digits.random() }

        return result
    }
}
