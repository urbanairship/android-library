/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.preferences.SyncPrefKey
import com.urbanairship.TestClock
import com.urbanairship.http.RequestResult
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.Clock
import com.urbanairship.util.LocaleCompat
import com.urbanairship.util.minus
import com.urbanairship.util.plus
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
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
    private val clock: TestClock = TestClock()
    private val provider = TestRemoteDataProvider(context, clock)

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
        val locale = LocaleCompat.of("bs")
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
                    timestamp = Instant.ofEpochMilli(1000),
                    data = jsonMapOf("something" to "something"),
                    remoteDataInfo = remoteDataInfo
                ),
                RemoteDataPayload(
                    type = "some other type",
                    timestamp = Instant.ofEpochMilli(4000),
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
        assertTrue(result is RemoteDataProvider.RefreshResult.NewData)

        val payloads = provider.payloads(listOf("some type", "some other type"))
        assertEquals(refreshResult.payloads.toSet(), payloads)
    }

    @Test
    public fun testRefreshUpdatesStatusFlow(): TestResult = runTest {
        val locale = LocaleCompat.of("bs")
        val randomValue = 100

        val remoteDataInfo = RemoteDataInfo(
            url = "example://",
            lastModified = "some last modified",
            source = RemoteDataSource.APP
        )

        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ -> true }
        provider.fetchRemoteDataCallback = { _, _, _ ->
            RequestResult(
                status = 200,
                value = RemoteDataApiClient.Result(
                    remoteDataInfo,
                    payloads = setOf(
                        RemoteDataPayload(
                            type = "some type",
                            timestamp = 1000,
                            data = jsonMapOf("something" to "something"),
                            remoteDataInfo = remoteDataInfo
                        )
                    )
                ),
                body = null,
                headers = emptyMap()
            )
        }

        assertTrue(provider.refresh("token-1", locale, randomValue) is RemoteDataProvider.RefreshResult.NewData)

        // A new change token (e.g. app foreground) makes local data stale. After a
        // successful refresh the status flow should report up to date again.
        assertTrue(provider.refresh("token-2", locale, randomValue) is RemoteDataProvider.RefreshResult.NewData)
        assertEquals(RemoteData.Status.UP_TO_DATE, provider.statusUpdates.value)
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
                        timestamp = Instant.ofEpochMilli(1000),
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
        var result = provider.refresh("some token", LocaleCompat.of("bs"), 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.NewData)

        var payloads = provider.payloads(listOf("some type", "some other type"))
        assertFalse(payloads.isEmpty())

        provider.isEnabled = false

        payloads = provider.payloads(listOf("some type", "some other type"))
        assertTrue(payloads.isEmpty())

        result = provider.refresh("some token", LocaleCompat.of("bs"), 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.NewData)

        result = provider.refresh("some token", LocaleCompat.of("bs"), 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.Skipped)
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
                        timestamp = Instant.ofEpochMilli(1000),
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
        var result = provider.refresh("some token", LocaleCompat.of("bs"), 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.NewData)

        provider.isRemoteDataInfoUpToDateCallback = { info, locale, randomValue ->
            assertEquals(remoteDataInfo, info)
            assertEquals(LocaleCompat.of("bs"), locale)
            assertEquals(randomValue, 100)
            true
        }

        result = provider.refresh("some token", LocaleCompat.of("bs"), 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.Skipped)
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
                        timestamp = Instant.ofEpochMilli(1000),
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
        var result = provider.refresh("some token", LocaleCompat.of("bs"), 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.NewData)

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

        result = provider.refresh("some token", LocaleCompat.of("bs"), 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.Skipped)
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

        val result = provider.refresh("some token", LocaleCompat.of("bs"), 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.Failed)
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

        val result = provider.refresh("some token", LocaleCompat.of("bs"), 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.Failed)
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

        val result = provider.refresh("some token", LocaleCompat.of("bs"), 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.Failed)
    }

    @Test
    public fun testIsCurrent(): TestResult = runTest {
        val remoteDataInfo = RemoteDataInfo(
            url = "example://",
            lastModified = "some last modified",
            source = RemoteDataSource.APP
        )

        // No data
        var isCurrent = provider.isCurrent(Locale.CANADA_FRENCH, 10, remoteDataInfo)
        assertFalse(isCurrent)


        provider.fetchRemoteDataCallback = { _, _, _ ->
            val refreshResult = RemoteDataApiClient.Result(
                remoteDataInfo,
                payloads = setOf(
                    RemoteDataPayload(
                        type = "some type",
                        timestamp = Instant.ofEpochMilli(1000),
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
        val result = provider.refresh("some token", Locale.CANADA_FRENCH, 100)
        assertTrue(result is RemoteDataProvider.RefreshResult.NewData)

        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ ->
            true
        }
        isCurrent = provider.isCurrent(Locale.CANADA_FRENCH, 100, remoteDataInfo)
        assertTrue(isCurrent)

        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ ->
            false
        }
        isCurrent = provider.isCurrent(Locale.CANADA_FRENCH, 100, remoteDataInfo)
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
                        timestamp = Instant.ofEpochMilli(1000),
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

    @Test
    public fun testStatus(): TestResult = runTest {
        val token = UUID.randomUUID().toString()
        val locale = Locale.CANADA_FRENCH
        val randomValue = 100

        refreshRemoteData(token, locale, randomValue)

        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ ->
            true
        }
        assertEquals(RemoteData.Status.UP_TO_DATE, provider.status(token, locale, randomValue))
    }

    @Test
    public fun testStatusAfter3Days(): TestResult = runTest {
        val token = UUID.randomUUID().toString()
        val locale = Locale.CANADA_FRENCH
        val randomValue = 100

        refreshRemoteData(token, locale, randomValue)

        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ ->
            true
        }

        clock.currentTime += (TimeUnit.DAYS.toMillis(3) - 1).milliseconds
        assertEquals(RemoteData.Status.UP_TO_DATE, provider.status(token, locale, randomValue + 1))

        clock.currentTime += (1).milliseconds
        assertEquals(RemoteData.Status.OUT_OF_DATE, provider.status(token, locale, randomValue + 1))
    }

    @Test
    public fun testStatusNotCurrent(): TestResult = runTest {
        val token = UUID.randomUUID().toString()
        val locale = Locale.CANADA_FRENCH
        val randomValue = 100

        refreshRemoteData(token, locale, randomValue)

        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ ->
            false
        }
        assertEquals(RemoteData.Status.OUT_OF_DATE, provider.status(token, locale, randomValue))
    }

    @Test
    public fun testStatusNoData(): TestResult = runTest {
        val token = UUID.randomUUID().toString()
        val locale = Locale.CANADA_FRENCH
        val randomValue = 100

        // No data
        assertEquals(RemoteData.Status.OUT_OF_DATE, provider.status(token, locale, randomValue))
    }

    /**
     * The refresh state is persisted under the key `timeMilliseconds`, named for its
     * epoch-millis encoding rather than for the property. Renaming it would make state
     * written by previous releases unreadable, defaulting the timestamp to the epoch and so
     * reporting remote data as out of date on every upgrade.
     */
    @Test
    public fun testRefreshStatePersistsTimestampUnderLegacyKey(): TestResult = runTest {
        val token = UUID.randomUUID().toString()
        val locale = Locale.CANADA_FRENCH
        val randomValue = 100
        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ -> true }

        refreshRemoteData(token, locale, randomValue)

        val stored = requireNotNull(provider.prefs.get(REFRESH_STATE_KEY)).requireMap()
        assertEquals(clock.currentTime.toEpochMilli(), stored.require("timeMilliseconds").getLong(0))
        assertNull(stored["timestamp"])
    }

    /** State written by a previous release must still be understood after an upgrade. */
    @Test
    public fun testRefreshStateWrittenByPreviousReleaseIsStillRead(): TestResult = runTest {
        val token = UUID.randomUUID().toString()
        val locale = Locale.CANADA_FRENCH
        val randomValue = 100
        provider.isRemoteDataInfoUpToDateCallback = { _, _, _ -> true }

        val remoteDataInfo = RemoteDataInfo(
            url = "example://",
            lastModified = "some last modified",
            source = RemoteDataSource.APP
        )
        provider.prefs.put(
            REFRESH_STATE_KEY,
            jsonMapOf(
                "changeToken" to token,
                "remoteDataInfo" to remoteDataInfo,
                "timeMilliseconds" to clock.currentTime.toEpochMilli()
            ).toJsonValue()
        )

        // A recognized timestamp means the state is current, not stale.
        assertEquals(RemoteData.Status.UP_TO_DATE, provider.status(token, locale, randomValue))
    }

    private suspend fun refreshRemoteData(token: String, locale: Locale, randomValue: Int) {
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
                        timestamp = Instant.ofEpochMilli(1000),
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

        provider.refresh(token, locale, randomValue)
    }

    private companion object {
        private val REFRESH_STATE_KEY =
            SyncPrefKey.json("RemoteDataProvider.${RemoteDataSource.APP.name}_refresh_state")
    }
}

internal class TestRemoteDataProvider(
    context: Context,
    clock: Clock,
    val prefs: PreferenceStore = PreferenceStore.inMemoryStore(context)
) : RemoteDataProvider(
    source = RemoteDataSource.APP,
    remoteDataStore = RemoteDataStore(context, "appKey", UUID.randomUUID().toString()),
    preferenceStore = prefs,
    clock = clock
) {
    var isRemoteDataInfoUpToDateCallback: ((RemoteDataInfo, Locale, Int) -> Boolean)? = null
    var fetchRemoteDataCallback: ((Locale, Int, RemoteDataInfo?) -> RequestResult<RemoteDataApiClient.Result>)? = null

    override fun isRemoteDataInfoUpToDate(
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
