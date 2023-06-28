/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import android.net.Uri
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.config.AirshipUrlConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.toSuspendingRequestSession
import java.util.TimeZone
import junit.framework.TestCase.assertEquals
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
public class SubscriptionListApiClientTest {

    private val testDispatcher = StandardTestDispatcher()

    private var runtimeConfig: TestAirshipRuntimeConfig =
        TestAirshipRuntimeConfig.newTestConfig().also {
            it.urlConfig = AirshipUrlConfig.newBuilder().setDeviceUrl("https://example.com").build()
        }
    private val requestSession = TestRequestSession()

    private var client = SubscriptionListApiClient(
        runtimeConfig, requestSession.toSuspendingRequestSession()
    )

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val usPacific = TimeZone.getTimeZone("US/Pacific")
        TimeZone.setDefault(usPacific)
    }

    @Test
    public fun testFetch(): TestResult = runTest {
        requestSession.addResponse(
            200,
            """
             {
               "ok": true,
               "subscription_lists": [
                  {
                     "list_ids": ["example_listId-1", "example_listId-3"],
                      "scope": "email"
                  },
                  {
                     "list_ids": ["example_listId-2", "example_listId-4"],
                     "scope": "app"
                  },
                  {
                     "list_ids": ["example_listId-2"],
                     "scope": "web"
                  }
               ]
            }
            """
        )

        val result = client.getSubscriptionLists("some-contact-id")
        val expectedRequest = Request(
            url = Uri.parse("https://example.com/api/subscription_lists/contacts/some-contact-id"),
            method = "GET",
            auth = RequestAuth.ContactTokenAuth("some-contact-id"),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Appkey" to runtimeConfig.configOptions.appKey,
            )
        )
        assertEquals(expectedRequest, requestSession.requests[0])

        val expectedResultValue = mapOf(
            "example_listId-1" to setOf(Scope.EMAIL),
            "example_listId-2" to setOf(Scope.APP, Scope.WEB),
            "example_listId-3" to setOf(Scope.EMAIL),
            "example_listId-4" to setOf(Scope.APP)
        )
        assertEquals(200, result.status)
        assertEquals(expectedResultValue, result.value)
    }

    @Test
    public fun testFetchEmptyLists(): TestResult = runTest {
        requestSession.addResponse(
            200, """
             {
               "ok" : true,
               "subscription_lists": []
            }
            """
        )

        val result = client.getSubscriptionLists("some-contact-id")
        val expectedRequest = Request(
            url = Uri.parse("https://example.com/api/subscription_lists/contacts/some-contact-id"),
            method = "GET",
            auth = RequestAuth.ContactTokenAuth("some-contact-id"),
            headers = mapOf(
                "Accept" to "application/vnd.urbanairship+json; version=3;",
                "X-UA-Appkey" to runtimeConfig.configOptions.appKey,
            )
        )
        assertEquals(expectedRequest, requestSession.requests[0])

        assertEquals(200, result.status)
        assertEquals(emptyMap<String, Set<String>>(), result.value)
    }
}
