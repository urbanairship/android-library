package com.urbanairship.http

import android.net.Uri
import android.util.Base64
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.UAirship
import com.urbanairship.util.PlatformUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class DefaultRequestSessionTest {

    private val appConfig = TestAirshipRuntimeConfig.newTestConfig().configOptions
    private val platform = UAirship.ANDROID_PLATFORM
    private val mockClient = mockk<HttpClient>()
    private val mockAuthProvider = mockk<AuthTokenProvider>()

    @Suppress("ObjectLiteralToLambda") // Breaks test when converted
    private val testParser = object : ResponseParser<String> {
        override fun parseResponse(
            status: Int,
            headers: Map<String, String>,
            responseBody: String?
        ): String {
            return "neat"
        }
    }

    private val expectedDefaultHeaders = mapOf(
        "X-UA-App-Key" to appConfig.appKey,
        "User-Agent" to "(UrbanAirshipLib-${PlatformUtils.asString(platform)}/${UAirship.getVersion()}; ${appConfig.appKey})"
    )

    private val requestSession = DefaultRequestSession(
        appConfig, platform, mockClient
    )

    @Test
    public fun testRequest() {
        val request = Request(
            url = Uri.parse("some uri"),
            method = "POST",
            body = RequestBody.Json("Some JSON"),
            headers = mapOf("foo" to "bar")
        )

        val expectedHeaders = request.headers + expectedDefaultHeaders
        val result = Response(200, "neat", "neat", emptyMap())

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returns result

        assertEquals(
            requestSession.execute(request, testParser), result
        )

        verify {
            mockClient.execute(
                Uri.parse("some uri"),
                request.method,
                expectedHeaders,
                RequestBody.Json("Some JSON"),
                true,
                testParser
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    public fun testBasicAuth() {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.BasicAuth("foo", "bar"),
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        val expectedHeaders = (request.headers + expectedDefaultHeaders).toMutableMap()
        expectedHeaders["Authorization"] =
            "Basic ${Base64.encodeToString("foo:bar".toByteArray(), Base64.NO_WRAP)}"

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returns Response(200, "neat", "neat", emptyMap())

        requestSession.execute(request, testParser)
        verify {
            mockClient.execute(
                Uri.parse("some uri"),
                request.method,
                expectedHeaders,
                request.body,
                true,
                testParser
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    public fun testBasicAppAuth() {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.BasicAppAuth,
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        val expectedHeaders = (request.headers + expectedDefaultHeaders).toMutableMap()
        val credentials = "${appConfig.appKey}:${appConfig.appSecret}"
        expectedHeaders["Authorization"] =
            "Basic ${Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)}"

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returns Response(200, "neat", "neat", emptyMap())

        requestSession.execute(request, testParser)
        verify {
            mockClient.execute(
                Uri.parse("some uri"), request.method, expectedHeaders, null, true, testParser
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    public fun testBearerTokenAuth() {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.BearerToken("some token"),
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        val expectedHeaders = (request.headers + expectedDefaultHeaders).toMutableMap()
        expectedHeaders["Authorization"] = "Bearer some token"

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returns Response(200, "neat", "neat", emptyMap())

        requestSession.execute(request, testParser)
        verify {
            mockClient.execute(
                Uri.parse("some uri"), request.method, expectedHeaders, null, true, testParser
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    public fun testChannelAuthToken(): TestResult = runTest {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.ChannelTokenAuth("some channel ID"),
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        coEvery {
            mockAuthProvider.fetchToken("some channel ID")
        } returns Result.success("some auth token")
        requestSession.channelAuthTokenProvider = mockAuthProvider

        val expectedHeaders = (request.headers + expectedDefaultHeaders).toMutableMap()
        expectedHeaders["Authorization"] = "Bearer some auth token"

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returns Response(200, "neat", "neat", emptyMap())

        requestSession.execute(request, testParser)
        verify {
            mockClient.execute(
                Uri.parse("some uri"), request.method, expectedHeaders, null, true, testParser
            )
        }
        confirmVerified(mockClient)
    }

    @Test(expected = RequestException::class)
    public fun testNoChannelAuthTokenProvider() {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.ChannelTokenAuth("some channel ID"),
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returns Response(200, "neat", "neat", emptyMap())

        requestSession.execute(request, testParser)
    }

    @Test(expected = RequestException::class)
    public fun testChannelAuthProviderThrows() {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.ChannelTokenAuth("some channel ID"),
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        requestSession.channelAuthTokenProvider = mockAuthProvider
        coEvery { mockAuthProvider.fetchToken("some channel ID") } returns Result.failure(
            IllegalArgumentException("neat")
        )

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returns Response(200, "neat", "neat", emptyMap())

        requestSession.execute(request, testParser)
    }

    @Test
    public fun testChannelAuthExpiredRetriesAndExpires() {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.ChannelTokenAuth("some channel ID"),
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        requestSession.channelAuthTokenProvider = mockAuthProvider
        coEvery { mockAuthProvider.fetchToken("some channel ID") } returnsMany listOf(
            Result.success("first"), Result.success("second")
        )

        coEvery { mockAuthProvider.expireToken("first") } just runs

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returnsMany listOf(
            Response(401, "neat", "neat", emptyMap()), Response(200, "neat", "neat", emptyMap())
        )

        assertEquals(
            Response(200, "neat", "neat", emptyMap()), requestSession.execute(request, testParser)
        )

        val firstRequestHeaders = (request.headers + expectedDefaultHeaders).toMutableMap()
        firstRequestHeaders["Authorization"] = "Bearer first"

        val secondRequestHeaders = (request.headers + expectedDefaultHeaders).toMutableMap()
        secondRequestHeaders["Authorization"] = "Bearer second"

        verify {
            mockClient.execute(
                Uri.parse("some uri"), request.method, firstRequestHeaders, null, true, testParser
            )
            mockClient.execute(
                Uri.parse("some uri"), request.method, secondRequestHeaders, null, true, testParser
            )
        }

        coVerify {
            mockAuthProvider.fetchToken("some channel ID")
            mockAuthProvider.fetchToken("some channel ID")
        }

        coVerify { mockAuthProvider.expireToken("first") }

        confirmVerified(mockClient)
        confirmVerified(mockAuthProvider)
    }

    @Test
    public fun testContactAuthProvider() {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.ContactTokenAuth("some contact ID"),
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        requestSession.contactAuthTokenProvider = mockAuthProvider
        coEvery { mockAuthProvider.fetchToken("some contact ID") } returns Result.success("some auth token")

        val expectedHeaders = (request.headers + expectedDefaultHeaders).toMutableMap()
        expectedHeaders["Authorization"] = "Bearer some auth token"

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returns Response(200, "neat", "neat", emptyMap())

        requestSession.execute(request, testParser)
        verify {
            mockClient.execute(
                Uri.parse("some uri"), request.method, expectedHeaders, null, true, testParser
            )
        }
        confirmVerified(mockClient)
    }

    @Test(expected = RequestException::class)
    public fun testNoContactAuthTokenProvider() {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.ContactTokenAuth("some contact ID"),
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returns Response(200, "neat", "neat", emptyMap())

        requestSession.execute(request, testParser)
    }

    @Test(expected = RequestException::class)
    public fun testContactAuthProviderThrows() {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.ContactTokenAuth("some contact ID"),
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        requestSession.contactAuthTokenProvider = mockAuthProvider
        coEvery { mockAuthProvider.fetchToken("some contact ID") } throws IllegalArgumentException("neat")

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returns Response(200, "neat", "neat", emptyMap())

        requestSession.execute(request, testParser)
    }

    @Test
    public fun testContactAuthExpiredRetriesAndExpires() {
        val request = Request(
            url = Uri.parse("some uri"),
            auth = RequestAuth.ContactTokenAuth("some contact ID"),
            method = "POST",
            headers = mapOf("foo" to "bar")
        )

        requestSession.contactAuthTokenProvider = mockAuthProvider
        coEvery { mockAuthProvider.fetchToken("some contact ID") } returnsMany listOf(
            Result.success("first"), Result.success("second")
        )
        coEvery { mockAuthProvider.expireToken("first") } just runs

        every<Response<String>> {
            mockClient.execute(any(), any(), any(), any(), any(), any())
        } returnsMany listOf(
            Response(401, "neat", "neat", emptyMap()), Response(200, "neat", "neat", emptyMap())
        )

        assertEquals(
            Response(200, "neat", "neat", emptyMap()), requestSession.execute(request, testParser)
        )

        val firstRequestHeaders = (request.headers + expectedDefaultHeaders).toMutableMap()
        firstRequestHeaders["Authorization"] = "Bearer first"

        val secondRequestHeaders = (request.headers + expectedDefaultHeaders).toMutableMap()
        secondRequestHeaders["Authorization"] = "Bearer second"

        verify {
            mockClient.execute(
                Uri.parse("some uri"), request.method, firstRequestHeaders, null, true, testParser
            )
            mockClient.execute(
                Uri.parse("some uri"), request.method, secondRequestHeaders, null, true, testParser
            )
        }

        coVerify {
            mockAuthProvider.fetchToken("some contact ID")
            mockAuthProvider.fetchToken("some contact ID")
        }

        coVerify { mockAuthProvider.expireToken("first") }

        confirmVerified(mockClient)
        confirmVerified(mockAuthProvider)
    }
}
