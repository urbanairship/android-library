package com.urbanairship.automation.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.automation.AutomationAppState
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.automation.AutomationDelay
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.EventAutomationTrigger
import com.urbanairship.automation.EventAutomationTriggerType
import com.urbanairship.automation.engine.AutomationScheduleData
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.AutomationStore
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.TriggeringInfo
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.automation.limits.LedgerEvent
import com.urbanairship.automation.limits.LedgerExecutionResult
import com.urbanairship.automation.limits.LedgerScope
import com.urbanairship.automation.limits.LedgerStoreInterface
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonMatcher
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
public class AutomationStoreMigratorTest {

    private val legacyDb = AutomationDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext())
    private val automationStore = AutomationStore.createInMemoryDatabase(ApplicationProvider.getApplicationContext())
    private val ledger = TestLedgerStore()
    private val clock = TestClock()
    private val preferenceStore = PreferenceStore.inMemoryStore(ApplicationProvider.getApplicationContext())
    private val migrator = AutomationStoreMigrator(legacyDb, automationStore, ledger, preferenceStore, clock)

    private val predicate = JsonPredicate.newBuilder().addMatcher(
        JsonMatcher.newBuilder()
            .setValueMatcher(
                ValueMatcher.newValueMatcher(JsonValue.wrapOpt("bingo"))
            ).build()
    ).build()

    @After
    public fun after() {
        legacyDb.close()
        automationStore.close()
        preferenceStore.tearDown()
    }

    @Test
    public fun testConvertMinSchedule(): TestResult = runTest {
        legacyDb.scheduleDao.insert(makeSimpleLegacySchedule())
        migrator.migrateData()

        val expected = AutomationScheduleData(
            schedule = AutomationSchedule(
                identifier = "some-schedule",
                interval = Duration.ZERO,
                priority = 0,
                limit = 0u,
                editGracePeriodDays = 0u,
                triggers = listOf(
                    AutomationTrigger.Event(
                        trigger = EventAutomationTrigger(
                            id = AutomationTrigger.generateStableId(
                                EventAutomationTriggerType.APP_INIT.value,
                                100.0,
                                null,
                                TriggerExecutionType.EXECUTION
                            ),
                            type = EventAutomationTriggerType.APP_INIT,
                            goal = 100.0,
                            predicate = null
                        )
                    )
                ),
                delay = AutomationDelay(seconds = 0),
                data =  AutomationSchedule.ScheduleData.Actions(
                    jsonMapOf("action" to "value").toJsonValue()
                ),
                bypassHoldoutGroups = false,
                created = Instant.ofEpochMilli(0)
            ),
            scheduleState = AutomationScheduleState.IDLE,
            scheduleStateChangeDate = Instant.ofEpochMilli(0),
            executionCount = 0,
            triggerInfo = TriggeringInfo(
                context = null,
                date = Instant.ofEpochMilli(0)
            ),
            triggerSessionId = UUID.randomUUID().toString()
        )

        val migrated = requireNotNull(automationStore.getSchedule("some-schedule"))
        assertEquals(expected, migrated)
    }

    @Test
    public fun testConvertInAppSchedule(): TestResult = runTest {
        val start = DateUtils.createIso8601TimeStamp(Instant.ofEpochMilli(3000))
        val end = DateUtils.createIso8601TimeStamp(Instant.ofEpochMilli(5000))
        val legacy = FullSchedule(
            ScheduleEntity().apply {
                this.scheduleId = "some-schedule"
                this.group = "some-group"
                this.metadata = jsonMapOf("meta" to "data")
                this.limit = 1
                this.priority = 2
                this.triggeredTime = 100
                this.scheduleStart = DateUtils.parseIso8601(start).toEpochMilli()
                this.scheduleEnd =  DateUtils.parseIso8601(end).toEpochMilli()
                this.editGracePeriod = TimeUnit.DAYS.toMillis(10)
                this.interval = TimeUnit.SECONDS.toMillis(500)
                this.scheduleType = "in_app_message"
                this.data = makeInAppMessageData()
                this.count = 3
                this.executionState = ScheduleState.EXECUTING.value
                this.executionStateChangeDate = 600
                this.appState = 3 // background
                this.screens = listOf("foo", "bar")
                this.seconds = 20
                this.regionId = "middle-earth"
                this.audience = jsonMapOf("new_user" to true).toString()
                this.campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue()
                this.reportingContext = jsonMapOf("reporting" to "context").toJsonValue()
                this.frequencyConstraintIds = listOf("constraint1", "constraint2")
                this.messageType = "cool inapp"
                this.bypassHoldoutGroups = true
                this.newUserEvaluationDate = 10000
                this.productId = "cool-product"
            },
            listOf(
                TriggerEntity().apply {
                    this.goal = 100.0
                    this.parentScheduleId = "some-schedule"
                    this.isCancellation = false
                    this.progress = 40.0
                    this.jsonPredicate = predicate
                    this.triggerType = 8 // app init
                },
                TriggerEntity().apply {
                    this.goal = 99.0
                    this.parentScheduleId = "some-schedule"
                    this.isCancellation = true
                    this.progress = 36.0
                    this.triggerType = 7 // screen view
                }
            )
        )

        legacyDb.scheduleDao.insert(legacy)
        migrator.migrateData()

        val expected = AutomationScheduleData(
            schedule = AutomationSchedule(
                identifier = "some-schedule",
                triggers = listOf(
                    AutomationTrigger.Event(
                        trigger = EventAutomationTrigger(
                            id = AutomationTrigger.generateStableId(
                                EventAutomationTriggerType.APP_INIT.value,
                                100.0,
                                predicate,
                                TriggerExecutionType.EXECUTION
                            ),
                            type = EventAutomationTriggerType.APP_INIT,
                            goal = 100.0,
                            predicate = predicate
                        )
                    )
                ),
                group = "some-group",
                priority = 2,
                limit = 1U,
                startDate = DateUtils.parseIso8601(start),
                endDate = DateUtils.parseIso8601(end),
                audience = AutomationAudience(
                    audienceSelector = AudienceSelector.newBuilder().setNewUser(true).build()
                ),
                delay = AutomationDelay(
                    seconds = 20,
                    screens = listOf("foo", "bar"),
                    appState = AutomationAppState.BACKGROUND,
                    regionId  = "middle-earth",
                    cancellationTriggers = listOf(
                        AutomationTrigger.Event(
                            trigger = EventAutomationTrigger(
                                id = AutomationTrigger.generateStableId(
                                    EventAutomationTriggerType.SCREEN.value,
                                    99.0,
                                    null,
                                    TriggerExecutionType.DELAY_CANCELLATION
                                ),
                                type = EventAutomationTriggerType.SCREEN,
                                goal = 99.0,
                                predicate = null
                            )
                        )
                    )

                ),
                interval =  500.seconds,
                data = makeScheduleData(),
                campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue(),
                bypassHoldoutGroups = true,
                editGracePeriodDays = 10U,
                metadata = jsonMapOf("meta" to "data").toJsonValue(),
                frequencyConstraintIds = listOf("constraint1", "constraint2"),
                messageType = "cool inapp",
                reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
                productId = "cool-product",
                created = Instant.ofEpochMilli(10000)
            ),
            scheduleState = AutomationScheduleState.EXECUTING,
            scheduleStateChangeDate = Instant.ofEpochMilli(600),
            executionCount = 3,
            preparedScheduleInfo = PreparedScheduleInfo(
                scheduleId = "some-schedule",
                productId = "cool-product",
                campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue(),
                contactId = null,
                experimentResult = null,
                reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
                triggerSessionId = UUID.randomUUID().toString()
            ),
            triggerInfo = TriggeringInfo(
                context = null,
                date = Instant.ofEpochMilli(100)
            ),
            triggerSessionId = UUID.randomUUID().toString()
        )

        val migrated = requireNotNull(automationStore.getSchedule("some-schedule"))
        verifySchedule(expected, migrated)
    }

    @Test
    public fun testConvertDeferredSchedule(): TestResult = runTest {
        val start = DateUtils.createIso8601TimeStamp(Instant.ofEpochMilli(3000))
        val end = DateUtils.createIso8601TimeStamp(Instant.ofEpochMilli(5000))
        val legacy = FullSchedule(
            ScheduleEntity().apply {
                this.scheduleId = "some-schedule"
                this.group = "some-group"
                this.metadata = jsonMapOf("meta" to "data")
                this.limit = 1
                this.priority = 2
                this.triggeredTime = 100
                this.scheduleStart = DateUtils.parseIso8601(start).toEpochMilli()
                this.scheduleEnd =  DateUtils.parseIso8601(end).toEpochMilli()
                this.editGracePeriod = TimeUnit.DAYS.toMillis(10)
                this.interval = TimeUnit.SECONDS.toMillis(500)
                this.scheduleType = "deferred"
                this.data = makeDeferredData()
                this.count = 3
                this.executionState = ScheduleState.EXECUTING.value
                this.executionStateChangeDate = 600
                this.appState = 3 // background
                this.screens = listOf("foo", "bar")
                this.seconds = 20
                this.regionId = "middle-earth"
                this.audience = jsonMapOf("new_user" to true).toString()
                this.campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue()
                this.reportingContext = jsonMapOf("reporting" to "context").toJsonValue()
                this.frequencyConstraintIds = listOf("constraint1", "constraint2")
                this.messageType = "cool deferred"
                this.bypassHoldoutGroups = true
                this.newUserEvaluationDate = 10000
                this.productId = "cool-product"
            },
            listOf(
                TriggerEntity().apply {
                    this.goal = 100.0
                    this.parentScheduleId = "some-schedule"
                    this.isCancellation = false
                    this.progress = 40.0
                    this.jsonPredicate = predicate
                    this.triggerType = 8 // app init
                },
                TriggerEntity().apply {
                    this.goal = 99.0
                    this.parentScheduleId = "some-schedule"
                    this.isCancellation = true
                    this.progress = 36.0
                    this.triggerType = 7 // screen view
                }
            )
        )

        legacyDb.scheduleDao.insert(legacy)
        migrator.migrateData()

        val expected = AutomationScheduleData(
            schedule = AutomationSchedule(
                identifier = "some-schedule",
                triggers = listOf(
                    AutomationTrigger.Event(
                        trigger = EventAutomationTrigger(
                            id = AutomationTrigger.generateStableId(
                                EventAutomationTriggerType.APP_INIT.value,
                                100.0,
                                predicate,
                                TriggerExecutionType.EXECUTION
                            ),
                            type = EventAutomationTriggerType.APP_INIT,
                            goal = 100.0,
                            predicate = predicate
                        )
                    )
                ),
                group = "some-group",
                priority = 2,
                limit = 1U,
                startDate = DateUtils.parseIso8601(start),
                endDate = DateUtils.parseIso8601(end),
                audience = AutomationAudience(
                    audienceSelector = AudienceSelector.newBuilder().setNewUser(true).build()
                ),
                delay = AutomationDelay(
                    seconds = 20,
                    screens = listOf("foo", "bar"),
                    appState = AutomationAppState.BACKGROUND,
                    regionId  = "middle-earth",
                    cancellationTriggers = listOf(
                        AutomationTrigger.Event(
                            trigger = EventAutomationTrigger(
                                id = AutomationTrigger.generateStableId(
                                    EventAutomationTriggerType.SCREEN.value,
                                    99.0,
                                    null,
                                    TriggerExecutionType.DELAY_CANCELLATION
                                ),
                                type = EventAutomationTriggerType.SCREEN,
                                goal = 99.0,
                                predicate = null
                            )
                        )
                    )

                ),
                interval =  500.seconds,
                data = makeDeferredScheduleData(),
                campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue(),
                bypassHoldoutGroups = true,
                editGracePeriodDays = 10U,
                metadata = jsonMapOf("meta" to "data").toJsonValue(),
                frequencyConstraintIds = listOf("constraint1", "constraint2"),
                messageType = "cool deferred",
                reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
                productId = "cool-product",
                created = Instant.ofEpochMilli(10000)
            ),
            scheduleState = AutomationScheduleState.EXECUTING,
            scheduleStateChangeDate = Instant.ofEpochMilli(600),
            executionCount = 3,
            preparedScheduleInfo = PreparedScheduleInfo(
                scheduleId = "some-schedule",
                productId = "cool-product",
                campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue(),
                contactId = null,
                experimentResult = null,
                reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
                triggerSessionId = UUID.randomUUID().toString()
            ),
            triggerInfo = TriggeringInfo(
                context = null,
                date = Instant.ofEpochMilli(100)
            ),
            triggerSessionId = UUID.randomUUID().toString()
        )

        val migrated = requireNotNull(automationStore.getSchedule("some-schedule"))
        verifySchedule(expected, migrated)
    }

    @Test
    public fun testConvertActionsSchedule(): TestResult = runTest {
        val start = DateUtils.createIso8601TimeStamp(Instant.ofEpochMilli(3000))
        val end = DateUtils.createIso8601TimeStamp(Instant.ofEpochMilli(5000))
        val legacy = FullSchedule(
            ScheduleEntity().apply {
                this.scheduleId = "some-schedule"
                this.group = "some-group"
                this.metadata = jsonMapOf("meta" to "data")
                this.limit = 1
                this.priority = 2
                this.triggeredTime = 100
                this.scheduleStart = DateUtils.parseIso8601(start).toEpochMilli()
                this.scheduleEnd =  DateUtils.parseIso8601(end).toEpochMilli()
                this.editGracePeriod = TimeUnit.DAYS.toMillis(10)
                this.interval = TimeUnit.SECONDS.toMillis(500)
                this.scheduleType = "actions"
                this.data = jsonMapOf("action" to "value").toJsonValue()
                this.count = 3
                this.executionState = ScheduleState.EXECUTING.value
                this.executionStateChangeDate = 600
                this.appState = 3 // background
                this.screens = listOf("foo", "bar")
                this.seconds = 20
                this.regionId = "middle-earth"
                this.audience = jsonMapOf("new_user" to true).toString()
                this.campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue()
                this.reportingContext = jsonMapOf("reporting" to "context").toJsonValue()
                this.frequencyConstraintIds = listOf("constraint1", "constraint2")
                this.messageType = "cool actions"
                this.bypassHoldoutGroups = true
                this.newUserEvaluationDate = 10000
                this.productId = "cool-product"
            },
            listOf(
                TriggerEntity().apply {
                    this.goal = 100.0
                    this.parentScheduleId = "some-schedule"
                    this.isCancellation = false
                    this.progress = 40.0
                    this.jsonPredicate = predicate
                    this.triggerType = 8 // app init
                },
                TriggerEntity().apply {
                    this.goal = 99.0
                    this.parentScheduleId = "some-schedule"
                    this.isCancellation = true
                    this.progress = 36.0
                    this.triggerType = 7 // screen view
                }
            )
        )

        legacyDb.scheduleDao.insert(legacy)
        migrator.migrateData()

        val expected = AutomationScheduleData(
            schedule = AutomationSchedule(
                identifier = "some-schedule",
                triggers = listOf(
                    AutomationTrigger.Event(
                        trigger = EventAutomationTrigger(
                            id = AutomationTrigger.generateStableId(
                                EventAutomationTriggerType.APP_INIT.value,
                                100.0,
                                predicate,
                                TriggerExecutionType.EXECUTION
                            ),
                            type = EventAutomationTriggerType.APP_INIT,
                            goal = 100.0,
                            predicate = predicate
                        )
                    )
                ),
                group = "some-group",
                priority = 2,
                limit = 1U,
                startDate = DateUtils.parseIso8601(start),
                endDate = DateUtils.parseIso8601(end),
                audience = AutomationAudience(
                    audienceSelector = AudienceSelector.newBuilder().setNewUser(true).build()
                ),
                delay = AutomationDelay(
                    seconds = 20,
                    screens = listOf("foo", "bar"),
                    appState = AutomationAppState.BACKGROUND,
                    regionId  = "middle-earth",
                    cancellationTriggers = listOf(
                        AutomationTrigger.Event(
                            trigger = EventAutomationTrigger(
                                id = AutomationTrigger.generateStableId(
                                    EventAutomationTriggerType.SCREEN.value,
                                    99.0,
                                    null,
                                    TriggerExecutionType.DELAY_CANCELLATION
                                ),
                                type = EventAutomationTriggerType.SCREEN,
                                goal = 99.0,
                                predicate = null
                            )
                        )
                    )

                ),
                interval =  500.seconds,
                data =  AutomationSchedule.ScheduleData.Actions(
                    jsonMapOf("action" to "value").toJsonValue()
                ),
                campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue(),
                bypassHoldoutGroups = true,
                editGracePeriodDays = 10U,
                metadata = jsonMapOf("meta" to "data").toJsonValue(),
                frequencyConstraintIds = listOf("constraint1", "constraint2"),
                messageType =  "cool actions",
                reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
                productId = "cool-product",
                created = Instant.ofEpochMilli(10000)
            ),
            scheduleState = AutomationScheduleState.EXECUTING,
            scheduleStateChangeDate = Instant.ofEpochMilli(600),
            executionCount = 3,
            preparedScheduleInfo = PreparedScheduleInfo(
                scheduleId = "some-schedule",
                productId = "cool-product",
                campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue(),
                contactId = null,
                experimentResult = null,
                reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
                triggerSessionId = UUID.randomUUID().toString()
            ),
            triggerInfo = TriggeringInfo(
                context = null,
                date = Instant.ofEpochMilli(100)
            ),
            triggerSessionId = UUID.randomUUID().toString()
        )

        val migrated = requireNotNull(automationStore.getSchedule("some-schedule"))
        verifySchedule(expected, migrated)
    }

    @Test
    public fun testFailingMigration(): TestResult = runTest {
        legacyDb.scheduleDao.insert(makeBadLegacySchedule())
        legacyDb.scheduleDao.insert(makeSimpleLegacySchedule())
        migrator.migrateData()

        val expected = AutomationScheduleData(
            schedule = AutomationSchedule(
                identifier = "some-schedule",
                interval = Duration.ZERO,
                priority = 0,
                limit = 0u,
                editGracePeriodDays = 0u,
                triggers = listOf(
                    AutomationTrigger.Event(
                        trigger = EventAutomationTrigger(
                            id = AutomationTrigger.generateStableId(
                                EventAutomationTriggerType.APP_INIT.value,
                                100.0,
                                null,
                                TriggerExecutionType.EXECUTION
                            ),
                            type = EventAutomationTriggerType.APP_INIT,
                            goal = 100.0,
                            predicate = null
                        )
                    )
                ),
                delay = AutomationDelay(seconds = 0),
                data =  AutomationSchedule.ScheduleData.Actions(
                    jsonMapOf("action" to "value").toJsonValue()
                ),
                bypassHoldoutGroups = false,
                created = Instant.ofEpochMilli(0)
            ),
            scheduleState = AutomationScheduleState.IDLE,
            scheduleStateChangeDate = Instant.ofEpochMilli(0),
            executionCount = 0,
            triggerInfo = TriggeringInfo(
                context = null,
                date = Instant.ofEpochMilli(0)
            ),
            triggerSessionId = UUID.randomUUID().toString()
        )

        val migrated = requireNotNull(automationStore.getSchedule("some-schedule"))
        assert(automationStore.getSchedule("bad-schedule") == null)
        assertEquals(expected, migrated)
    }

    @Test
    public fun testBackfillLedgerEvents() {
        val timestamp = Instant.ofEpochMilli(1000)
        val schedules = listOf(
            scheduleData("a", 3),
            scheduleData("b", 0),
            scheduleData("c", 1)
        )

        val events = AutomationStoreMigrator.backfillLedgerEvents(schedules, timestamp)

        // Only schedules with a non-zero legacy count are backfilled, each as a
        // single execution/backfill event with no trigger or shared scope.
        val expected = listOf(
            LedgerEvent.Execution(
                scheduleId = "a",
                sharedId = null,
                triggerId = null,
                timestamp = timestamp,
                count = 3,
                result = LedgerExecutionResult.BACKFILL,
                cancel = null
            ),
            LedgerEvent.Execution(
                scheduleId = "c",
                sharedId = null,
                triggerId = null,
                timestamp = timestamp,
                count = 1,
                result = LedgerExecutionResult.BACKFILL,
                cancel = null
            )
        )

        assertEquals(expected, events)
    }

    @Test
    public fun testBackfillLedgerEventsSkipsZeroCounts() {
        val events = AutomationStoreMigrator.backfillLedgerEvents(
            listOf(scheduleData("a", 0)),
            timestamp = Instant.ofEpochMilli(1)
        )
        assertTrue(events.isEmpty())
    }

    @Test
    public fun testBackfillLedgerEventsEmptyInput() {
        val events = AutomationStoreMigrator.backfillLedgerEvents(emptyList(), timestamp = Instant.ofEpochMilli(1))
        assertTrue(events.isEmpty())
    }

    @Test
    public fun testMigrationBackfillsLegacyExecutionCounts(): TestResult = runTest {
        clock.currentTime = Instant.ofEpochMilli(5000)
        legacyDb.scheduleDao.insert(makeLegacySchedule("legacy-1", count = 5))

        migrator.migrateData()

        val migrated = requireNotNull(automationStore.getSchedule("legacy-1"))
        assertEquals(5, migrated.executionCount)

        assertEquals(
            listOf(
                LedgerEvent.Execution(
                    scheduleId = "legacy-1",
                    sharedId = null,
                    triggerId = null,
                    timestamp = Instant.ofEpochMilli(5000),
                    count = 5,
                    result = LedgerExecutionResult.BACKFILL,
                    cancel = null
                )
            ),
            ledger.recorded
        )
    }

    @Test
    public fun testMigrationDoesNotBackfillZeroCounts(): TestResult = runTest {
        legacyDb.scheduleDao.insert(makeLegacySchedule("legacy-1", count = 0))

        migrator.migrateData()

        // The schedule still migrates, but a zero count produces no backfill.
        assertEquals(0, requireNotNull(automationStore.getSchedule("legacy-1")).executionCount)
        assertTrue(ledger.recorded.isEmpty())
    }

    @Test
    public fun testMigrationBackfillNotDuplicatedOnRerun(): TestResult = runTest {
        legacyDb.scheduleDao.insert(makeLegacySchedule("legacy-1", count = 5))
        migrator.migrateData()
        assertEquals(1, ledger.recorded.size)

        // Simulate a relaunch where the post-migration legacy delete had failed:
        // the legacy row is present again, but the new store already holds the
        // migrated schedule. Migration must move it without re-recording backfill.
        legacyDb.scheduleDao.insert(makeLegacySchedule("legacy-1", count = 5))
        migrator.migrateData()

        assertEquals(1, ledger.recorded.size)
    }

    @Test
    public fun testCurrentStoreBackfillRecordsExistingCounts(): TestResult = runTest {
        clock.currentTime = Instant.ofEpochMilli(7000)

        // Schedules already in the current store from a pre-ledger SDK version,
        // with no legacy store to migrate.
        automationStore.upsertSchedules(listOf("current-1", "current-2", "current-3")) { id, _ ->
            when (id) {
                "current-1" -> scheduleData(id, 2)
                "current-2" -> scheduleData(id, 0)
                else -> scheduleData(id, 4)
            }
        }

        migrator.migrateData()

        // Only non-zero counts are backfilled. Store ordering isn't guaranteed,
        // so compare sorted by schedule id.
        assertEquals(
            listOf(
                LedgerEvent.Execution(
                    scheduleId = "current-1",
                    sharedId = null,
                    triggerId = null,
                    timestamp = Instant.ofEpochMilli(7000),
                    count = 2,
                    result = LedgerExecutionResult.BACKFILL,
                    cancel = null
                ),
                LedgerEvent.Execution(
                    scheduleId = "current-3",
                    sharedId = null,
                    triggerId = null,
                    timestamp = Instant.ofEpochMilli(7000),
                    count = 4,
                    result = LedgerExecutionResult.BACKFILL,
                    cancel = null
                )
            ),
            ledger.recorded.sortedBy { it.scheduleId }
        )
    }

    @Test
    public fun testBackfillRetriedWhenRecordFails(): TestResult = runTest {
        legacyDb.scheduleDao.insert(makeLegacySchedule("legacy-1", count = 5))
        ledger.failNextRecord = true

        migrator.migrateData()

        // The record failed, so nothing was backfilled and the run must not be
        // marked complete — otherwise the counts are lost for good.
        assertTrue(ledger.recorded.isEmpty())

        // The schedule still migrated, so the next launch retries off the
        // current store and recovers the count exactly once.
        migrator.migrateData()

        assertEquals(
            listOf(
                LedgerEvent.Execution(
                    scheduleId = "legacy-1",
                    sharedId = null,
                    triggerId = null,
                    timestamp = clock.currentTime,
                    count = 5,
                    result = LedgerExecutionResult.BACKFILL,
                    cancel = null
                )
            ),
            ledger.recorded
        )

        // And a third launch does not double-count.
        migrator.migrateData()
        assertEquals(1, ledger.recorded.size)
    }

    /**
     * [PreferenceStore] degrades a failed write to a no-op, so the completion
     * flag can silently fail to persist. The next launch must still not record
     * the counts a second time.
     */
    @Test
    public fun testBackfillDoesNotDoubleRecordWhenFlagIsLost(): TestResult = runTest {
        automationStore.upsertSchedules(listOf("current-1")) { id, _ -> scheduleData(id, 2) }

        migrator.migrateData()
        assertEquals(1, ledger.recorded.size)

        // As if the flag write had been swallowed on the previous launch.
        preferenceStore.remove(AutomationStoreMigrator.LEDGER_BACKFILL_COMPLETED_KEY)

        migrator.migrateData()

        assertEquals(1, ledger.recorded.size)
    }

    @Test
    public fun testCurrentStoreBackfillRunsOnce(): TestResult = runTest {
        automationStore.upsertSchedules(listOf("current-1")) { id, _ -> scheduleData(id, 2) }

        migrator.migrateData()
        assertEquals(1, ledger.recorded.size)

        // A later launch — where the current store's counts now also include
        // ledger-recorded executions — must not backfill a second time.
        ledger.recorded.clear()
        migrator.migrateData()
        assertTrue(ledger.recorded.isEmpty())
    }

    private fun scheduleData(id: String, count: Int): AutomationScheduleData {
        return AutomationScheduleData(
            schedule = AutomationSchedule(
                identifier = id,
                triggers = emptyList(),
                data = AutomationSchedule.ScheduleData.Actions(
                    jsonMapOf("action" to "value").toJsonValue()
                )
            ),
            scheduleState = AutomationScheduleState.IDLE,
            scheduleStateChangeDate = Instant.ofEpochMilli(0),
            executionCount = count,
            triggerInfo = null,
            triggerSessionId = UUID.randomUUID().toString()
        )
    }

    private fun makeLegacySchedule(id: String, count: Int): FullSchedule {
        return FullSchedule(
            ScheduleEntity().apply {
                this.scheduleId = id
                this.scheduleType = "actions"
                this.data = jsonMapOf("action" to "value").toJsonValue()
                this.scheduleStart = -1
                this.scheduleEnd = -1
                this.count = count
            },
            listOf(
                TriggerEntity().apply {
                    this.goal = 100.0
                    this.parentScheduleId = id
                    this.isCancellation = false
                    this.progress = 40.0
                    this.triggerType = 8 // app init
                }
            )
        )
    }

    private class TestLedgerStore : LedgerStoreInterface {
        val recorded: MutableList<LedgerEvent> = mutableListOf()

        /** Makes the next [recordEvents] throw, then clears itself. */
        var failNextRecord: Boolean = false

        override suspend fun recordEvents(events: List<LedgerEvent>) {
            if (failNextRecord) {
                failNextRecord = false
                throw IllegalStateException("ledger unavailable")
            }
            recorded.addAll(events)
        }

        override suspend fun events(scheduleId: String, sharedId: String?): List<LedgerEvent> =
            emptyList()

        override suspend fun hasEvents(scheduleId: String): Boolean =
            recorded.any { it.scheduleId == scheduleId }

        override suspend fun deleteEvents(scopes: List<LedgerScope>) {}
    }

    private fun makeSimpleLegacySchedule(): FullSchedule {
        return FullSchedule(
            ScheduleEntity().apply {
                this.scheduleId = "some-schedule"
                this.scheduleType = "actions"
                this.data = jsonMapOf("action" to "value").toJsonValue()
                this.scheduleStart = -1
                this.scheduleEnd = -1
            },
            listOf(
                TriggerEntity().apply {
                    this.goal = 100.0
                    this.parentScheduleId = "some-schedule"
                    this.isCancellation = false
                    this.progress = 40.0
                    this.triggerType = 8 // app init
                }
            )
        )
    }

    private fun makeBadLegacySchedule(): FullSchedule {
        return FullSchedule(
            ScheduleEntity().apply {
                this.scheduleId = "bad-schedule"
                this.scheduleType = "mytype"
                this.data = jsonMapOf("anything" to "value").toJsonValue()
                this.scheduleStart = -1
                this.scheduleEnd = -1
            },
            listOf(
                TriggerEntity().apply {
                    this.goal = 100.0
                    this.parentScheduleId = "bad-schedule"
                    this.isCancellation = false
                    this.progress = 40.0
                    this.triggerType = 8 // app init
                }
            )
        )
    }

    private fun makeInAppMessageData(): JsonValue {
        return JsonValue.parseString(
            "{\"display_type\":\"fullscreen\",\"reporting_enabled\":true,\"extra\":{},\"display\":{\"template\":\"header_media_body\",\"buttons\":[{\"background_color\":\"#ff000000\",\"label\":{\"style\":[\"bold\"],\"text\":\"bouton\",\"font_family\":[\"sans-serif\"],\"color\":\"#ffffffff\",\"size\":12},\"id\":\"bouton\",\"border_color\":\"#ff000000\",\"behavior\":\"dismiss\",\"actions\":{},\"border_radius\":2}],\"background_color\":\"#ffffffff\",\"dismiss_button_color\":\"#ff000000\",\"heading\":{\"color\":\"#ff000000\",\"size\":14,\"style\":[],\"text\":\"header\",\"font_family\":[\"sans-serif\"],\"alignment\":\"left\"},\"footer\":{\"label\":{\"color\":\"#ffffa600\",\"size\":18,\"style\":[\"bold\",\"italic\",\"underline\"],\"text\":\"footer\",\"font_family\":[\"Gothem SSm\",\"gothem_family\",\"sans-serif\"],\"alignment\":\"left\"},\"id\":\"footer\",\"behavior\":\"dismiss\",\"actions\":{\"open_external_url_action\":\"https://google.fr\"},\"border_radius\":0},\"media\":{\"description\":\"Image\",\"type\":\"image\",\"url\":\"https://hangar-dl.urbanairship.com/binary/public/ISex_TTJRuarzs9-o_Gkhg/8d02a9db-309e-4e11-85e2-b24429e9bcd0\"},\"body\":{\"color\":\"#ff000000\",\"size\":14,\"style\":[],\"text\":\"body\",\"font_family\":[\"sans-serif\"],\"alignment\":\"left\"},\"button_layout\":\"stacked\"},\"name\":\"test ct\",\"source\":\"remote-data\",\"actions\":{},\"display_behavior\":\"default\"}")
    }

    private fun makeDeferredData(): JsonValue {
        return JsonValue.parseString("{\"retry_on_timeout\":true,\"type\":\"in_app_message\",\"url\":\"https://remote-data.urbanairship.com/api/remote-data/deferred/ISex_TTJRuarzs9-o_Gkhg/android/1beb08e2-6bd0-49d1-99b9-7d5d3ea0656a?language=fr&country=FR&sdk_version=17.8.0&ts=1716234393840\"}")
    }

    private fun makeScheduleData(): AutomationSchedule.ScheduleData {
        return AutomationSchedule.ScheduleData.fromJson(
            JsonMap.newBuilder().putAll(makeInAppMessageData().map!!)
                .put("message", makeInAppMessageData())
                .put("type", "in_app_message")
                .build().toJsonValue())
    }

    private fun makeDeferredScheduleData(): AutomationSchedule.ScheduleData {
        return AutomationSchedule.ScheduleData.fromJson(
            JsonMap.newBuilder().putAll(makeDeferredData().map!!)
                .put("deferred", makeDeferredData())
                .put("type", "deferred")
                .build().toJsonValue())
    }

    /**
     * Verify the migration went well. Just ignore verifying triggerSessionId as it's random.
     */
    private fun verifySchedule(expected: AutomationScheduleData, migrated: AutomationScheduleData) {
        assertEquals(expected.schedule, migrated.schedule)
        assertEquals(expected.scheduleState, migrated.scheduleState)
        assertEquals(expected.scheduleStateChangeDate, migrated.scheduleStateChangeDate)
        assertEquals(expected.executionCount, migrated.executionCount)
        assertEquals(expected.triggerInfo, migrated.triggerInfo)
        assertEquals(expected.preparedScheduleInfo?.scheduleId, migrated.preparedScheduleInfo?.scheduleId)
        assertEquals(expected.preparedScheduleInfo?.productId, migrated.preparedScheduleInfo?.productId)
        assertEquals(expected.preparedScheduleInfo?.campaigns, migrated.preparedScheduleInfo?.campaigns)
        assertEquals(expected.preparedScheduleInfo?.contactId, migrated.preparedScheduleInfo?.contactId)
        assertEquals(expected.preparedScheduleInfo?.experimentResult, migrated.preparedScheduleInfo?.experimentResult)
        assertEquals(expected.preparedScheduleInfo?.reportingContext, migrated.preparedScheduleInfo?.reportingContext)
        assertEquals(expected.preparedScheduleInfo?.additionalAudienceCheckResult, migrated.preparedScheduleInfo?.additionalAudienceCheckResult)
    }
}
