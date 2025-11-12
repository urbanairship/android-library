package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.json.jsonMapOf
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
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class SubscriptionListApiClientTest {

    private var config: TestAirshipRuntimeConfig = TestAirshipRuntimeConfig(
        RemoteConfig(
            airshipConfig = RemoteAirshipConfig(
                deviceApiUrl = "https://test.urbanairship.com"
            )
        )
    )

    private val requestSession = TestRequestSession()
    private val testDispatcher = StandardTestDispatcher()

    private val client = SubscriptionListApiClient(
        config,
        requestSession
    )

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testGetSubscriptionLists(): TestResult = runTest {
        requestSession.addResponse(
            200,
            jsonMapOf(
                "ok" to true,
                "list_ids" to listOf("one", "two", "three")
            ).toString()
        )

        val result = client.getSubscriptionLists("identifier")
        Assert.assertEquals(
            "https://test.urbanairship.com/api/subscription_lists/channels/identifier",
            requestSession.lastRequest.url.toString()
        )

        Assert.assertEquals("GET", requestSession.lastRequest.method)
        Assert.assertEquals(200, result.status)

        Assert.assertEquals(setOf("one", "two", "three"), result.value)
    }

    @Test
    public fun testGetSubscriptionListsEmptyList(): TestResult = runTest {
        requestSession.addResponse(
            200,
            jsonMapOf(
                "ok" to true,
                "list_ids" to emptyList<String>()
            ).toString()
        )

        val result = client.getSubscriptionLists("identifier")
        Assert.assertEquals(
            "https://test.urbanairship.com/api/subscription_lists/channels/identifier",
            requestSession.lastRequest.url.toString()
        )

        Assert.assertEquals("GET", requestSession.lastRequest.method)
        Assert.assertEquals(200, result.status)
        Assert.assertEquals(emptySet<String>(), result.value)
    }

    @Test
    public fun testGetSubscriptionListsMissing(): TestResult = runTest {
        requestSession.addResponse(
            200,
            jsonMapOf(
                "ok" to true,
            ).toString()
        )

        val result = client.getSubscriptionLists("identifier")
        Assert.assertEquals(
            "https://test.urbanairship.com/api/subscription_lists/channels/identifier",
            requestSession.lastRequest.url.toString()
        )

        Assert.assertEquals("GET", requestSession.lastRequest.method)
        Assert.assertEquals(200, result.status)
        Assert.assertEquals(emptySet<String>(), result.value)
    }
}
