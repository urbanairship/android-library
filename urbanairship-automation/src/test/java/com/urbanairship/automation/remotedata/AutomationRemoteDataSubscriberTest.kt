package com.urbanairship.automation.remotedata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestClock
import com.urbanairship.automation.AutomationEngineInterface
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.limits.FrequencyConstraint
import com.urbanairship.automation.limits.FrequencyLimitManager
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource
import java.util.UUID
import kotlin.random.Random
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationRemoteDataSubscriberTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val clock = TestClock().apply { currentTimeMillis = 1000 }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = UnconfinedTestDispatcher()

    private var updatesFlow = MutableSharedFlow<InAppRemoteData>()
    private val remoteDataAccess: AutomationRemoteDataAccessInterface = mockk {
        every { this@mockk.updatesFlow } returns this@AutomationRemoteDataSubscriberTest.updatesFlow
        every { this@mockk.sourceFor(any()) } answers { getSource(firstArg()) }
    }

    private val engine: AutomationEngineInterface = mockk {
        coEvery { this@mockk.getSchedules() } returns emptyList()
    }

    private val frequencyLimitManager: FrequencyLimitManager = mockk {
        coEvery { this@mockk.setConstraints(any()) } returns Result.success(Unit)
    }
    private var subscriber: AutomationRemoteDataSubscriber = AutomationRemoteDataSubscriber(
        dataStore, remoteDataAccess, engine, frequencyLimitManager, "1.11", testDispatcher
    )

    @Test
    public fun testSchedulingAutomations(): TestResult = runTest {
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

        coEvery {
            engine.upsertSchedules(any())
        } just runs

        subscriber.subscribe()
        updatesFlow.emit(data)

        coVerify {
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

        coEvery { engine.stopSchedules(any()) } just runs

        subscriber.subscribe()
        updatesFlow.emit(emptyData)

        coVerify { engine.stopSchedules(eq(scheduleIDs)) }
    }

    @Test
    public fun testIgnoreSchedulesNoLongerScheduled(): TestResult = runTest {
        coEvery { engine.upsertSchedules(any()) } just runs

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

        updatesFlow.emit(firstUpdate)

        coVerify { engine.upsertSchedules(firstUpdateSchedules) }
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

        updatesFlow.emit(secondUpdate)
        // Should still be the first update schedules since the second updates are older
        coVerify { engine.upsertSchedules(firstUpdateSchedules) }

    }

    @Test
    public fun testOlderSchedulesMinSDKVersion(): TestResult = runTest {
        coEvery { engine.upsertSchedules(any()) } just runs

        subscriber = AutomationRemoteDataSubscriber(
            dataStore, remoteDataAccess, engine, frequencyLimitManager, "1.0.0", testDispatcher
        )
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

        updatesFlow.emit(firstUpdate)
        coVerify { engine.upsertSchedules(firstUpdateSchedules) }
        subscriber.unsubscribe()

        subscriber = AutomationRemoteDataSubscriber(dataStore, remoteDataAccess, engine, frequencyLimitManager, "2.0.0", testDispatcher)

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


        coEvery { engine.getSchedules() } returns firstUpdateSchedules

        subscriber.subscribe()
        updatesFlow.emit(secondUpdate)

        coVerify { engine.upsertSchedules(secondUpdateSchedules) }
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

        updatesFlow.emit(update)
        updatesFlow.emit(update)

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

        updatesFlow.emit(remoteData)

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

        updatesFlow.emit(
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

        updatesFlow.emit(
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
        updatesFlow.emit(
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

        updatesFlow.emit(data)
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
