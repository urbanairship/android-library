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
import com.google.common.truth.Truth.assertThat
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
        mockDispatcher,
        mockPrivacyManager,
        listOf(mockAppRemoteDataProvider, mockContactRemoteDataProvider)
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
        every { mockPrivacyManager.isAnyFeatureEnabled(any()) } returns true
        coEvery { mockAppRemoteDataProvider.refresh(any(), any(), any()) } returns RemoteDataProvider.RefreshResult.NewData()
        coEvery { mockContactRemoteDataProvider.refresh(any(), any(), any()) } returns RemoteDataProvider.RefreshResult.Skipped()

        remoteDataRefreshManager.refreshFlow.test {
            val jobResult = remoteDataRefreshManager.performRefresh(
                "some token",
                Locale.CANADA,
                100
            )

            assertEquals(JobResult.SUCCESS, jobResult)

            val results = setOf(awaitItem(), awaitItem())
            assertThat(results.size).isEqualTo(2)
            assertThat(
                results.any { value ->
                    return@any value.first == RemoteDataSource.APP &&
                            value.second is RemoteDataProvider.RefreshResult.NewData
                }
            ).isTrue()

            assertThat(
                results.any { value ->
                    return@any value.first == RemoteDataSource.CONTACT &&
                            value.second is RemoteDataProvider.RefreshResult.Skipped
                }
            ).isTrue()
        }

        coVerify { mockAppRemoteDataProvider.refresh("some token", Locale.CANADA, 100) }
        coVerify { mockContactRemoteDataProvider.refresh("some token", Locale.CANADA, 100) }
    }

    @Test
    public fun testRefreshFailed(): TestResult = runTest {
        every { mockPrivacyManager.isAnyFeatureEnabled(any()) } returns true
        coEvery { mockAppRemoteDataProvider.refresh(any(), any(), any()) } returns RemoteDataProvider.RefreshResult.Failed()
        coEvery { mockContactRemoteDataProvider.refresh(any(), any(), any()) } returns RemoteDataProvider.RefreshResult.Skipped()

        remoteDataRefreshManager.refreshFlow.test {
            val jobResult = remoteDataRefreshManager.performRefresh(
                "some token",
                Locale.CANADA,
                100
            )

            assertEquals(JobResult.RETRY, jobResult)

            val results = setOf(awaitItem(), awaitItem())
            assertThat(results.size).isEqualTo(2)
            assertThat(
                results.any { value ->
                    return@any value.first == RemoteDataSource.APP &&
                            value.second is RemoteDataProvider.RefreshResult.Failed
                }
            ).isTrue()

            assertThat(
                results.any { value ->
                    return@any value.first == RemoteDataSource.CONTACT &&
                            value.second is RemoteDataProvider.RefreshResult.Skipped
                }
            ).isTrue()
        }

        coVerify { mockAppRemoteDataProvider.refresh("some token", Locale.CANADA, 100) }
        coVerify { mockContactRemoteDataProvider.refresh("some token", Locale.CANADA, 100) }
    }

    @Test
    public fun testRefreshPrivacyManagerNotEnabled(): TestResult = runTest {
        every { mockPrivacyManager.isAnyFeatureEnabled(any()) } returns false

        remoteDataRefreshManager.refreshFlow.test {
            val jobResult = remoteDataRefreshManager.performRefresh(
                "some token",
                Locale.CANADA,
                100
            )

            assertEquals(JobResult.SUCCESS, jobResult)

            val results = setOf(awaitItem(), awaitItem())
            assertThat(results.size).isEqualTo(2)
            assertThat(
                results.any { value ->
                    return@any value.first == RemoteDataSource.APP &&
                            value.second is RemoteDataProvider.RefreshResult.Skipped
                }
            ).isTrue()

            assertThat(
                results.any { value ->
                    return@any value.first == RemoteDataSource.CONTACT &&
                            value.second is RemoteDataProvider.RefreshResult.Skipped
                }
            ).isTrue()
        }

        coVerify(exactly = 0) { mockAppRemoteDataProvider.refresh(any(), any(), any()) }
        coVerify(exactly = 0) { mockContactRemoteDataProvider.refresh(any(), any(), any()) }
    }
}
