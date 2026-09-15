package com.urbanairship.automation.remotedata

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.engine.AutomationEngineInterface
import com.urbanairship.automation.limits.FrequencyConstraint
import com.urbanairship.automation.limits.FrequencyLimitManager
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.minus
import com.urbanairship.util.plus
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class AutomationRemoteDataSubscriberTest {
    private val clock = TestClock().apply { currentTime = Instant.ofEpochMilli(1000) }

    private val testDispatcher = StandardTestDispatcher()

    private var updatesFlow = MutableSharedFlow<InAppRemoteData>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)
    private val remoteDataAccess: AutomationRemoteDataAccessInterface = mockk {
        every { this@mockk.updatesFlow } returns this@AutomationRemoteDataSubscriberTest.updatesFlow
        every { this@mockk.sourceFor(any()) } answers { getSource(firstArg()) }
    }

    private val engine: AutomationEngineInterface = mockk {
        coEvery { this@mockk.getSchedules() } returns emptyList()
        coJustRun { this@mockk.reconcileLedger() }
    }

    private val frequencyLimitManager: FrequencyLimitManager = mockk {
        coEvery { this@mockk.setConstraints(any()) } returns Result.success(Unit)
    }

    /**
     * In-memory stand-in for [AutomationSourceInfoStore] keyed by `(source, contactID)`. Avoids
     * the real implementation's async DAO calls so `advanceUntilIdle()` is sufficient to drive
     * the subscriber's processing — Room's executor doesn't participate.
     */
    private val sourceInfoState = mutableMapOf<Pair<RemoteDataSource, String?>, AutomationSourceInfo>()
    private fun keyFor(source: RemoteDataSource, contactID: String?): Pair<RemoteDataSource, String?> =
        when (source) {
            RemoteDataSource.APP -> source to null
            RemoteDataSource.CONTACT -> source to contactID
        }
    private val sourceInfoStore: AutomationSourceInfoStore = mockk {
        coEvery { getSourceInfo(any(), any()) } coAnswers {
            sourceInfoState[keyFor(firstArg(), secondArg())]
        }
        coEvery { setSourceInfo(any(), any(), any()) } coAnswers {
            sourceInfoState[keyFor(secondArg(), thirdArg())] = firstArg()
        }
    }

    private var subscriber: AutomationRemoteDataSubscriber = AutomationRemoteDataSubscriber(
        sourceInfoStore, remoteDataAccess, engine, frequencyLimitManager, "1.11", testDispatcher
    )

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testSchedulingAutomations(): TestResult = runTest {
        val appSchedules = makeSchedules(source = RemoteDataSource.APP)
        val contactSchedules = makeSchedules(source = RemoteDataSource.CONTACT)

        val data = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(appSchedules, emptyList()),
                    clock.now()
                ),
                RemoteDataSource.CONTACT to InAppRemoteData.Payload(
                    InAppRemoteData.Data(contactSchedules, emptyList()),
                    clock.now()
                )
            )
        )

        coJustRun { engine.upsertSchedules(any()) }

        subscriber.subscribe()

        advanceUntilIdle()
        updatesFlow.emit(data)
        advanceUntilIdle()

        coVerify(timeout = 2000) {
            engine.upsertSchedules(appSchedules)
            engine.upsertSchedules(contactSchedules)
        }
    }

    @Test
    public fun testEmptyPayloadStopsSchedules(): TestResult = runTest {
        val appSchedules = makeSchedules(RemoteDataSource.APP)
        coEvery { engine.getSchedules() } returns appSchedules

        val emptyData = InAppRemoteData(emptyMap())
        val scheduleIDs = appSchedules.map { it.identifier }

        coJustRun { engine.stopSchedules(any()) }

        subscriber.subscribe()
        advanceUntilIdle()

        assertTrue(updatesFlow.tryEmit(emptyData))
        advanceUntilIdle()

        coVerify(timeout = 1000) { engine.stopSchedules(eq(scheduleIDs)) }
    }

    @Test
    public fun testIgnoreSchedulesNoLongerScheduled(): TestResult = runTest {
        coEvery { engine.upsertSchedules(any()) } just runs

        subscriber.subscribe()
        advanceUntilIdle()

        clock.currentTime = Instant.ofEpochMilli(1)

        val firstUpdateSchedules = makeSchedules(RemoteDataSource.APP, 4)
        val firstUpdate = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(firstUpdateSchedules, emptyList()),
                    clock.now(),
                    remoteDataInfo = RemoteDataInfo(
                        url = "https://some.url",
                        lastModified = null,
                        source = RemoteDataSource.APP
                    )
                )
            )
        )

        updatesFlow.emit(firstUpdate)
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(firstUpdateSchedules) }
        coEvery { engine.getSchedules() } returns firstUpdateSchedules

        val secondUpdateSchedules = firstUpdateSchedules + makeSchedules(RemoteDataSource.APP, 4)
        val secondUpdate = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(secondUpdateSchedules, emptyList()),
                    clock.now() + 100.milliseconds,
                    remoteDataInfo = RemoteDataInfo(
                        url = "https://some.url",
                        lastModified = null,
                        source = RemoteDataSource.APP
                    )
                )
            )
        )

        updatesFlow.emit(secondUpdate)
        advanceUntilIdle()

        // Should still be the first update schedules since the second updates are older
        coVerify { engine.upsertSchedules(firstUpdateSchedules) }

    }

    @Test
    public fun testOlderSchedulesMinSDKVersion(): TestResult = runTest {
        coEvery { engine.upsertSchedules(any()) } just runs

        subscriber = AutomationRemoteDataSubscriber(
            sourceInfoStore, remoteDataAccess, engine, frequencyLimitManager, "1.0.0", testDispatcher
        )
        subscriber.subscribe()
        advanceUntilIdle()

        clock.currentTime = Instant.ofEpochMilli(1)

        val firstUpdateSchedules = makeSchedules(RemoteDataSource.APP, 4)
        val firstUpdate = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(firstUpdateSchedules, emptyList()),
                    clock.now(),
                    remoteDataInfo = RemoteDataInfo(
                        url = "https://some.url",
                        lastModified = null,
                        source = RemoteDataSource.APP
                    )
                )
            )
        )

        updatesFlow.emit(firstUpdate)
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(firstUpdateSchedules) }
        subscriber.unsubscribe()
        advanceUntilIdle()

        subscriber = AutomationRemoteDataSubscriber(sourceInfoStore, remoteDataAccess, engine, frequencyLimitManager, "2.0.0", testDispatcher)

        val secondUpdateSchedules = firstUpdateSchedules + makeSchedules(
            source = RemoteDataSource.APP,
            count = 4,
            minSDKVersion = "2.0.0"
        )

        val secondUpdate = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(secondUpdateSchedules, emptyList()),
                    clock.now() + 100.milliseconds
                )
            )
        )


        coEvery { engine.getSchedules() } returns firstUpdateSchedules

        subscriber.subscribe()
        advanceUntilIdle()

        updatesFlow.emit(secondUpdate)
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(secondUpdateSchedules) }
    }

    @Test
    public fun testSamePayloadSkipsAutomations(): TestResult = runTest {
        subscriber.subscribe()
        advanceUntilIdle()

        clock.currentTime = Instant.ofEpochMilli(1)

        val schedules = makeSchedules(RemoteDataSource.APP, 4)
        val update = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(schedules, emptyList()),
                    clock.now(),
                    remoteDataInfo = RemoteDataInfo(
                        url = "https://some.url",
                        lastModified = null,
                        source = RemoteDataSource.APP
                    )
                )
            )
        )


        coEvery { engine.upsertSchedules(any()) } just runs

        updatesFlow.emit(update)
        advanceUntilIdle()

        updatesFlow.emit(update)
        advanceUntilIdle()

        coVerify(exactly = 1) { engine.upsertSchedules(any()) }
    }

    @Test
    public fun testRemoteDataInfoChangeUpdatesSchedules(): TestResult = runTest {
        subscriber.subscribe()
        advanceUntilIdle()

        val remoteDataInfo = RemoteDataInfo(
            url = "https://some.url",
            lastModified = null,
            source = RemoteDataSource.APP
        )

        clock.currentTime = Instant.ofEpochMilli(1)

        val schedules = makeSchedules(RemoteDataSource.APP, 4).map {
            it.copyWith(metadata =  jsonMapOf(InAppRemoteData.REMOTE_INFO_METADATA_KEY to remoteDataInfo).toJsonValue())
        }

        var expectedSchedules = schedules
        coJustRun { engine.upsertSchedules(expectedSchedules) }

        val remoteData = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    data = InAppRemoteData.Data(schedules, listOf()),
                    timestamp = clock.now(),
                    remoteDataInfo = remoteDataInfo
                )
            )
        )

        assertTrue(
            updatesFlow.tryEmit(remoteData)
        )
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(expectedSchedules) }
        advanceUntilIdle()

        coEvery { engine.getSchedules() } returns schedules

        val updatedRemoteDataInfo = RemoteDataInfo(
            url = "https://some.other.url",
            lastModified = null,
            source = RemoteDataSource.APP
        )

        val updatedSchedules = schedules.map {
            it.also { it.copyWith(metadata =  jsonMapOf(InAppRemoteData.REMOTE_INFO_METADATA_KEY to updatedRemoteDataInfo).toJsonValue()) }
        }

        expectedSchedules = updatedSchedules

        assertTrue(
            updatesFlow.tryEmit(
                InAppRemoteData(
                    payload = mapOf(
                        RemoteDataSource.APP to InAppRemoteData.Payload(
                            data = InAppRemoteData.Data(updatedSchedules, emptyList()),
                            timestamp = clock.now(),
                            remoteDataInfo = updatedRemoteDataInfo
                        )
                    )
                )
            )
        )
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(expectedSchedules) }
    }

    @Test
    public fun testPayloadDateChangeAutomations(): TestResult = runTest {
        subscriber.subscribe()
        advanceUntilIdle()

        clock.currentTime = Instant.ofEpochMilli(1)
        val schedules = makeSchedules(RemoteDataSource.APP, 4)

        coJustRun { engine.upsertSchedules(schedules) }

        val remoteDataInfo = RemoteDataInfo(
            url = "https://some.other.url",
            lastModified = null,
            source = RemoteDataSource.APP
        )

        assertTrue(
            updatesFlow.tryEmit(
                InAppRemoteData(
                    payload = mapOf(
                        RemoteDataSource.APP to InAppRemoteData.Payload(
                            data = InAppRemoteData.Data(schedules, emptyList()),
                            timestamp = clock.now(),
                            remoteDataInfo = remoteDataInfo
                        )
                    )
                )
            )
        )
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(schedules) }

        advanceUntilIdle()

        coEvery { engine.getSchedules() } returns schedules

        // update again with different date
        assertTrue(
            updatesFlow.tryEmit(
                InAppRemoteData(
                    payload = mapOf(
                        RemoteDataSource.APP to InAppRemoteData.Payload(
                            data = InAppRemoteData.Data(schedules, emptyList()),
                            timestamp = clock.now() + 1.milliseconds,
                            remoteDataInfo = remoteDataInfo
                        )
                    )
                )
            )
        )
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(schedules) }

    }

    @Test
    public fun testConstraints(): TestResult = runTest {
        val appConstraints = listOf(
            FrequencyConstraint("foo", 100.seconds, 10),
            FrequencyConstraint("bar", 100.seconds, 10)
        )

        val contactConstraints = listOf(
            FrequencyConstraint("foo", 1.seconds, 1),
            FrequencyConstraint("baz", 1.seconds, 1)
        )

        val data = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    data = InAppRemoteData.Data(listOf(), appConstraints),
                    timestamp = clock.now()
                ),
                RemoteDataSource.CONTACT to InAppRemoteData.Payload(
                    data = InAppRemoteData.Data(listOf(), contactConstraints),
                    timestamp = clock.now()
                )
            )
        )

        subscriber.subscribe()
        advanceUntilIdle()


        coEvery { frequencyLimitManager.setConstraints(any()) } returns Result.success(Unit)
        updatesFlow.emit(data)
        advanceUntilIdle()

        coVerify { frequencyLimitManager.setConstraints(appConstraints + contactConstraints) }
    }

    @Test
    public fun testFailedScheduleRetriedOnSDKUpdate(): TestResult = runTest {
        coEvery { engine.upsertSchedules(any()) } just runs
        clock.currentTime = Instant.ofEpochMilli(1)

        val scheduleA = makeSchedule(RemoteDataSource.APP)
        val failedB = FailedScheduleRecord(
            identifier = "failed_schedule_B",
            createdDate = clock.currentTime.toEpochMilli(),
            minSDKVersion = null
        )

        subscriber = AutomationRemoteDataSubscriber(
            sourceInfoStore, remoteDataAccess, engine, frequencyLimitManager, "1.0.0", testDispatcher
        )
        subscriber.subscribe()
        advanceUntilIdle()

        updatesFlow.emit(makeUpdate(listOf(scheduleA), listOf(failedB)))
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(listOf(scheduleA)) }
        assertEquals(listOf(failedB), trackedFailures())

        subscriber.unsubscribe()
        advanceUntilIdle()
        coEvery { engine.getSchedules() } returns listOf(scheduleA)

        // New SDK version can now parse B. Its created date is not newer than the last payload
        // timestamp, so only the recovery bypass can get it scheduled.
        val scheduleB = makeSchedule(RemoteDataSource.APP, identifier = failedB.identifier)

        subscriber = AutomationRemoteDataSubscriber(
            sourceInfoStore, remoteDataAccess, engine, frequencyLimitManager, "2.0.0", testDispatcher
        )
        subscriber.subscribe()
        advanceUntilIdle()

        updatesFlow.emit(makeUpdate(listOf(scheduleA, scheduleB), emptyList()))
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(listOf(scheduleA, scheduleB)) }
        assertNull(trackedFailures())
    }

    @Test
    public fun testFailedScheduleRecoveredOnServerFix(): TestResult = runTest {
        coEvery { engine.upsertSchedules(any()) } just runs
        clock.currentTime = Instant.ofEpochMilli(1)

        val scheduleA = makeSchedule(RemoteDataSource.APP)
        val failedB = FailedScheduleRecord(
            identifier = "failed_schedule_B",
            createdDate = clock.currentTime.toEpochMilli(),
            minSDKVersion = null
        )

        subscriber.subscribe()
        advanceUntilIdle()

        updatesFlow.emit(makeUpdate(listOf(scheduleA), listOf(failedB)))
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(listOf(scheduleA)) }
        coEvery { engine.getSchedules() } returns listOf(scheduleA)

        // Server republishes B in a form we can parse. B keeps its original created date, which is
        // older than the checkpoint we advanced to on the first sync.
        val scheduleB = makeSchedule(RemoteDataSource.APP, identifier = failedB.identifier)

        updatesFlow.emit(
            makeUpdate(listOf(scheduleA, scheduleB), emptyList(), timestamp = clock.currentTime.plusMillis(100))
        )
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(listOf(scheduleA, scheduleB)) }
        assertNull(trackedFailures())
    }

    @Test
    public fun testFailedScheduleRemovedFromRemoteData(): TestResult = runTest {
        coEvery { engine.upsertSchedules(any()) } just runs
        clock.currentTime = Instant.ofEpochMilli(1)

        val scheduleA = makeSchedule(RemoteDataSource.APP)
        val failedB = FailedScheduleRecord(
            identifier = "failed_schedule_B",
            createdDate = clock.currentTime.toEpochMilli(),
            minSDKVersion = null
        )

        subscriber.subscribe()
        advanceUntilIdle()

        updatesFlow.emit(makeUpdate(listOf(scheduleA), listOf(failedB)))
        advanceUntilIdle()

        assertEquals(listOf(failedB), trackedFailures())
        coEvery { engine.getSchedules() } returns listOf(scheduleA)

        // B is gone from remote data entirely, so we stop tracking it and never schedule it.
        updatesFlow.emit(
            makeUpdate(listOf(scheduleA), emptyList(), timestamp = clock.currentTime.plusMillis(100))
        )
        advanceUntilIdle()

        assertNull(trackedFailures())
        coVerify(exactly = 0) {
            engine.upsertSchedules(match { schedules ->
                schedules.any { it.identifier == failedB.identifier }
            })
        }
    }

    @Test
    public fun testSamePayloadWithFailuresSkipsAutomations(): TestResult = runTest {
        coEvery { engine.upsertSchedules(any()) } just runs
        clock.currentTime = Instant.ofEpochMilli(1)

        val update = makeUpdate(
            schedules = listOf(makeSchedule(RemoteDataSource.APP)),
            failedSchedules = listOf(
                FailedScheduleRecord(
                    identifier = "failed_schedule_B",
                    createdDate = clock.currentTime.toEpochMilli(),
                    minSDKVersion = null
                )
            )
        )

        subscriber.subscribe()
        advanceUntilIdle()

        updatesFlow.emit(update)
        advanceUntilIdle()

        updatesFlow.emit(update)
        advanceUntilIdle()

        coVerify(exactly = 1) { engine.upsertSchedules(any()) }
    }

    private fun trackedFailures(): List<FailedScheduleRecord>? =
        sourceInfoState[RemoteDataSource.APP to null]?.failedSchedules

    private fun makeUpdate(
        schedules: List<AutomationSchedule>,
        failedSchedules: List<FailedScheduleRecord>,
        timestamp: Instant = clock.now()
    ): InAppRemoteData {
        return InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    data = InAppRemoteData.Data(schedules, emptyList(), failedSchedules),
                    timestamp = timestamp,
                    remoteDataInfo = RemoteDataInfo(
                        url = "https://some.url",
                        lastModified = null,
                        source = RemoteDataSource.APP
                    )
                )
            )
        )
    }

    private fun makeSchedules(
        source: RemoteDataSource,
        count: Int = Random.nextInt(1, 10),
        minSDKVersion: String? = null,
        created: Instant = clock.now()
    ) : List<AutomationSchedule> {
        return (0 until count)
            .map { makeSchedule(source, minSDKVersion, created) }
    }

    private fun makeSchedule(
        source: RemoteDataSource,
        minSDKVersion: String? = null,
        created: Instant = clock.now(),
        identifier: String = UUID.randomUUID().toString()
    ) : AutomationSchedule {
        val remoteDataInfo = RemoteDataInfo(
            url = "https://test.url",
            lastModified = null,
            source = source
        )

        return AutomationSchedule(
            identifier = identifier,
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.wrap("actions")),
            triggers = listOf(AutomationTrigger.activeSession(1u)),
            created = created,
            metadata = jsonMapOf(InAppRemoteData.REMOTE_INFO_METADATA_KEY to remoteDataInfo).toJsonValue(),
            minSDKVersion = minSDKVersion
        )
    }

    private fun getSource(schedule: AutomationSchedule): RemoteDataSource? {
        return schedule.metadata
            ?.optMap()
            ?.get(InAppRemoteData.REMOTE_INFO_METADATA_KEY)
            ?.let { RemoteDataInfo(it) }
            ?.source
    }

    /**
     * Ledger cleanup runs once per update, after the schedules missing from the
     * listing have been synced — so retention sees the post-sync schedule set.
     */
    @Test
    public fun testReconcileLedgerRunsAfterUpdate(): TestResult = runTest {
        val appSchedules = makeSchedules(source = RemoteDataSource.APP)
        val data = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(appSchedules, emptyList()),
                    clock.now()
                )
            )
        )

        coJustRun { engine.upsertSchedules(any()) }

        subscriber.subscribe()
        advanceUntilIdle()
        updatesFlow.emit(data)
        advanceUntilIdle()

        coVerify(timeout = 2000, exactly = 1) { engine.reconcileLedger() }
        coVerifyOrder {
            engine.upsertSchedules(appSchedules)
            engine.reconcileLedger()
        }
    }

    /**
     * A failed cleanup must not tear down the subscription: the next update
     * still syncs and still reconciles.
     */
    @Test
    public fun testReconcileLedgerErrorIsSwallowed(): TestResult = runTest {
        val firstSchedules = makeSchedules(source = RemoteDataSource.APP)

        coJustRun { engine.upsertSchedules(any()) }
        coJustRun { engine.stopSchedules(any()) }
        coEvery { engine.reconcileLedger() } throws IllegalStateException("reconcile failed")

        subscriber.subscribe()
        advanceUntilIdle()

        updatesFlow.emit(
            InAppRemoteData(
                payload = mapOf(
                    RemoteDataSource.APP to InAppRemoteData.Payload(
                        InAppRemoteData.Data(firstSchedules, emptyList()),
                        clock.now()
                    )
                )
            )
        )
        advanceUntilIdle()

        // Created after the clock moves, so the second update reads as newer
        // than the source info the first one stored.
        clock.currentTime = clock.currentTime.plusMillis(1)
        val secondSchedules = makeSchedules(source = RemoteDataSource.APP)

        updatesFlow.emit(
            InAppRemoteData(
                payload = mapOf(
                    RemoteDataSource.APP to InAppRemoteData.Payload(
                        InAppRemoteData.Data(secondSchedules, emptyList()),
                        clock.now()
                    )
                )
            )
        )
        advanceUntilIdle()

        coVerify(timeout = 2000) { engine.upsertSchedules(secondSchedules) }
        coVerify(exactly = 2) { engine.reconcileLedger() }
    }
}
