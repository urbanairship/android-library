/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import app.cash.turbine.test
import com.urbanairship.PrivacyManager
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class RemoteDataRefreshManagerTest {

    private val mockDispatcher: JobDispatcher = mockk(relaxed = true)
    private val mockPrivacyManager: PrivacyManager = mockk()

    private val mockContactRemoteDataProvider: RemoteDataProvider = mockk {
        every { this@mockk.source } returns RemoteDataSource.CONTACT
    }
    private val mockAppRemoteDataProvider: RemoteDataProvider = mockk {
        every { this@mockk.source } returns RemoteDataSource.APP
    }

    private val remoteDataRefreshManager = RemoteDataRefreshManager(
        mockDispatcher, mockPrivacyManager
    )

    @Test
    public fun testDispatch(): TestResult = runTest {
        val expected = JobInfo.newBuilder()
            .setAction(RemoteData.ACTION_REFRESH)
            .setNetworkAccessRequired(true)
            .setAirshipComponent(RemoteData::class.java)
            .setConflictStrategy(JobInfo.REPLACE)
            .build()

        remoteDataRefreshManager.dispatchRefreshJob()

        verify { mockDispatcher.dispatch(expected) }
    }

    @Test
    public fun testRefresh(): TestResult = runTest {
        every { mockPrivacyManager.isAnyFeatureEnabled } returns true
        coEvery { mockAppRemoteDataProvider.refresh(any(), any(), any()) } returns RemoteDataProvider.RefreshResult.NEW_DATA
        coEvery { mockContactRemoteDataProvider.refresh(any(), any(), any()) } returns RemoteDataProvider.RefreshResult.SKIPPED

        remoteDataRefreshManager.refreshFlow.test {
            val jobResult = remoteDataRefreshManager.performRefresh(
                "some token",
                Locale.CANADA,
                100,
                listOf(mockAppRemoteDataProvider, mockContactRemoteDataProvider)
            )

            assertEquals(JobResult.SUCCESS, jobResult)

            assertEquals(
                setOf(
                    Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.NEW_DATA),
                    Pair(RemoteDataSource.CONTACT, RemoteDataProvider.RefreshResult.SKIPPED),
                ),
                setOf(awaitItem(), awaitItem())
            )
        }

        coVerify { mockAppRemoteDataProvider.refresh("some token", Locale.CANADA, 100) }
        coVerify { mockContactRemoteDataProvider.refresh("some token", Locale.CANADA, 100) }
    }

    @Test
    public fun testRefreshFailed(): TestResult = runTest {
        every { mockPrivacyManager.isAnyFeatureEnabled } returns true
        coEvery { mockAppRemoteDataProvider.refresh(any(), any(), any()) } returns RemoteDataProvider.RefreshResult.FAILED
        coEvery { mockContactRemoteDataProvider.refresh(any(), any(), any()) } returns RemoteDataProvider.RefreshResult.SKIPPED

        remoteDataRefreshManager.refreshFlow.test {
            val jobResult = remoteDataRefreshManager.performRefresh(
                "some token",
                Locale.CANADA,
                100,
                listOf(mockAppRemoteDataProvider, mockContactRemoteDataProvider)
            )

            assertEquals(JobResult.RETRY, jobResult)

            assertEquals(
                setOf(
                    Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.FAILED),
                    Pair(RemoteDataSource.CONTACT, RemoteDataProvider.RefreshResult.SKIPPED),
                ),
                setOf(awaitItem(), awaitItem())
            )
        }

        coVerify { mockAppRemoteDataProvider.refresh("some token", Locale.CANADA, 100) }
        coVerify { mockContactRemoteDataProvider.refresh("some token", Locale.CANADA, 100) }
    }

    @Test
    public fun testRefreshPrivacyManagerNotEnabled(): TestResult = runTest {
        every { mockPrivacyManager.isAnyFeatureEnabled } returns false

        remoteDataRefreshManager.refreshFlow.test {
            val jobResult = remoteDataRefreshManager.performRefresh(
                "some token",
                Locale.CANADA,
                100,
                listOf(mockAppRemoteDataProvider, mockContactRemoteDataProvider)
            )

            assertEquals(JobResult.SUCCESS, jobResult)

            assertEquals(
                setOf(
                    Pair(RemoteDataSource.APP, RemoteDataProvider.RefreshResult.SKIPPED),
                    Pair(RemoteDataSource.CONTACT, RemoteDataProvider.RefreshResult.SKIPPED),
                ),
                setOf(awaitItem(), awaitItem())
            )
        }

        coVerify(exactly = 0) { mockAppRemoteDataProvider.refresh(any(), any(), any()) }
        coVerify(exactly = 0) { mockContactRemoteDataProvider.refresh(any(), any(), any()) }
    }
}
