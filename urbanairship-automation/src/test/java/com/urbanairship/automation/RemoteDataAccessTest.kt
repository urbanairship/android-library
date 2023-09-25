/* Copyright Airship and Contributors */

package com.urbanairship.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.AirshipDispatchers
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.Network
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class RemoteDataAccessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val remoteData: RemoteData = mockk(relaxed = true)

    private val network: Network = mockk()
    private val subject = RemoteDataAccess(
        context, remoteData, network, AirshipDispatchers.newSerialDispatcher()
    )

    @Test
    public fun testRequiresRefresh(): TestResult = runTest {
        coEvery { remoteData.isCurrent(any()) } returns true
        coEvery { remoteData.status(any()) } returns RemoteData.Status.UP_TO_DATE

        Assert.assertFalse(
            subject.requiresRefresh(makeRemoteDataInfo(RemoteDataSource.APP))
        )
    }

    @Test
    public fun testRequiresRefreshStaleData(): TestResult = runTest {
        coEvery { remoteData.isCurrent(any()) } returns true
        coEvery { remoteData.status(any()) } returns RemoteData.Status.STALE

        Assert.assertFalse(
            subject.requiresRefresh(makeRemoteDataInfo(RemoteDataSource.APP))
        )
    }

    @Test
    public fun testRequiresRefreshNotCurrent(): TestResult = runTest {
        coEvery { remoteData.isCurrent(any()) } returns false
        coEvery { remoteData.status(any()) } returns RemoteData.Status.OUT_OF_DATE

        Assert.assertTrue(
            subject.requiresRefresh(makeRemoteDataInfo(RemoteDataSource.APP))
        )
    }

    @Test
    public fun testRequiresRefreshNoRemoteDataInfo(): TestResult = runTest {
        Assert.assertTrue(
            subject.requiresRefresh(null)
        )
    }

    @Test
    public fun testRequiresRefreshOutOfDate(): TestResult = runTest {
        coEvery { remoteData.isCurrent(any()) } returns true
        coEvery { remoteData.status(any()) } returns RemoteData.Status.OUT_OF_DATE

        Assert.assertTrue(
            subject.requiresRefresh(makeRemoteDataInfo(RemoteDataSource.APP))
        )
    }

    @Test
    public fun testBestEffort(): TestResult = runTest {
        coEvery { network.isConnected(any()) } returns false
        coEvery { remoteData.isCurrent(any()) } returns false
        coEvery { remoteData.status(any()) } returns RemoteData.Status.UP_TO_DATE
        coEvery { remoteData.waitForRefreshAttempt(any()) } just runs

        Assert.assertFalse(
            subject.bestEffortRefresh(makeRemoteDataInfo(RemoteDataSource.APP))
        )
    }

    @Test
    public fun testBestEffortRefreshSkipsNotConnected(): TestResult = runTest {
        coEvery { network.isConnected(any()) } returns false
        coEvery { remoteData.isCurrent(any()) } returns true
        coEvery { remoteData.status(any()) } returns RemoteData.Status.STALE

        Assert.assertTrue(
            subject.bestEffortRefresh(makeRemoteDataInfo(RemoteDataSource.APP))
        )
    }

    @Test
    public fun testBestEffortFailToRefresh(): TestResult = runTest(UnconfinedTestDispatcher()) {
        coEvery { network.isConnected(any()) } returns true
        coEvery { remoteData.isCurrent(any()) } returns true
        coEvery { remoteData.status(any()) } returns RemoteData.Status.STALE
        coEvery { remoteData.waitForRefreshAttempt(any()) } just runs

        Assert.assertTrue(
            subject.bestEffortRefresh(makeRemoteDataInfo(RemoteDataSource.APP))
        )
    }

    @Test
    public fun testBestEffortRefreshNoLongerCurrent(): TestResult = runTest(UnconfinedTestDispatcher()) {
        coEvery { network.isConnected(any()) } returns true
        coEvery { remoteData.isCurrent(any()) } returnsMany listOf(true, false)
        coEvery { remoteData.status(any()) } returns RemoteData.Status.STALE
        coEvery { remoteData.waitForRefreshAttempt(any()) } just runs

        Assert.assertFalse(
            subject.bestEffortRefresh(makeRemoteDataInfo(RemoteDataSource.APP))
        )
    }

    private fun makeRemoteDataInfo(source: RemoteDataSource = RemoteDataSource.APP): RemoteDataInfo {
        return RemoteDataInfo(
            UUID.randomUUID().toString(),
            null,
            source,
            null
        )
    }
}
