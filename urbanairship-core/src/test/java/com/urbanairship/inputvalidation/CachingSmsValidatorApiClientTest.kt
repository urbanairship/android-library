/* Copyright Airship and Contributors */

package com.urbanairship.inputvalidation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.http.RequestResult
import java.util.UUID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class CachingSmsValidatorApiClientTest {

    private val apiClient: SmsValidatorApiInterface = mockk()
    private lateinit var cacheClient: CachingSmsValidatorApiClient
    private val maxEntriesCount = 5

    @Before
    public fun setUp() {
        cacheClient = CachingSmsValidatorApiClient(
            client = apiClient,
            maxCacheEntries = maxEntriesCount
        )
    }

    @Test
    public fun testCachesSuccessResultsPrefix(): TestResult = runTest {

        val msisdn = UUID.randomUUID().toString()
        val prefix = UUID.randomUUID().toString()

        coEvery { apiClient.validateSmsWithPrefix(any(), any()) } answers {
            RequestResult(
                status = 200,
                value = SmsValidatorApiClient.Result.Valid("valid string"),
                headers = emptyMap(),
                body = null
            )
        }

        var result = cacheClient.validateSmsWithPrefix(msisdn, prefix)
        assertTrue(result.isSuccessful)
        assertEquals(result.value, SmsValidatorApiClient.Result.Valid("valid string"))
        coVerify(exactly = 1) { apiClient.validateSmsWithPrefix(any(), any()) }

        result = cacheClient.validateSmsWithPrefix(msisdn, prefix)
        assertTrue(result.isSuccessful)
        assertEquals(result.value, SmsValidatorApiClient.Result.Valid("valid string"))
        coVerify(exactly = 1) { apiClient.validateSmsWithPrefix(any(), any()) }
    }

    @Test
    public fun testCachesSuccessResultsSender(): TestResult = runTest {

        val msisdn = UUID.randomUUID().toString()
        val sender = UUID.randomUUID().toString()

        coEvery { apiClient.validateSmsWithSender(any(), any()) } answers {
            RequestResult(
                status = 200,
                value = SmsValidatorApiClient.Result.Valid("valid string"),
                headers = emptyMap(),
                body = null
            )
        }

        var result = cacheClient.validateSmsWithSender(msisdn, sender)
        assertTrue(result.isSuccessful)
        assertEquals(result.value, SmsValidatorApiClient.Result.Valid("valid string"))
        coVerify(exactly = 1) { apiClient.validateSmsWithSender(any(), any()) }

        result = cacheClient.validateSmsWithSender(msisdn, sender)
        assertTrue(result.isSuccessful)
        assertEquals(result.value, SmsValidatorApiClient.Result.Valid("valid string"))
        coVerify(exactly = 1) { apiClient.validateSmsWithSender(any(), any()) }
    }

    @Test
    public fun testCachesResultForRequestParams(): TestResult = runTest {
        val msisdn = UUID.randomUUID().toString()
        val sender = UUID.randomUUID().toString()
        val prefix = UUID.randomUUID().toString()

        val generateResponse: (String) -> RequestResult<SmsValidatorApiClient.Result> = { number ->
            RequestResult(
                status = 200,
                value = SmsValidatorApiClient.Result.Valid("$number valid"),
                headers = emptyMap(),
                body = null
            )
        }

        coEvery { apiClient.validateSmsWithSender(any(), any()) } answers {
            generateResponse(firstArg())
        }

        var result = cacheClient.validateSmsWithSender(msisdn, sender)
        assertTrue(result.isSuccessful)
        assertEquals(result.value, SmsValidatorApiClient.Result.Valid("$msisdn valid"))
        coVerify(exactly = 1) { apiClient.validateSmsWithSender(any(), any()) }


        coEvery { apiClient.validateSmsWithPrefix(any(), any()) } answers {
            generateResponse(firstArg())
        }

        result = cacheClient.validateSmsWithPrefix(msisdn, prefix)
        assertTrue(result.isSuccessful)
        assertEquals(result.value, SmsValidatorApiClient.Result.Valid("$msisdn valid"))
        coVerify(exactly = 1) { apiClient.validateSmsWithPrefix(any(), any()) }
    }

    @Test
    public fun testMaxCacheEntries(): TestResult = runTest {
        coEvery { apiClient.validateSmsWithPrefix(any(), any()) } answers {
            RequestResult(
                status = 200,
                value = SmsValidatorApiClient.Result.Valid("${args[0]} valid"),
                headers = emptyMap(),
                body = null
            )
        }

        val requestValidation: suspend () -> Unit = {
            cacheClient.validateSmsWithPrefix(
                msisdn = UUID.randomUUID().toString(),
                prefix = UUID.randomUUID().toString()
            )
        }

        (1..maxEntriesCount).forEach { _ -> requestValidation() }
        coVerify(exactly = maxEntriesCount) { apiClient.validateSmsWithPrefix(any(), any()) }

        val msisdn = UUID.randomUUID().toString()
        val prefix = UUID.randomUUID().toString()

        cacheClient.validateSmsWithPrefix(msisdn, prefix)
        coVerify(exactly = maxEntriesCount + 1) { apiClient.validateSmsWithPrefix(any(), any()) }

        cacheClient.validateSmsWithPrefix(msisdn, prefix)
        coVerify(exactly = maxEntriesCount + 1) { apiClient.validateSmsWithPrefix(any(), any()) }
    }
}
