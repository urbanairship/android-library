/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class RemoteDataApiClientTest {
    private val testConfig = TestAirshipRuntimeConfig()
    private val requestSession = TestRequestSession()
    private var client: RemoteDataApiClient = RemoteDataApiClient(testConfig, requestSession.toSuspendingRequestSession())

    private val validPayload = RemoteDataPayload(
        type = "test_data_type",
        timestamp = DateUtils.parseIso8601("2017-01-01T12:00:00"),
        data = jsonMapOf(
            "message_center" to jsonMapOf(
                "background_color" to "0000FF",
                "font" to "Comic Sans"
            )
        ),
        remoteDataInfo = null
    )

    private val validResponse = """
   {
       "ok":true,
       "payloads":[
          {
             "type":"test_data_type",
             "timestamp":"2017-01-01T12:00:00",
             "data":{
                "message_center":{
                   "background_color":"0000FF",
                   "font":"Comic Sans"
                }
             }
          }
       ]
    }
    """

    @Test
    public fun testFetch(): TestResult = runTest {
        val responseTimestamp = DateUtils.createIso8601TimeStamp(10000)
        requestSession.addResponse(
            200,
            validResponse,
            mutableMapOf("Last-Modified" to responseTimestamp)
        )
        val url = Uri.parse("https://example.com")

        val expectedInfo = RemoteDataInfo(
            url = "some url",
            lastModified = responseTimestamp,
            source = RemoteDataSource.APP
        )

        val expectedPayload = validPayload.copy(remoteDataInfo = expectedInfo)
        val expectedResult = RemoteDataApiClient.Result(
            remoteDataInfo = expectedInfo,
            payloads = setOf(expectedPayload)
        )

        val response = client.fetch(
            url,
            RequestAuth.BasicAppAuth,
            "some last modified"
        ) { lastModified ->
            assertEquals(responseTimestamp, lastModified)
            expectedInfo
        }

        assertEquals(expectedResult, response.value)
        assertEquals(200, response.status)

        val expectedHeaders = mutableMapOf(
            "X-UA-Appkey" to testConfig.configOptions.appKey,
            "If-Modified-Since" to "some last modified"
        )

        assertEquals(url, requestSession.lastRequest.url)
        assertEquals(RequestAuth.BasicAppAuth, requestSession.lastRequest.auth)
        assertEquals(expectedHeaders, requestSession.lastRequest.headers)
        assertEquals("GET", requestSession.lastRequest.method)
    }

    @Test
    public fun testFetchNoLastModified(): TestResult = runTest {
        val responseTimestamp = DateUtils.createIso8601TimeStamp(10000)
        requestSession.addResponse(
            200,
            validResponse,
            mutableMapOf("Last-Modified" to responseTimestamp)
        )
        val url = Uri.parse("https://example.com")

        val expectedInfo = RemoteDataInfo(
            url = "some url",
            lastModified = responseTimestamp,
            source = RemoteDataSource.APP
        )

        val expectedPayload = validPayload.copy(remoteDataInfo = expectedInfo)
        val expectedResult = RemoteDataApiClient.Result(
            remoteDataInfo = expectedInfo,
            payloads = setOf(expectedPayload)
        )

        val response = client.fetch(
            url,
            RequestAuth.BasicAppAuth,
            null
        ) {
            expectedInfo
        }

        assertEquals(expectedResult, response.value)
        assertEquals(200, response.status)

        val expectedHeaders = mutableMapOf(
            "X-UA-Appkey" to testConfig.configOptions.appKey,
        )
        assertEquals(url, requestSession.lastRequest.url)
        assertEquals(RequestAuth.BasicAppAuth, requestSession.lastRequest.auth)
        assertEquals(expectedHeaders, requestSession.lastRequest.headers)
        assertEquals("GET", requestSession.lastRequest.method)
    }

    @Test
    public fun testFetch304(): TestResult = runTest {
        requestSession.addResponse(
            304,
            null,
            emptyMap()
        )
        val url = Uri.parse("https://example.com")

        val response = client.fetch(
            url,
            RequestAuth.BasicAppAuth,
            null
        ) {
            throw IllegalStateException("should not be called")
        }

        assertNull(response.value)
        assertEquals(304, response.status)

        val expectedHeaders = mutableMapOf(
            "X-UA-Appkey" to testConfig.configOptions.appKey,
        )
        assertEquals(url, requestSession.lastRequest.url)
        assertEquals(RequestAuth.BasicAppAuth, requestSession.lastRequest.auth)
        assertEquals(expectedHeaders, requestSession.lastRequest.headers)
        assertEquals("GET", requestSession.lastRequest.method)
    }

    @Test
    public fun testError(): TestResult = runTest {
        requestSession.addResponse(
            400,
            null,
            emptyMap()
        )
        val url = Uri.parse("https://example.com")

        val response = client.fetch(
            url,
            RequestAuth.BasicAppAuth,
            null
        ) {
           throw IllegalStateException("should not be called")
        }

        assertNull(response.value)
        assertEquals(400, response.status)

        val expectedHeaders = mutableMapOf(
            "X-UA-Appkey" to testConfig.configOptions.appKey,
        )
        assertEquals(url, requestSession.lastRequest.url)
        assertEquals(RequestAuth.BasicAppAuth, requestSession.lastRequest.auth)
        assertEquals(expectedHeaders, requestSession.lastRequest.headers)
        assertEquals("GET", requestSession.lastRequest.method)
    }

    @Test
    public fun testEmptyResponse(): TestResult = runTest {
        requestSession.addResponse(
            200,
            "{ \"ok\": true }",
            emptyMap()
        )

        val url = Uri.parse("https://example.com")

        val expectedInfo = RemoteDataInfo(
            url = "some url",
            lastModified = null,
            source = RemoteDataSource.APP
        )

        val response = client.fetch(
            url,
            RequestAuth.BasicAppAuth,
            null
        ) {
            expectedInfo
        }

        val expectedResult = RemoteDataApiClient.Result(
            remoteDataInfo = expectedInfo,
            payloads = emptySet()
        )

        assertEquals(expectedResult, response.value)
        assertEquals(200, response.status)

        val expectedHeaders = mutableMapOf(
            "X-UA-Appkey" to testConfig.configOptions.appKey,
        )
        assertEquals(url, requestSession.lastRequest.url)
        assertEquals(RequestAuth.BasicAppAuth, requestSession.lastRequest.auth)
        assertEquals(expectedHeaders, requestSession.lastRequest.headers)
        assertEquals("GET", requestSession.lastRequest.method)
    }
}
