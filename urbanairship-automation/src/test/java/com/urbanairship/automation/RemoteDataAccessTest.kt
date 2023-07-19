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
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class RemoteDataAccessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val remoteData: RemoteData = mockk()
    private val network: Network = mockk()
    private val subject = RemoteDataAccess(
        context, remoteData, network, AirshipDispatchers.newSerialDispatcher()
    )

    @Test
    public fun testRemoteDataCalledOncePerSource(): TestResult = runTest {
        val statuses = RemoteDataSource
            .values()
            .associateWith { RemoteData.Status.OUT_OF_DATE }
            .toMutableMap()

        coEvery { network.isConnected(any()) } returns true
        coEvery { remoteData.isCurrent(any()) } returns true
        coEvery { remoteData.refresh(any<RemoteDataSource>()) } answers {
            statuses[firstArg()] = RemoteData.Status.UP_TO_DATE
            true
        }
        coEvery { remoteData.status(any()) } answers { statuses[firstArg()]!! }

        val appSourceResponses = (1..5).map {
            subject.refreshAndCheckCurrentSync(makeRemoteDataInfo(RemoteDataSource.APP))
        }

        val contactSourceResponses = (1..5).map {
            subject.refreshAndCheckCurrentSync(makeRemoteDataInfo(RemoteDataSource.CONTACT))
        }

        Assert.assertTrue(appSourceResponses.all { it })
        Assert.assertTrue(contactSourceResponses.all { it })

        coVerify(exactly = 1) { remoteData.refresh(RemoteDataSource.APP) }
        coVerify(exactly = 1) { remoteData.refresh(RemoteDataSource.CONTACT) }
    }

    @Test
    public fun testRemoteDataCalledOncePerSourceParallel(): TestResult = runTest {
        val statuses = RemoteDataSource
            .values()
            .associateWith { RemoteData.Status.OUT_OF_DATE }
            .toMutableMap()

        coEvery { network.isConnected(any()) } returns true
        coEvery { remoteData.isCurrent(any()) } returns true
        coEvery { remoteData.refresh(any<RemoteDataSource>()) } coAnswers {
            val appSource: RemoteDataSource = firstArg()
            statuses[appSource] = RemoteData.Status.UP_TO_DATE
            true
        }
        coEvery { remoteData.status(any()) } answers { statuses[firstArg()]!! }

        val appSourceResponses = (1..5).map {
            async { subject.refreshAndCheckCurrentSync(makeRemoteDataInfo(RemoteDataSource.APP)) }
        }

        Assert.assertTrue(appSourceResponses.all { it.await() })
        coVerify(exactly = 1) { remoteData.refresh(RemoteDataSource.APP) }
    }

    @Test
    public fun testRefreshEachForeground(): TestResult = runTest {
        var sourceStatus = RemoteData.Status.OUT_OF_DATE
        coEvery { network.isConnected(any()) } returns true
        coEvery { remoteData.isCurrent(any()) } returns true
        coEvery { remoteData.refresh(any<RemoteDataSource>()) } returns true
        coEvery { remoteData.status(any()) } answers { sourceStatus }

        Assert.assertTrue(
            subject.refreshAndCheckCurrentSync(makeRemoteDataInfo(RemoteDataSource.APP))
        )

        Assert.assertTrue(
            subject.refreshAndCheckCurrentSync(makeRemoteDataInfo(RemoteDataSource.APP))
        )
        sourceStatus = RemoteData.Status.UP_TO_DATE

        Assert.assertTrue(
            subject.refreshAndCheckCurrentSync(makeRemoteDataInfo(RemoteDataSource.APP))
        )

        coVerify(exactly = 2) { remoteData.refresh(RemoteDataSource.APP) }
    }

    @Test
    public fun testSkipRefreshNotConnected(): TestResult = runTest {
        coEvery { network.isConnected(any()) } returns false
        coEvery { remoteData.isCurrent(any()) } returns true
        coEvery { remoteData.status(any()) } returns RemoteData.Status.OUT_OF_DATE

        Assert.assertTrue(
            subject.refreshAndCheckCurrentSync(makeRemoteDataInfo(RemoteDataSource.APP))
        )

        coVerify(exactly = 0) { remoteData.refresh(RemoteDataSource.APP) }
    }

    @Test
    public fun testRequireRefreshNotCurrent(): TestResult = runTest {
        coEvery { network.isConnected(any()) } returns false
        coEvery { remoteData.isCurrent(any()) } returnsMany listOf(false, true)
        coEvery { remoteData.refresh(any<RemoteDataSource>()) } returns true

        Assert.assertTrue(
            subject.refreshAndCheckCurrentSync(makeRemoteDataInfo(RemoteDataSource.APP))
        )

        coVerify(exactly = 1) { remoteData.refresh(RemoteDataSource.APP) }
    }

    @Test
    public fun testFailToRefresh(): TestResult = runTest {
        coEvery { network.isConnected(any()) } returns true
        coEvery { remoteData.isCurrent(any()) } returns false
        coEvery { remoteData.refresh(any<RemoteDataSource>()) } returns false

        (1..5).forEach { _ ->
            Assert.assertFalse(
                subject.refreshAndCheckCurrentSync(makeRemoteDataInfo(RemoteDataSource.APP))
            )
        }

        coVerify(exactly = 5) { remoteData.refresh(RemoteDataSource.APP) }
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
