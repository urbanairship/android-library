/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class AppRemoteDataProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val mockUrlFactory: RemoteDataUrlFactory = mockk()
    private val mockApiClient: RemoteDataApiClient = mockk()

    private val provider: AppRemoteDataProvider = AppRemoteDataProvider(
        context,
        PreferenceDataStore.inMemoryStore(context),
        TestAirshipRuntimeConfig.newTestConfig(),
        mockApiClient,
        mockUrlFactory
    )

    @Test
    public fun testIsRemoteDataInfoUpToDate(): TestResult = runTest {
        val remoteDataInfo = RemoteDataInfo(
            url = "some url",
            lastModified = "anything",
            source = RemoteDataSource.APP
        )

        every {
            mockUrlFactory.createAppUrl(Locale.CANADA_FRENCH, 100)
        } returns Uri.parse("some url")
        assertTrue(provider.isRemoteDataInfoUpToDate(remoteDataInfo, Locale.CANADA_FRENCH, 100))
    }

    @Test
    public fun testIsRemoteDataInfoUpToDateMismatchUrl(): TestResult = runTest {
        val remoteDataInfo = RemoteDataInfo(
            url = "some url",
            lastModified = "anything",
            source = RemoteDataSource.CONTACT,
            contactId = "some contact id"
        )

        every {
            mockUrlFactory.createAppUrl(Locale.CANADA_FRENCH, 100)
        } returns Uri.parse("some other url")
        assertFalse(provider.isRemoteDataInfoUpToDate(remoteDataInfo, Locale.CANADA_FRENCH, 100))
    }

    @Test
    public fun testFetchRemoteData(): TestResult = runTest {
        val result = RequestResult<RemoteDataApiClient.Result>(
            304, null, null, null, null
        )

        val uri = Uri.parse("some uri")

        coEvery {
            mockApiClient.fetch(uri, RequestAuth.GeneratedAppToken, null, any())
        } returns result

        every {
            mockUrlFactory.createAppUrl(Locale.CANADA_FRENCH, 100)
        } returns uri

        assertEquals(
            result,
            provider.fetchRemoteData(Locale.CANADA_FRENCH, 100, null)
        )
    }

    @Test
    public fun testFetchRemoteDataLastModified(): TestResult = runTest {
        val result = RequestResult<RemoteDataApiClient.Result>(
            304, null, null, null, null
        )

        val remoteDataInfo = RemoteDataInfo(
            url = "some uri",
            lastModified = "some last modified",
            source = RemoteDataSource.APP
        )

        val uri = Uri.parse("some uri")

        every {
            mockUrlFactory.createAppUrl(Locale.CANADA_FRENCH, 100)
        } returns uri

        coEvery {
            mockApiClient.fetch(uri, RequestAuth.GeneratedAppToken, "some last modified", any())
        } returns result

        assertEquals(
            result,
            provider.fetchRemoteData(Locale.CANADA_FRENCH, 100, remoteDataInfo)
        )
    }

    @Test
    public fun testFetchRemoteDataLastModifiedRemovedDifferentUrl(): TestResult = runTest {
        val result = RequestResult<RemoteDataApiClient.Result>(
            304, null, null, null, null
        )

        val remoteDataInfo = RemoteDataInfo(
            url = "some other uri",
            lastModified = "some last modified",
            source = RemoteDataSource.APP
        )

        val uri = Uri.parse("some uri")

        every {
            mockUrlFactory.createAppUrl(Locale.CANADA_FRENCH, 100)
        } returns uri

        coEvery {
            mockApiClient.fetch(uri, RequestAuth.GeneratedAppToken, null, any())
        } returns result

        assertEquals(
            result,
            provider.fetchRemoteData(Locale.CANADA_FRENCH, 100, remoteDataInfo)
        )
    }
}
