/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.contacts.Contact
import com.urbanairship.contacts.ContactIdUpdate
import com.urbanairship.contacts.StableContactInfo
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
public class ContactRemoteDataProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val mockUrlFactory: RemoteDataUrlFactory = mockk()
    private val mockContact: Contact = mockk()
    private val mockApiClient: RemoteDataApiClient = mockk()

    private val provider: ContactRemoteDataProvider = ContactRemoteDataProvider(
        context,
        PreferenceDataStore.inMemoryStore(context),
        TestAirshipRuntimeConfig(),
        mockContact,
        mockApiClient,
        mockUrlFactory
    )

    @Test
    public fun testIsRemoteDataInfoUpToDate(): TestResult = runTest {
        val remoteDataInfo = RemoteDataInfo(
            url = "some url",
            lastModified = "anything",
            source = RemoteDataSource.CONTACT,
            contactId = "some contact id"
        )

        every {
            mockUrlFactory.createContactUrl("some contact id", Locale.CANADA_FRENCH, 100)
        } returns Uri.parse("some url")
        every { mockContact.currentContactIdUpdate } returns ContactIdUpdate("some contact id", null,true, 0)
        assertTrue(provider.isRemoteDataInfoUpToDate(remoteDataInfo, Locale.CANADA_FRENCH, 100))
    }

    @Test
    public fun testIsRemoteDataInfoUpToDateUnstableContactId(): TestResult = runTest {
        val remoteDataInfo = RemoteDataInfo(
            url = "some url",
            lastModified = "anything",
            source = RemoteDataSource.CONTACT,
            contactId = "some contact id"
        )

        every {
            mockUrlFactory.createContactUrl("some contact id", Locale.CANADA_FRENCH, 100)
        } returns Uri.parse("some url")
        every { mockContact.currentContactIdUpdate } returns ContactIdUpdate("some contact id", null, false, 0)
        assertFalse(provider.isRemoteDataInfoUpToDate(remoteDataInfo, Locale.CANADA_FRENCH, 100))
    }

    @Test
    public fun testIsRemoteDataInfoUpToDateMismatchContactId(): TestResult = runTest {
        val remoteDataInfo = RemoteDataInfo(
            url = "some url",
            lastModified = "anything",
            source = RemoteDataSource.CONTACT,
            contactId = "some contact id"
        )

        every {
            mockUrlFactory.createContactUrl("some contact id", Locale.CANADA_FRENCH, 100)
        } returns Uri.parse("some url")
        every { mockContact.currentContactIdUpdate } returns ContactIdUpdate("some other contact id", null, true, 0)
        assertFalse(provider.isRemoteDataInfoUpToDate(remoteDataInfo, Locale.CANADA_FRENCH, 100))
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
            mockUrlFactory.createContactUrl("some contact id", Locale.CANADA_FRENCH, 100)
        } returns Uri.parse("some other url")
        every { mockContact.currentContactIdUpdate } returns ContactIdUpdate("some contact id", null, true, 0)
        assertFalse(provider.isRemoteDataInfoUpToDate(remoteDataInfo, Locale.CANADA_FRENCH, 100))
    }

    @Test
    public fun testFetchRemoteData(): TestResult = runTest {
        val result = RequestResult<RemoteDataApiClient.Result>(
            304, null, null, null, null
        )

        val uri = Uri.parse("some uri")
        coEvery { mockContact.stableContactInfo() } returns StableContactInfo("stable", null)

        coEvery {
            mockApiClient.fetch(uri, RequestAuth.ContactTokenAuth("stable"), null, any())
        } returns result

        every {
            mockUrlFactory.createContactUrl("stable", Locale.CANADA_FRENCH, 100)
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
            source = RemoteDataSource.CONTACT,
            contactId = "stable"
        )

        val uri = Uri.parse("some uri")

        every {
            mockUrlFactory.createContactUrl("stable", Locale.CANADA_FRENCH, 100)
        } returns uri

        coEvery { mockContact.stableContactInfo() } returns StableContactInfo("stable", null)

        coEvery {
            mockApiClient.fetch(uri, RequestAuth.ContactTokenAuth("stable"), "some last modified", any())
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
            source = RemoteDataSource.CONTACT,
            contactId = "stable"
        )

        val uri = Uri.parse("some uri")

        every {
            mockUrlFactory.createContactUrl("stable", Locale.CANADA_FRENCH, 100)
        } returns uri

        coEvery { mockContact.stableContactInfo() } returns StableContactInfo("stable", null)

        coEvery {
            mockApiClient.fetch(uri, RequestAuth.ContactTokenAuth("stable"), null, any())
        } returns result

        assertEquals(
            result,
            provider.fetchRemoteData(Locale.CANADA_FRENCH, 100, remoteDataInfo)
        )
    }
}
