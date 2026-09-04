package com.urbanairship.automation.remotedata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.automation.AutomationSchedule
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import com.urbanairship.json.JsonMap
import com.urbanairship.remotedata.RemoteDataPayload
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

    @Test
    public fun testUpdatesFlowCorruptPayloadEmitsEmpty(): TestResult = runTest {
        // Simulate a payload whose data map is missing the required "in_app_messages" key,
        // as happens when JsonMap.toString() previously stored SQL NULL and the row reads back
        // as an empty map.
        val corruptPayload = RemoteDataPayload(
            type = "in_app_messages",
            timestamp = 0L,
            data = JsonMap.EMPTY_MAP,
            remoteDataInfo = null
        )
        every { remoteData.payloadFlow(eq(listOf("in_app_messages"))) } returns flowOf(listOf(corruptPayload))

        subject = AutomationRemoteDataAccess(context, remoteData, network)

        val result = subject.updatesFlow.first()

        assertEquals(InAppRemoteData(emptyMap()), result)
    }

    @Test
    public fun testParseTracksFailedSchedules() {
        val data = parseData(
            listOf(VALID_SCHEDULE, INVALID_SCHEDULE_WITH_ID),
            payloadTimestamp = 999L
        )

        assertEquals(1, data.schedules.size)
        assertEquals("valid_schedule", data.schedules.first().identifier)

        assertEquals(listOf("failed_schedule_id"), data.failedSchedules.map { it.identifier })
        assertEquals(CREATED_MILLIS, data.failedSchedules.first().createdDate)
        assertEquals("18.0.0", data.failedSchedules.first().minSDKVersion)
    }

    @Test
    public fun testParseIgnoresFailedScheduleWithoutId() {
        // Without an ID there is nothing to track it by, so it stays dropped.
        val invalidWithoutId = """
            {
                "created": "2023-12-20T12:00:00Z",
                "type": "actions",
                "actions": { "foo": "bar" }
            }
        """.trimIndent()

        val data = parseData(listOf(VALID_SCHEDULE, invalidWithoutId), payloadTimestamp = 999L)

        assertEquals(1, data.schedules.size)
        assertTrue(data.failedSchedules.isEmpty())
    }

    @Test
    public fun testParseFallsBackToPayloadTimestampForMissingCreated() {
        val invalidWithoutCreated = """
            {
                "id": "failed_schedule_id",
                "type": "actions",
                "actions": { "foo": "bar" }
            }
        """.trimIndent()

        val data = parseData(listOf(invalidWithoutCreated), payloadTimestamp = 999L)

        assertEquals(999L, data.failedSchedules.first().createdDate)
        assertNull(data.failedSchedules.first().minSDKVersion)
    }

    @Test
    public fun testFromPayloadsAggregatesFailedSchedules() {
        // Covers the full payload path, including the metadata pass in parse() that rebuilds Data.
        val payload = RemoteDataPayload(
            type = "in_app_messages",
            timestamp = 999L,
            data = JsonValue
                .parseString("""{ "in_app_messages": [$VALID_SCHEDULE, $INVALID_SCHEDULE_WITH_ID] }""")
                .requireMap(),
            remoteDataInfo = makeRemoteDataInfo()
        )

        val result = InAppRemoteData.fromPayloads(listOf(payload))

        assertEquals(1, result.payload[RemoteDataSource.APP]?.data?.schedules?.size)
        assertEquals(listOf("failed_schedule_id"), result.failedSchedules.map { it.identifier })
        assertEquals(CREATED_MILLIS, result.failedSchedules.first().createdDate)
    }

    @Test
    public fun testCorruptPayloadDoesNotAffectOtherSources() {
        // Missing the required "in_app_messages" key, so the whole payload fails to parse.
        val corruptContact = RemoteDataPayload(
            type = "in_app_messages",
            timestamp = 999L,
            data = JsonMap.EMPTY_MAP,
            remoteDataInfo = makeRemoteDataInfo(RemoteDataSource.CONTACT)
        )

        val validApp = RemoteDataPayload(
            type = "in_app_messages",
            timestamp = 999L,
            data = JsonValue
                .parseString("""{ "in_app_messages": [$VALID_SCHEDULE] }""")
                .requireMap(),
            remoteDataInfo = makeRemoteDataInfo(RemoteDataSource.APP)
        )

        val result = InAppRemoteData.fromPayloads(listOf(validApp, corruptContact))

        // The APP source survives; only CONTACT reads as having no payload.
        assertEquals(
            listOf("valid_schedule"),
            result.payload[RemoteDataSource.APP]?.data?.schedules?.map { it.identifier }
        )
        assertNull(result.payload[RemoteDataSource.CONTACT])
    }

    @Test
    public fun testBadConstraintOnlyFailsItsOwnSource() {
        // A single malformed frequency constraint still fails its whole payload, but must not
        // reach across sources.
        val badConstraints = RemoteDataPayload(
            type = "in_app_messages",
            timestamp = 999L,
            data = JsonValue
                .parseString(
                    """{ "in_app_messages": [], "frequency_constraints": [ { "id": "no-range" } ] }"""
                )
                .requireMap(),
            remoteDataInfo = makeRemoteDataInfo(RemoteDataSource.CONTACT)
        )

        val validApp = RemoteDataPayload(
            type = "in_app_messages",
            timestamp = 999L,
            data = JsonValue
                .parseString("""{ "in_app_messages": [$VALID_SCHEDULE] }""")
                .requireMap(),
            remoteDataInfo = makeRemoteDataInfo(RemoteDataSource.APP)
        )

        val result = InAppRemoteData.fromPayloads(listOf(validApp, badConstraints))

        assertEquals(1, result.payload[RemoteDataSource.APP]?.data?.schedules?.size)
        assertNull(result.payload[RemoteDataSource.CONTACT])
    }

    private fun parseData(schedules: List<String>, payloadTimestamp: Long): InAppRemoteData.Data {
        val json = JsonValue
            .parseString("""{ "in_app_messages": [${schedules.joinToString(",")}] }""")
            .requireMap()

        return InAppRemoteData.Data.fromJson(json, payloadTimestamp)
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

    private companion object {
        const val CREATED_MILLIS: Long = 1703073600000L

        val VALID_SCHEDULE: String = """
            {
                "id": "valid_schedule",
                "created": "2023-12-20T12:00:00Z",
                "triggers": [
                    { "type": "custom_event_count", "goal": 1, "id": "json-id" }
                ],
                "type": "actions",
                "actions": { "foo": "bar" }
            }
        """.trimIndent()

        /** Missing the required "triggers" field. */
        val INVALID_SCHEDULE_WITH_ID: String = """
            {
                "id": "failed_schedule_id",
                "created": "2023-12-20T12:00:00Z",
                "min_sdk_version": "18.0.0",
                "type": "actions",
                "actions": { "foo": "bar" }
            }
        """.trimIndent()
    }
}
