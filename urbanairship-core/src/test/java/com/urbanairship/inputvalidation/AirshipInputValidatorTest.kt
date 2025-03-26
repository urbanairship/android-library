/* Copyright Airship and Contributors */

package com.urbanairship.inputvalidation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PendingResult
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
                        result = AirshipInputValidation.Override.Replace(
                            result = AirshipInputValidation.Result.Valid(overrideValue)
                        )
                    }
                }
            }
        )

        checkValidAddress(originalRequest, overrideValue, validator)
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
                        result = AirshipInputValidation.Override.UseDefault
                    }
                }
            }
        )

        checkValidAddress(originalRequest, "some-valid@email.com", validator)
    }

    @Test
    public fun testSMSValidationWithSenderID(): TestResult = runTest {
        val request = AirshipInputValidation.Request.ValidateSms(
            sms = AirshipInputValidation.Request.Sms(
                rawInput = "555555555",
                validationOptions = AirshipInputValidation.Request.Sms.ValidationOptions.Sender(
                    senderId = "some sender"
                )
            )
        )

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

        checkValidAddress(request, "+1555555555")

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

        checkValidAddress(request, "+1555555555")
        coVerify(exactly = 1) { mockApiClient.validateSmsWithPrefix(any(), any()) }
    }

    /*
    @Test("Test sms validation 4xx response should return invalid")
    func testSMSValidationWith400Response() async throws {
        let request = AirshipInputValidation.Request.sms(
            .init(
                rawInput: "555555555",
                validationOptions: .sender(senderID: "some sender", prefix: nil)
            )
        )

        let validator = AirshipInputValidation.DefaultValidator(
            smsValidatorAPIClient: smsValidatorAPIClient
        )

        try await confirmation { confirmation in
            await smsValidatorAPIClient.setOnValidate { apiRequest in
                #expect(apiRequest.msisdn == "555555555")
                #expect(apiRequest.sender == "some sender")
                confirmation.confirm()
                return AirshipHTTPResponse(
                    result: nil,
                    statusCode: Int.random(in: 400...499),
                    headers: [:]
                )
            }

            let result = try await validator.validateRequest(request)
            #expect(result == .invalid)
        }
    }

    @Test("Test sms validation 5xx should throw")
    func testSMSValidationWith500Response() async throws {
        let request = AirshipInputValidation.Request.sms(
            .init(
                rawInput: "555555555",
                validationOptions: .sender(senderID: "some sender", prefix: nil)
            )
        )

        let validator = AirshipInputValidation.DefaultValidator(
            smsValidatorAPIClient: smsValidatorAPIClient
        )

        await confirmation { confirmation in
            await smsValidatorAPIClient.setOnValidate { apiRequest in
                #expect(apiRequest.msisdn == "555555555")
                #expect(apiRequest.sender == "some sender")
                confirmation.confirm()
                return AirshipHTTPResponse(
                    result: nil,
                    statusCode: Int.random(in: 500...599),
                    headers: [:]
                )
            }

            await #expect(throws: NSError.self) {
                _ = try await validator.validateRequest(request)
            }
        }
    }

    @Test("Test sms validation 2xx without a result should throw")
    func testSMSValidationWith200ResponseNoResult() async throws {
        let request = AirshipInputValidation.Request.sms(
            .init(
                rawInput: "555555555",
                validationOptions: .sender(senderID: "some sender", prefix: nil)
            )
        )

        let validator = AirshipInputValidation.DefaultValidator(
            smsValidatorAPIClient: smsValidatorAPIClient
        )

        await confirmation { confirmation in
            await smsValidatorAPIClient.setOnValidate { apiRequest in
                #expect(apiRequest.msisdn == "555555555")
                #expect(apiRequest.sender == "some sender")
                confirmation.confirm()
                return AirshipHTTPResponse(
                    result: nil,
                    statusCode: Int.random(in: 200...299),
                    headers: [:]
                )
            }

            await #expect(throws: NSError.self) {
                _ = try await validator.validateRequest(request)
            }
        }
    }

    @Test("Test validation hints are checked before API client")
    func testValidationHints() async throws {
        // Setup a valid response
        await smsValidatorAPIClient.setOnValidate { apiRequest in
            return AirshipHTTPResponse(
                result: .valid("+1555555555"),
                statusCode: 200,
                headers: [:]
            )
        }

        let validator = AirshipInputValidation.DefaultValidator(
            smsValidatorAPIClient: smsValidatorAPIClient
        )

        // Test 0-3 digits
        for i in 0...3 {
            let request = AirshipInputValidation.Request.sms(
                .init(
                    rawInput: generateRandomNumberString(length: i),
                    validationOptions: .sender(senderID: "some sender", prefix: nil),
                    validationHints: .init(minDigits: 4, maxDigits: 6)
                )
            )
            try await #expect(validator.validateRequest(request) == .invalid)
        }

        // Test 4-6 digits
        for i in 4...6 {
            let request = AirshipInputValidation.Request.sms(
                .init(
                    rawInput: generateRandomNumberString(length: i),
                    validationOptions: .sender(senderID: "some sender", prefix: nil),
                    validationHints: .init(minDigits: 4, maxDigits: 6)
                )
            )
            try await #expect(validator.validateRequest(request) == .valid(address: "+1555555555"))
        }

        // Test over 6 digits
        for i in 7...10 {
            let request = AirshipInputValidation.Request.sms(
                .init(
                    rawInput: generateRandomNumberString(length: i),
                    validationOptions: .sender(senderID: "some sender", prefix: nil),
                    validationHints: .init(minDigits: 4, maxDigits: 6)
                )
            )
            try await #expect(validator.validateRequest(request) == .invalid)
        }

        // Test digits with other characters
        let request = AirshipInputValidation.Request.sms(
            .init(
                rawInput: "a1b2c3d4b5e6",
                validationOptions: .sender(senderID: "some sender", prefix: nil),
                validationHints: .init(minDigits: 4, maxDigits: 6)
            )
        )
        try await #expect(validator.validateRequest(request) == .valid(address: "+1555555555"))
    }

    @Test("Test SMS override.")
    func testSMSOverride() async throws {
        let request = AirshipInputValidation.Request.sms(
            .init(
                rawInput: "555555555",
                validationOptions: .sender(senderID: "some sender", prefix: nil)
            )
        )

        try await confirmation { confirmation in
            let validator = AirshipInputValidation.DefaultValidator(
                smsValidatorAPIClient: smsValidatorAPIClient
            ) { arg in
                #expect(arg == request)
                confirmation.confirm()
                return .override(.valid(address: "some other result"))
            }

            let result = try await validator.validateRequest(request)
            #expect(result == .valid(address: "some other result"))
        }
    }

    @Test("Test SMS override default fallback.")
    func testSMSOverrideFallback() async throws {
        let request = AirshipInputValidation.Request.sms(
            .init(
                rawInput: "555555555",
                validationOptions: .sender(senderID: "some sender", prefix: nil)
            )
        )

        try await confirmation(expectedCount: 2) { confirmation in
            await smsValidatorAPIClient.setOnValidate { apiRequest in
                #expect(apiRequest.msisdn == "555555555")
                #expect(apiRequest.sender == "some sender")
                confirmation.confirm()
                return AirshipHTTPResponse(
                    result: .valid("API result"),
                    statusCode: Int.random(in: 200...299),
                    headers: [:]
                )
            }

            let validator = AirshipInputValidation.DefaultValidator(
                smsValidatorAPIClient: smsValidatorAPIClient
            ) { arg in
                #expect(arg == request)
                confirmation.confirm()
                return .useDefault
            }

            let result = try await validator.validateRequest(request)
            #expect(result == .valid(address: "API result"))
        }
    }

    @Test(
        "Test SMS legacy delegate receives formatted input",
        arguments: [
            "1 555 867 5309",
            "1.555.867.5309",
            "1-555-867-5309",
            "5 5 5 8 6  7 5309",
        ]
    )
    func testSMSLegacyDelegate(arg: String) async throws {
        let request = AirshipInputValidation.Request.sms(
            .init(
                rawInput: arg,
                validationOptions: .sender(senderID: "some sender", prefix: "+1")
            )
        )

        let validator = AirshipInputValidation.DefaultValidator(
            smsValidatorAPIClient: smsValidatorAPIClient
        )

        try await confirmation { confirmation in
            let delegate = TestLegacySMSDelegate { msisdn, sender in
                #expect(msisdn == "15558675309")
                #expect(sender == "some sender")
                confirmation.confirm()
                return true
            }

            await Task { @MainActor in
                validator.legacySMSDelegate = delegate
            }.value

            let result = try await validator.validateRequest(request)
            #expect(result == .valid(address: "15558675309"))
        }
    }

    @Test("Test SMS legacy delegate invalid")
    func testLegacySMSDelegateInvalidates() async throws {
        let request = AirshipInputValidation.Request.sms(
            .init(
                rawInput: "123456",
                validationOptions: .sender(senderID: "some sender", prefix: "+1")
            )
        )

        let validator = AirshipInputValidation.DefaultValidator(
            smsValidatorAPIClient: smsValidatorAPIClient
        )

        try await confirmation { confirmation in
            let delegate = TestLegacySMSDelegate { msisdn, sender in
                #expect(msisdn == "123456")
                #expect(sender == "some sender")
                confirmation.confirm()
                return false
            }

            await Task { @MainActor in
                validator.legacySMSDelegate = delegate
            }.value

            let result = try await validator.validateRequest(request)
            #expect(result == .invalid)
        }
    }

    @Test("Test SMS legacy delegate invalid")
    func testLegacySMSDelegateNoPrefix() async throws {
        let request = AirshipInputValidation.Request.sms(
            .init(
                rawInput: "123456",
                validationOptions: .sender(senderID: "some sender", prefix: nil)
            )
        )

        let validator = AirshipInputValidation.DefaultValidator(
            smsValidatorAPIClient: smsValidatorAPIClient
        )

        try await confirmation { confirmation in
            let delegate = TestLegacySMSDelegate { msisdn, sender in
                #expect(msisdn == "123456")
                #expect(sender == "some sender")
                confirmation.confirm()
                return false
            }

            await Task { @MainActor in
                validator.legacySMSDelegate = delegate
            }.value

            let result = try await validator.validateRequest(request)
            #expect(result == .invalid)
        }
    }

    @Test("Test SMS legacy delegate ignored when only prefix")
    func testLegacySMSDelegatePrefix() async throws {
        let request = AirshipInputValidation.Request.sms(
            .init(
                rawInput: "123456",
                validationOptions: .prefix(prefix: "+1")
            )
        )

        let validator = AirshipInputValidation.DefaultValidator(
            smsValidatorAPIClient: smsValidatorAPIClient
        )

        try await confirmation { confirmation in
            let delegate = TestLegacySMSDelegate { msisdn, sender in
                return false
            }

            await smsValidatorAPIClient.setOnValidate { apiRequest in
                #expect(apiRequest.msisdn == "123456")
                #expect(apiRequest.prefix == "+1")
                confirmation.confirm()
                return AirshipHTTPResponse(
                    result: .valid("API result"),
                    statusCode: Int.random(in: 200...299),
                    headers: [:]
                )
            }

            await Task { @MainActor in
                validator.legacySMSDelegate = delegate
            }.value

            let result = try await validator.validateRequest(request)
            #expect(result == .valid(address: "API result"))
        }
    }
}

fileprivate actor TestLegacySMSDelegate: SMSValidatorDelegate {

    var onValidate: (@Sendable (String, String) async throws -> Bool)

    init(onValidate: @Sendable @escaping (String, String) -> Bool) {
        self.onValidate = onValidate
    }

    func validateSMS(msisdn: String, sender: String) async throws -> Bool {
        return try await onValidate(msisdn, sender)
    }
}

// Helpers
fileprivate extension AirshipInputValidationTest {
    func generateRandomNumberString(length: Int) -> String {
        let digits = "0123456789"
        var result = ""

        for _ in 0..<length {
            if let randomCharacter = digits.randomElement() {
                result.append(randomCharacter)
            }
        }

        return result
    }
}
     */

    private suspend fun checkValidAddress(
        request: AirshipInputValidation.Request,
        expected: String,
        validation: AirshipInputValidation.Validator = defaultValidator
    ) {
        when(val result = validation.validate(request)) {
            AirshipInputValidation.Result.Invalid -> fail()
            is AirshipInputValidation.Result.Valid -> assertEquals(result.address, expected)
        }
    }
}