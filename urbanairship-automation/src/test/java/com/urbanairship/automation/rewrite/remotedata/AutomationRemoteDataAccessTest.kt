package com.urbanairship.automation.rewrite.remotedata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.automation.rewrite.AutomationSchedule
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.Network
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationRemoteDataAccessTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val network: Network = mockk()
    private val remoteData: RemoteData = mockk()
    private val clock = TestClock()

    private lateinit var subject: AutomationRemoteDataAccess

    @Before
    public fun setup() {
        every { remoteData.payloadFlow(eq(listOf("in_app_messages"))) } returns flowOf()

        subject = AutomationRemoteDataAccess(context, remoteData, network)
    }

    @Test
    public fun testIsCurrentTrue(): TestResult = runTest {
        val info = makeRemoteDataInfo()
        every { remoteData.isCurrent(any()) } returns true

        val isCurrent = subject.isCurrent(makeSchedule(info))
        assertTrue(isCurrent)
        verify { remoteData.isCurrent(eq(info)) }
    }

    @Test
    public fun testIsCurrentFalse(): TestResult = runTest {
        val info = makeRemoteDataInfo()
        every { remoteData.isCurrent(any()) } returns false

        val isCurrent = subject.isCurrent(makeSchedule(info))
        assertFalse(isCurrent)
        verify { remoteData.isCurrent(eq(info)) }
    }

    @Test
    public fun testIsCurrentNilRemoteDataInfo(): TestResult = runTest {
        every { remoteData.isCurrent(any()) } returns true

        val isCurrent = subject.isCurrent(makeSchedule())
        assertFalse(isCurrent)
    }

    @Test
    public fun testRequiresUpdateUpToDate(): TestResult = runTest {
        val info = makeRemoteDataInfo()
        val schedule = makeSchedule(info)

        every { remoteData.isCurrent(any()) } returns true
        every { remoteData.status(any()) } answers {
            if (RemoteDataSource.APP == firstArg()) {
                RemoteData.Status.UP_TO_DATE
            } else {
                RemoteData.Status.STALE
            }
        }

        assertFalse(subject.requiredUpdate(schedule))
        verify { remoteData.isCurrent(eq(info)) }
        verify { remoteData.status(eq(RemoteDataSource.APP)) }
    }

    @Test
    public fun testRequiresUpdateStale(): TestResult = runTest {
        val info = makeRemoteDataInfo()
        val schedule = makeSchedule(info)

        every { remoteData.isCurrent(any()) } returns true
        every { remoteData.status(any()) } answers {
            if (RemoteDataSource.APP == firstArg()) {
                RemoteData.Status.STALE
            } else {
                RemoteData.Status.UP_TO_DATE
            }
        }

        assertFalse(subject.requiredUpdate(schedule))
        verify { remoteData.isCurrent(eq(info)) }
        verify { remoteData.status(eq(RemoteDataSource.APP)) }
    }

    @Test
    public fun testRequiresUpdateOutOfDate(): TestResult = runTest {
        val info = makeRemoteDataInfo()
        val schedule = makeSchedule(info)

        every { remoteData.isCurrent(any()) } returns true
        every { remoteData.status(any()) } answers {
            if (RemoteDataSource.APP == firstArg()) {
                RemoteData.Status.OUT_OF_DATE
            } else {
                RemoteData.Status.UP_TO_DATE
            }
        }

        assertTrue(subject.requiredUpdate(schedule))
        verify { remoteData.isCurrent(eq(info)) }
        verify { remoteData.status(eq(RemoteDataSource.APP)) }
    }

    @Test
    public fun testRequiresUpdateNotCurrent(): TestResult = runTest {
        val info = makeRemoteDataInfo()
        val schedule = makeSchedule(info)

        every { remoteData.isCurrent(any()) } returns false
        every { remoteData.status(any()) } answers {
            if (RemoteDataSource.APP == firstArg()) {
                RemoteData.Status.UP_TO_DATE
            } else {
                RemoteData.Status.OUT_OF_DATE
            }
        }

        assertTrue(subject.requiredUpdate(schedule))
        verify { remoteData.isCurrent(eq(info)) }
        verify(exactly = 0) { remoteData.status(eq(RemoteDataSource.APP)) }
    }

    @Test
    public fun testRequiresUpdateNilRemoteDataInfo(): TestResult = runTest {
        val schedule = makeSchedule(null)

        every { remoteData.isCurrent(any()) } returns false
        every { remoteData.status(any()) } answers {
            if (RemoteDataSource.APP == firstArg()) {
                RemoteData.Status.UP_TO_DATE
            } else {
                RemoteData.Status.OUT_OF_DATE
            }
        }

        assertTrue(subject.requiredUpdate(schedule))
        verify(exactly = 0) { remoteData.status(eq(RemoteDataSource.APP)) }
    }

    @Test
    public fun testRequiresUpdateRightSource(): TestResult = runTest {
        every { remoteData.isCurrent(any()) } returns true
        every { remoteData.status(any()) } answers {
            val source: RemoteDataSource = firstArg()
            when(source) {
                RemoteDataSource.APP -> RemoteData.Status.OUT_OF_DATE
                RemoteDataSource.CONTACT -> RemoteData.Status.UP_TO_DATE
            }
        }

        assertFalse(subject.requiredUpdate(makeSchedule(makeRemoteDataInfo(RemoteDataSource.CONTACT))))
        assertTrue(subject.requiredUpdate(makeSchedule(makeRemoteDataInfo(RemoteDataSource.APP))))
        verify { remoteData.status(eq(RemoteDataSource.CONTACT)) }
        verify { remoteData.status(eq(RemoteDataSource.APP)) }
    }

    @Test
    public fun testWaitForFullRefresh(): TestResult = runTest {
        val schedule = makeSchedule(makeRemoteDataInfo(RemoteDataSource.CONTACT))

        coEvery { remoteData.waitForRefresh(any(), any()) } answers {
            assertEquals(RemoteDataSource.CONTACT, firstArg())
            assertNull(secondArg())
        }

        subject.waitForFullRefresh(schedule)
        coVerify { remoteData.waitForRefresh(any(), any()) }
    }

    @Test
    public fun testWaitForFullRefreshNilInfo(): TestResult = runTest {
        coEvery { remoteData.waitForRefresh(any(), any()) } answers {
            assertEquals(RemoteDataSource.APP, firstArg())
            assertNull(secondArg())
        }
        subject.waitForFullRefresh(makeSchedule())
        coVerify { remoteData.waitForRefresh(any(), any()) }
    }

    @Test
    public fun testBestEffortRefresh(): TestResult = runTest {
        coEvery { network.isConnected(any()) } returns true
        every { remoteData.isCurrent(any()) } returns true
        every { remoteData.status(any()) } answers {
            val source: RemoteDataSource = firstArg()
            when(source) {
                RemoteDataSource.APP -> RemoteData.Status.UP_TO_DATE
                RemoteDataSource.CONTACT -> RemoteData.Status.STALE
            }
        }

        coEvery { remoteData.waitForRefreshAttempt(any(), any()) } answers {
            assertEquals(RemoteDataSource.CONTACT, firstArg())
            assertNull(secondArg())
        }

        assertTrue(subject.bestEffortRefresh(makeSchedule(makeRemoteDataInfo(RemoteDataSource.CONTACT))))
        coVerify { remoteData.waitForRefreshAttempt(any(), any()) }
    }

    @Test
    public fun testBestEffortRefreshNotCurrentAfterAttempt(): TestResult = runTest {
        var isCurrentRemoteData = true
        coEvery { network.isConnected(any()) } returns true
        every { remoteData.isCurrent(any()) } answers { isCurrentRemoteData }
        every { remoteData.status(any()) } answers {
            val source: RemoteDataSource = firstArg()
            when(source) {
                RemoteDataSource.APP -> RemoteData.Status.UP_TO_DATE
                RemoteDataSource.CONTACT -> RemoteData.Status.STALE
            }
        }

        coEvery { remoteData.waitForRefreshAttempt(any(), any()) } answers {
            assertEquals(RemoteDataSource.CONTACT, firstArg())
            assertNull(secondArg())
            isCurrentRemoteData = false
        }

        assertFalse(subject.bestEffortRefresh(makeSchedule(makeRemoteDataInfo(RemoteDataSource.CONTACT))))
        coVerify { remoteData.waitForRefreshAttempt(any(), any()) }
    }

    @Test
    public fun testBestEffortRefreshNotCurrentReturnsNil(): TestResult = runTest {
        coEvery { network.isConnected(any()) } returns true
        every { remoteData.isCurrent(any()) } answers { false }
        every { remoteData.status(any()) } answers {
            val source: RemoteDataSource = firstArg()
            when(source) {
                RemoteDataSource.APP -> RemoteData.Status.UP_TO_DATE
                RemoteDataSource.CONTACT -> RemoteData.Status.STALE
            }
        }

        coEvery { remoteData.waitForRefreshAttempt(any(), any()) } answers {
            fail()
        }

        assertFalse(subject.bestEffortRefresh(makeSchedule(makeRemoteDataInfo(RemoteDataSource.CONTACT))))
    }

    @Test
    public fun testBestEffortRefreshNotConnected(): TestResult = runTest {
        coEvery { network.isConnected(any()) } returns false
        every { remoteData.isCurrent(any()) } answers { true }
        every { remoteData.status(any()) } answers {
            val source: RemoteDataSource = firstArg()
            when(source) {
                RemoteDataSource.APP -> RemoteData.Status.UP_TO_DATE
                RemoteDataSource.CONTACT -> RemoteData.Status.STALE
            }
        }

        coEvery { remoteData.waitForRefreshAttempt(any(), any()) } answers {
            fail()
        }

        assertTrue(subject.bestEffortRefresh(makeSchedule(makeRemoteDataInfo(RemoteDataSource.CONTACT))))
    }

    private fun makeRemoteDataInfo(source: RemoteDataSource = RemoteDataSource.APP): RemoteDataInfo {
        return RemoteDataInfo(
            url = "https://airship.test",
            lastModified = null,
            source = source
        )
    }

    private fun makeSchedule(remoteDataInfo: RemoteDataInfo? = null): AutomationSchedule {
        return AutomationSchedule(
            identifier = "schedule id",
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.NULL),
            triggers = listOf(),
            created = clock.currentTimeMillis.toULong(),
            metadata = jsonMapOf("com.urbanairship.iaa.REMOTE_DATA_INFO" to (remoteDataInfo ?: "")).toJsonValue()
        )
    }
}
