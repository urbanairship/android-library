package com.urbanairship.automation.rewrite.remotedata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestClock
import com.urbanairship.automation.rewrite.AutomationEngineInterface
import com.urbanairship.automation.rewrite.AutomationSchedule
import com.urbanairship.automation.rewrite.AutomationTrigger
import com.urbanairship.automation.rewrite.limits.FrequencyConstraint
import com.urbanairship.automation.rewrite.limits.FrequencyLimitManager
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationRemoteDataSubscriberTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val remoteDataAccess: AutomationRemoteDataAccessInterface = mockk()
    private val engine: AutomationEngineInterface = mockk()
    private val frequencyLimitManager: FrequencyLimitManager = mockk()
    private lateinit var subscriber: AutomationRemoteDataSubscriber
    private val clock = TestClock()
    private var updatesFlow = MutableSharedFlow<InAppRemoteData>(replay = 1)

    @Before
    public fun setup() {
        subscriber = AutomationRemoteDataSubscriber(dataStore, remoteDataAccess, engine, frequencyLimitManager, "1.11")
        every { remoteDataAccess.updatesFlow } answers { updatesFlow }
        every { remoteDataAccess.sourceFor(any()) } answers { getSource(firstArg()) }

        coEvery { frequencyLimitManager.setConstraints(any()) } returns Result.success(Unit)
        coEvery { engine.getSchedules() } returns emptyList()
    }

    @Test
    public fun testSchedulingAutomations(): TestResult = runTest(timeout = 15.seconds) {

        val appSchedules = makeSchedules(source = RemoteDataSource.APP)
        val contactSchedules = makeSchedules(source = RemoteDataSource.CONTACT)

        val data = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(appSchedules, emptyList()),
                    clock.currentTimeMillis()
                ),
                RemoteDataSource.CONTACT to InAppRemoteData.Payload(
                    InAppRemoteData.Data(contactSchedules, emptyList()),
                    clock.currentTimeMillis()
                )
            )
        )

        val job = Job()

        coEvery { engine.upsertSchedules(any()) } answers {
            if (appSchedules != firstArg() && contactSchedules != firstArg()) {
                fail()
            }

            if (contactSchedules == firstArg()) {
                job.complete()
            }
        }

        subscriber.subscribe()
        updatesFlow.tryEmit(data)

        job.join()

        coVerify(exactly = 1) { engine.upsertSchedules(eq(appSchedules)) }
        coVerify(exactly = 1) { engine.upsertSchedules(eq(contactSchedules)) }
    }

    @Test
    public fun testEmptyPayloadStopsSchedules(): TestResult = runTest(timeout = 20.seconds) {
        val appSchedules = makeSchedules(RemoteDataSource.APP)
        coEvery { engine.getSchedules() } returns appSchedules

        val emptyData = InAppRemoteData(emptyMap())
        val scheduleIDs = appSchedules.map { it.identifier }

        val job = Job()
        coEvery { engine.stopSchedules(any()) } answers {
            assertEquals(scheduleIDs, firstArg())
            job.complete()
        }

        subscriber.subscribe()
        updatesFlow.tryEmit(emptyData)

        job.join()

        coVerify { engine.stopSchedules(eq(scheduleIDs)) }
    }

    @Test
    public fun testIgnoreSchedulesNoLongerScheduled(): TestResult = runTest {
        subscriber.subscribe()

        clock.currentTimeMillis = 1

        val firstUpdateSchedules = makeSchedules(RemoteDataSource.APP, 4u)
        val firstUpdate = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(firstUpdateSchedules, emptyList()),
                    clock.currentTimeMillis(),
                    remoteDataInfo = RemoteDataInfo(
                        url = "https://some.url",
                        lastModified = null,
                        source = RemoteDataSource.APP
                    )
                )
            )
        )

        var updateJob = Job()

        coEvery { engine.upsertSchedules(any()) } answers {
            assertEquals(firstUpdateSchedules, firstArg())
            updateJob.complete()
        }

        updatesFlow.tryEmit(firstUpdate)
        updateJob.join()

        coEvery { engine.getSchedules() } returns firstUpdateSchedules

        val secondUpdateSchedules = firstUpdateSchedules + makeSchedules(RemoteDataSource.APP, 4u)
        val secondUpdate = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(secondUpdateSchedules, emptyList()),
                    clock.currentTimeMillis() + 100,
                    remoteDataInfo = RemoteDataInfo(
                        url = "https://some.url",
                        lastModified = null,
                        source = RemoteDataSource.APP
                    )
                )
            )
        )

        updateJob = Job()

        updatesFlow.tryEmit(secondUpdate)
        // Should still be the first update schedules since the second updates are older
        updateJob.join()
    }

    @Test
    public fun testOlderSchedulesMinSDKVersion(): TestResult = runTest {
        subscriber = AutomationRemoteDataSubscriber(dataStore, remoteDataAccess, engine, frequencyLimitManager, "1.0.0")
        subscriber.subscribe()

        clock.currentTimeMillis = 1

        val firstUpdateSchedules = makeSchedules(RemoteDataSource.APP, 4u)
        val firstUpdate = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(firstUpdateSchedules, emptyList()),
                    clock.currentTimeMillis(),
                    remoteDataInfo = RemoteDataInfo(
                        url = "https://some.url",
                        lastModified = null,
                        source = RemoteDataSource.APP
                    )
                )
            )
        )

        var updateJob = Job()
        var expectedSchedules = firstUpdateSchedules
        coEvery { engine.upsertSchedules(any()) } answers {
            assertEquals(expectedSchedules, firstArg())
            updateJob.complete()
        }

        updatesFlow.tryEmit(firstUpdate)
        updateJob.join()

        subscriber.unsubscribe()
        updatesFlow = MutableSharedFlow(replay = 1)

        subscriber = AutomationRemoteDataSubscriber(dataStore, remoteDataAccess, engine, frequencyLimitManager, "2.0.0")
        subscriber.subscribe()

        coEvery { engine.getSchedules() } returns firstUpdateSchedules

        val secondUpdateSchedules = firstUpdateSchedules + makeSchedules(
            source = RemoteDataSource.APP,
            count = 4u,
            minSDKVersion = "2.0.0"
        )
        val secondUpdate = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(secondUpdateSchedules, emptyList()),
                    clock.currentTimeMillis() + 100
                )
            )
        )
        expectedSchedules = secondUpdateSchedules
        updateJob = Job()

        updatesFlow.tryEmit(secondUpdate)
        updateJob.join()
    }

    @Test
    public fun testSamePayloadSkipsAutomations(): TestResult = runTest {
        subscriber.subscribe()

        clock.currentTimeMillis = 1

        val schedules = makeSchedules(RemoteDataSource.APP, 4u)
        val update = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    InAppRemoteData.Data(schedules, emptyList()),
                    clock.currentTimeMillis(),
                    remoteDataInfo = RemoteDataInfo(
                        url = "https://some.url",
                        lastModified = null,
                        source = RemoteDataSource.APP
                    )
                )
            )
        )

        val updateJob = Job()

        coEvery { engine.upsertSchedules(any()) } answers {
            assertEquals(schedules, firstArg())
            updateJob.complete()
        }

        updatesFlow.tryEmit(update)
        updatesFlow.tryEmit(update)

        updateJob.join()

        coVerify(exactly = 1) { engine.upsertSchedules(any()) }
    }

    @Test
    public fun testRemoteDataInfoChangeUpdatesSchedules(): TestResult = runTest {
        subscriber.subscribe()

        val remoteDataInfo = RemoteDataInfo(
            url = "https://some.url",
            lastModified = null,
            source = RemoteDataSource.APP
        )

        clock.currentTimeMillis = 1
        val schedules = makeSchedules(RemoteDataSource.APP, 4u).map {
            it.copyWith(metadata =  jsonMapOf(InAppRemoteData.REMOTE_INFO_METADATA_KEY to remoteDataInfo).toJsonValue())
        }

        var updateJob = Job()
        var expectedSchedules = schedules
        coEvery { engine.upsertSchedules(any()) } answers {
            assertEquals(expectedSchedules, firstArg())
            updateJob.complete()
        }

        val remoteData = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    data = InAppRemoteData.Data(schedules, listOf()),
                    timestamp = clock.currentTimeMillis(),
                    remoteDataInfo = remoteDataInfo
                )
            )
        )

        updatesFlow.tryEmit(remoteData)

        updateJob.join()

        coEvery { engine.getSchedules() } returns schedules

        val updatedRemoteDataInfo = RemoteDataInfo(
            url = "https://some.other.url",
            lastModified = null,
            source = RemoteDataSource.APP
        )

        val updatedSchedules = schedules.map {
            it.also { it.copyWith(metadata =  jsonMapOf(InAppRemoteData.REMOTE_INFO_METADATA_KEY to updatedRemoteDataInfo).toJsonValue()) }
        }

        updateJob = Job()
        expectedSchedules = updatedSchedules

        updatesFlow.tryEmit(
            InAppRemoteData(
                payload = mapOf(
                    RemoteDataSource.APP to InAppRemoteData.Payload(
                        data = InAppRemoteData.Data(updatedSchedules, emptyList()),
                        timestamp = clock.currentTimeMillis(),
                        remoteDataInfo = updatedRemoteDataInfo
                    )
                )
            )
        )

        updateJob.join()
    }

    @Test
    public fun testPayloadDateChangeAutomations(): TestResult = runTest {
        subscriber.subscribe()

        clock.currentTimeMillis = 1
        val schedules = makeSchedules(RemoteDataSource.APP, 4u)

        var updateJob = Job()

        coEvery { engine.upsertSchedules(any()) } answers {
            assertEquals(schedules, firstArg())
            updateJob.complete()
        }

        val remoteDataInfo = RemoteDataInfo(
            url = "https://some.other.url",
            lastModified = null,
            source = RemoteDataSource.APP
        )

        updatesFlow.tryEmit(
            InAppRemoteData(
                payload = mapOf(
                    RemoteDataSource.APP to InAppRemoteData.Payload(
                        data = InAppRemoteData.Data(schedules, emptyList()),
                        timestamp = clock.currentTimeMillis(),
                        remoteDataInfo = remoteDataInfo
                    )
                )
            )
        )

        updateJob.join()

        coEvery { engine.getSchedules() } returns schedules

        updateJob = Job()

        // update again with different date
        updatesFlow.tryEmit(
            InAppRemoteData(
                payload = mapOf(
                    RemoteDataSource.APP to InAppRemoteData.Payload(
                        data = InAppRemoteData.Data(schedules, emptyList()),
                        timestamp = clock.currentTimeMillis() + 1,
                        remoteDataInfo = remoteDataInfo
                    )
                )
            )
        )

        updateJob.join()
    }

    @Test
    public fun testConstraints(): TestResult = runTest {
        val appConstraints = listOf(
            FrequencyConstraint("foo", 100, 10u),
            FrequencyConstraint("bar", 100, 10u)
        )

        val contactConstraints = listOf(
            FrequencyConstraint("foo", 1, 1u),
            FrequencyConstraint("baz", 1, 1u)
        )

        val data = InAppRemoteData(
            payload = mapOf(
                RemoteDataSource.APP to InAppRemoteData.Payload(
                    data = InAppRemoteData.Data(listOf(), appConstraints),
                    timestamp = clock.currentTimeMillis()
                ),
                RemoteDataSource.CONTACT to InAppRemoteData.Payload(
                    data = InAppRemoteData.Data(listOf(), contactConstraints),
                    timestamp = clock.currentTimeMillis()
                )
            )
        )

        subscriber.subscribe()

        val updateJob = Job()

        coEvery { frequencyLimitManager.setConstraints(any()) } answers {
            assertEquals(appConstraints + contactConstraints, firstArg())
            updateJob.complete()
            Result.success(Unit)
        }

        updatesFlow.tryEmit(data)
        updateJob.join()
    }

    private fun makeSchedules(
        source: RemoteDataSource,
        count: UInt = Random.nextInt(1, 10).toUInt(),
        minSDKVersion: String? = null,
        created: Long = clock.currentTimeMillis()
    ) : List<AutomationSchedule> {
        return (1u until count)
            .map { makeSchedule(source, minSDKVersion, created) }
    }

    private fun makeSchedule(
        source: RemoteDataSource,
        minSDKVersion: String? = null,
        created: Long = clock.currentTimeMillis()
    ) : AutomationSchedule {
        val remoteDataInfo = RemoteDataInfo(
            url = "https://test.url",
            lastModified = null,
            source = source
        )

        return AutomationSchedule(
            identifier = UUID.randomUUID().toString(),
            data = AutomationSchedule.ScheduleData.Actions(JsonValue.wrap("actions")),
            triggers = listOf(AutomationTrigger.activeSession(1u)),
            created = created.toULong(),
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
}
