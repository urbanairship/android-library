package com.urbanairship.remotedata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.PreferenceDataStore
import com.urbanairship.http.RequestResult
import com.urbanairship.json.jsonMapOf
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class RemoteDataProviderTest {
    private val testDispatcher = StandardTestDispatcher()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = TestRemoteDataProvider(context)

    @Before
    public fun setUp() {
        provider.isEnabled = true
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testRefresh(): TestResult = runTest {
        val locale = Locale("bs")
        val randomValue = 100

        val remoteDataInfo = RemoteDataInfo(
            url = "example://",
            lastModified = "some last modified",
            source = RemoteDataSource.APP
        )

        val refreshResult = RemoteDataApiClient.Result(
            remoteDataInfo,
            payloads = setOf(
                RemoteDataPayload(
                    type = "some type",
                    timestamp = 1000,
                    data = jsonMapOf("something" to "something"),
                    remoteDataInfo = remoteDataInfo
                ),
                RemoteDataPayload(
                    type = "some other type",
                    timestamp = 4000,
                    data = jsonMapOf("something else" to "something something"),
                    remoteDataInfo = remoteDataInfo
                )
            )
        )

        provider.fetchRemoteDataCallback = { requestLocale, requestRandomValue, lastRemoteDataInfo ->
            assertEquals(locale, requestLocale)
            assertEquals(randomValue, requestRandomValue)
            assertNull(lastRemoteDataInfo)

            RequestResult(
                status = 200,
                value = refreshResult,
                body = null,
                headers = emptyMap()
            )
        }

        val result = provider.refresh("some token", locale, randomValue)
        assertEquals(RemoteDataProvider.RefreshResult.NEW_DATA, result)

        val payloads = provider.payloads(listOf("some type", "some other type"))
        assertEquals(refreshResult.payloads.toSet(), payloads)
    }

    @Test
    public fun testRefreshDisabled(): TestResult = runTest {
        provider.fetchRemoteDataCallback = { _, _, _ ->
            val remoteDataInfo = RemoteDataInfo(
                url = "example://",
                lastModified = "some last modified",
                source = RemoteDataSource.APP
            )

            val refreshResult = RemoteDataApiClient.Result(
                remoteDataInfo,
                payloads = setOf(
                    RemoteDataPayload(
                        type = "some type",
                        timestamp = 1000,
                        data = jsonMapOf("something" to "something"),
                        remoteDataInfo = remoteDataInfo
                    )
                )
            )
            RequestResult(
                status = 200,
                value = refreshResult,
                body = null,
                headers = emptyMap()
            )
        }

        // load data
        var result = provider.refresh("some token", Locale("bs"), 100)
        assertEquals(RemoteDataProvider.RefreshResult.NEW_DATA, result)

        var payloads = provider.payloads(listOf("some type", "some other type"))
        assertFalse(payloads.isEmpty())

        provider.isEnabled = false

        payloads = provider.payloads(listOf("some type", "some other type"))
        assertTrue(payloads.isEmpty())

        result = provider.refresh("some token", Locale("bs"), 100)
        assertEquals(RemoteDataProvider.RefreshResult.NEW_DATA, result)

        result = provider.refresh("some token", Locale("bs"), 100)
        assertEquals(RemoteDataProvider.RefreshResult.SKIPPED, result)
    }

    @Test
    public fun testRefreshSkipped(): TestResult = runTest {
        val remoteDataInfo = RemoteDataInfo(
            url = "example://",
            lastModified = "some last modified",
            source = RemoteDataSource.APP
        )

        provider.fetchRemoteDataCallback = { _, _, _ ->
            val refreshResult = RemoteDataApiClient.Result(
                remoteDataInfo,
                payloads = setOf(
                    RemoteDataPayload(
                        type = "some type",
                        timestamp = 1000,
                        data = jsonMapOf("something" to "something"),
                        remoteDataInfo = remoteDataInfo
                    )
                )
            )
            RequestResult(
                status = 200,
                value = refreshResult,
                body = null,
                headers = emptyMap()
            )
        }

        // load data
        var result = provider.refresh("some token", Locale("bs"), 100)
        assertEquals(RemoteDataProvider.RefreshResult.NEW_DATA, result)

        provider.isRemoteDataInfoUpToDateCallback = { info, locale, randomValue ->
            assertEquals(remoteDataInfo, info)
            assertEquals(Locale("bs"), locale)
            assertEquals(randomValue, 100)
            true
        }

        result = provider.refresh("some token", Locale("bs"), 100)
        assertEquals(RemoteDataProvider.RefreshResult.SKIPPED, result)
    }

    @Test
    public fun testRefresh304(): TestResult = runTest {
        val remoteDataInfo = RemoteDataInfo(
            url = "example://",
            lastModified = "some last modified",
            source = RemoteDataSource.APP
        )

        provider.fetchRemoteDataCallback = { _, _, _ ->
            val refreshResult = RemoteDataApiClient.Result(
                remoteDataInfo,
                payloads = setOf(
                    RemoteDataPayload(
                        type = "some type",
                        timestamp = 1000,
                        data = jsonMapOf("something" to "something"),
                        remoteDataInfo = remoteDataInfo
                    )
                )
            )
            RequestResult(
                status = 200,
                value = refreshResult,
                body = null,
                headers = emptyMap()
            )
        }

        // load data
        var result = provider.refresh("some token", Locale("bs"), 100)
        assertEquals(RemoteDataProvider.RefreshResult.NEW_DATA, result)

        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ ->
            false
        }

        provider.fetchRemoteDataCallback = { _, _, _ ->
            RequestResult(
                status = 304,
                value = null,
                body = null,
                headers = emptyMap()
            )
        }

        result = provider.refresh("some token", Locale("bs"), 100)
        assertEquals(RemoteDataProvider.RefreshResult.SKIPPED, result)
    }

    @Test
    public fun testRefresh304WithoutLastModified(): TestResult = runTest {
        provider.fetchRemoteDataCallback = { _, _, _ ->
            RequestResult(
                status = 304,
                value = null,
                body = null,
                headers = emptyMap()
            )
        }

        val result = provider.refresh("some token", Locale("bs"), 100)
        assertEquals(RemoteDataProvider.RefreshResult.FAILED, result)
    }

    @Test
    public fun testRefreshClientError(): TestResult = runTest {
        provider.fetchRemoteDataCallback = { _, _, _ ->
            RequestResult(
                status = 400,
                value = null,
                body = null,
                headers = emptyMap()
            )
        }

        val result = provider.refresh("some token", Locale("bs"), 100)
        assertEquals(RemoteDataProvider.RefreshResult.FAILED, result)
    }

    @Test
    public fun testRefreshServerError(): TestResult = runTest {
        provider.fetchRemoteDataCallback = { _, _, _ ->
            RequestResult(
                status = 500,
                value = null,
                body = null,
                headers = emptyMap()
            )
        }

        val result = provider.refresh("some token", Locale("bs"), 100)
        assertEquals(RemoteDataProvider.RefreshResult.FAILED, result)
    }

    @Test
    public fun testIsCurrent(): TestResult = runTest {
        // No data
        var isCurrent = provider.isCurrent(Locale.CANADA_FRENCH, 100)
        assertFalse(isCurrent)

        val remoteDataInfo = RemoteDataInfo(
            url = "example://",
            lastModified = "some last modified",
            source = RemoteDataSource.APP
        )

        provider.fetchRemoteDataCallback = { _, _, _ ->
            val refreshResult = RemoteDataApiClient.Result(
                remoteDataInfo,
                payloads = setOf(
                    RemoteDataPayload(
                        type = "some type",
                        timestamp = 1000,
                        data = jsonMapOf("something" to "something"),
                        remoteDataInfo = remoteDataInfo
                    )
                )
            )
            RequestResult(
                status = 200,
                value = refreshResult,
                body = null,
                headers = emptyMap()
            )
        }

        // load data
        var result = provider.refresh("some token", Locale.CANADA_FRENCH, 100)
        assertEquals(RemoteDataProvider.RefreshResult.NEW_DATA, result)

        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ ->
            true
        }
        isCurrent = provider.isCurrent(Locale.CANADA_FRENCH, 100)
        assertTrue(isCurrent)

        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ ->
            false
        }
        isCurrent = provider.isCurrent(Locale.CANADA_FRENCH, 100)
        assertFalse(isCurrent)
    }

    @Test
    public fun testNotifyOutdated(): TestResult = runTest {
        var requestCount = 0

        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ ->
            true
        }

        val remoteDataInfo = RemoteDataInfo(
            url = "example://",
            lastModified = "some last modified",
            source = RemoteDataSource.APP
        )

        provider.fetchRemoteDataCallback = { _, _, _ ->
            requestCount++

            val refreshResult = RemoteDataApiClient.Result(
                remoteDataInfo,
                payloads = setOf(
                    RemoteDataPayload(
                        type = "some type",
                        timestamp = 1000,
                        data = jsonMapOf("something" to "something"),
                        remoteDataInfo = remoteDataInfo
                    )
                )
            )
            RequestResult(
                status = 200,
                value = refreshResult,
                body = null,
                headers = emptyMap()
            )
        }

        provider.refresh("some token", Locale.CANADA_FRENCH, 100)
        assertEquals(1, requestCount)

        provider.refresh("some token", Locale.CANADA_FRENCH, 100)
        assertEquals(1, requestCount)

        provider.notifyOutdated(RemoteDataInfo("some other url", "some other thing", RemoteDataSource.APP))
        provider.refresh("some token", Locale.CANADA_FRENCH, 100)
        assertEquals(1, requestCount)

        provider.notifyOutdated(remoteDataInfo)
        provider.refresh("some token", Locale.CANADA_FRENCH, 100)
        assertEquals(2, requestCount)
    }
}

internal class TestRemoteDataProvider(context: Context) : RemoteDataProvider(
    source = RemoteDataSource.APP,
    remoteDataStore = RemoteDataStore(context, "appKey", UUID.randomUUID().toString()),
    preferenceDataStore = PreferenceDataStore.inMemoryStore(context)
) {
    var isRemoteDataInfoUpToDateCallback: ((RemoteDataInfo, Locale, Int) -> Boolean)? = null
    var fetchRemoteDataCallback: ((Locale, Int, RemoteDataInfo?) -> RequestResult<RemoteDataApiClient.Result>)? = null

    override suspend fun isRemoteDataInfoUpToDate(
        remoteDataInfo: RemoteDataInfo,
        locale: Locale,
        randomValue: Int
    ): Boolean {
        return this.isRemoteDataInfoUpToDateCallback!!.invoke(remoteDataInfo, locale, randomValue)
    }

    override suspend fun fetchRemoteData(
        locale: Locale,
        randomValue: Int,
        lastRemoteDataInfo: RemoteDataInfo?
    ): RequestResult<RemoteDataApiClient.Result> {
        return this.fetchRemoteDataCallback!!.invoke(locale, randomValue, lastRemoteDataInfo)
    }
}
