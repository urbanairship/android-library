package com.urbanairship.automation.remotedata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
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
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val clock = TestClock().apply { currentTimeMillis = 1000 }

    private val testDispatcher = StandardTestDispatcher()
    private val unconfinedDispatcher = UnconfinedTestDispatcher()

    private var updatesFlow = MutableSharedFlow<InAppRemoteData>(extraBufferCapacity = Int.MAX_VALUE)
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
        dataStore, remoteDataAccess, engine, frequencyLimitManager, "1.11", unconfinedDispatcher
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
        advanceUntilIdle()

        updatesFlow.emit(data)
        advanceUntilIdle()

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

        coJustRun { engine.stopSchedules(any()) }

        subscriber.subscribe()
        advanceUntilIdle()

        updatesFlow.emit(emptyData)
        advanceUntilIdle()

        coVerify { engine.stopSchedules(eq(scheduleIDs)) }
    }

    @Test
    public fun testIgnoreSchedulesNoLongerScheduled(): TestResult = runTest {
        coEvery { engine.upsertSchedules(any()) } just runs

        subscriber.subscribe()
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        // Should still be the first update schedules since the second updates are older
        coVerify { engine.upsertSchedules(firstUpdateSchedules) }

    }

    @Test
    public fun testOlderSchedulesMinSDKVersion(): TestResult = runTest {
        coEvery { engine.upsertSchedules(any()) } just runs

        subscriber = AutomationRemoteDataSubscriber(
            dataStore, remoteDataAccess, engine, frequencyLimitManager, "1.0.0", unconfinedDispatcher
        )
        subscriber.subscribe()
        advanceUntilIdle()

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
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(firstUpdateSchedules) }
        subscriber.unsubscribe()
        advanceUntilIdle()

        subscriber = AutomationRemoteDataSubscriber(dataStore, remoteDataAccess, engine, frequencyLimitManager, "2.0.0", unconfinedDispatcher)

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
        advanceUntilIdle()

        updatesFlow.emit(secondUpdate)
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(secondUpdateSchedules) }
    }

    @Test
    public fun testSamePayloadSkipsAutomations(): TestResult = runTest {
        subscriber.subscribe()
        advanceUntilIdle()

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

        val remoteDataInfo = RemoteDataInfo(
            url = "https://some.url",
            lastModified = null,
            source = RemoteDataSource.APP
        )

        clock.currentTimeMillis = 1
        val schedules = makeSchedules(RemoteDataSource.APP, 4u).map {
            it.copyWith(metadata =  jsonMapOf(InAppRemoteData.REMOTE_INFO_METADATA_KEY to remoteDataInfo).toJsonValue())
        }

        var expectedSchedules = schedules
        coJustRun { engine.upsertSchedules(expectedSchedules) }

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
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(expectedSchedules) }
    }

    @Test
    public fun testPayloadDateChangeAutomations(): TestResult = runTest {
        subscriber.subscribe()

        clock.currentTimeMillis = 1
        val schedules = makeSchedules(RemoteDataSource.APP, 4u)

        coJustRun { engine.upsertSchedules(schedules) }

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
        advanceUntilIdle()

        coVerify { engine.upsertSchedules(schedules) }

        advanceUntilIdle()

        coEvery { engine.getSchedules() } returns schedules

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
                    timestamp = clock.currentTimeMillis()
                ),
                RemoteDataSource.CONTACT to InAppRemoteData.Payload(
                    data = InAppRemoteData.Data(listOf(), contactConstraints),
                    timestamp = clock.currentTimeMillis()
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
