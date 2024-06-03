package com.urbanairship.automation.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonMatcher
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationStoreMigratorTest {

    private val legacyDb = AutomationDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext())
    private val automationStore = AutomationStore.createInMemoryDatabase(ApplicationProvider.getApplicationContext())
    private val migrator = AutomationStoreMigrator(legacyDb, automationStore)

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
    }

    @Test
    public fun testConvertMinSchedule(): TestResult = runTest {
        legacyDb.scheduleDao.insert(makeSimpleLegacySchedule())
        migrator.migrateData()

        val expected = AutomationScheduleData(
            schedule = AutomationSchedule(
                identifier = "some-schedule",
                interval = 0u,
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
                created = 0U
            ),
            scheduleState = AutomationScheduleState.IDLE,
            scheduleStateChangeDate = 0,
            executionCount = 0,
            triggerInfo = TriggeringInfo(
                context = null,
                date = 0
            ),
            triggerSessionId = UUID.randomUUID().toString()
        )

        val migrated = requireNotNull(automationStore.getSchedule("some-schedule"))
        assertEquals(expected, migrated)
    }

    @Test
    public fun testConvertInAppSchedule(): TestResult = runTest {
        val start = DateUtils.createIso8601TimeStamp(3000)
        val end = DateUtils.createIso8601TimeStamp(5000)
        val legacy = FullSchedule(
            ScheduleEntity().apply {
                this.scheduleId = "some-schedule"
                this.group = "some-group"
                this.metadata = jsonMapOf("meta" to "data")
                this.limit = 1
                this.priority = 2
                this.triggeredTime = 100
                this.scheduleStart = DateUtils.parseIso8601(start)
                this.scheduleEnd =  DateUtils.parseIso8601(end)
                this.editGracePeriod = TimeUnit.DAYS.toMillis(10)
                this.interval = 500
                this.scheduleType = "in_app_message"
                this.data = makeInAppMessageData()
                this.count = 3
                this.executionState = ScheduleState.EXECUTING
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
                startDate = DateUtils.parseIso8601(start).toULong(),
                endDate = DateUtils.parseIso8601(end).toULong(),
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
                interval =  500U,
                data = makeScheduleData(),
                campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue(),
                bypassHoldoutGroups = true,
                editGracePeriodDays = 10U,
                metadata = jsonMapOf("meta" to "data").toJsonValue(),
                frequencyConstraintIds = listOf("constraint1", "constraint2"),
                messageType = "cool inapp",
                reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
                productId = "cool-product",
                created = 10000UL
            ),
            scheduleState = AutomationScheduleState.EXECUTING,
            scheduleStateChangeDate = 600,
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
                date = 100
            ),
            triggerSessionId = UUID.randomUUID().toString()
        )

        val migrated = requireNotNull(automationStore.getSchedule("some-schedule"))
        verifySchedule(expected, migrated)
    }

    @Test
    public fun testConvertDeferredSchedule(): TestResult = runTest {
        val start = DateUtils.createIso8601TimeStamp(3000)
        val end = DateUtils.createIso8601TimeStamp(5000)
        val legacy = FullSchedule(
            ScheduleEntity().apply {
                this.scheduleId = "some-schedule"
                this.group = "some-group"
                this.metadata = jsonMapOf("meta" to "data")
                this.limit = 1
                this.priority = 2
                this.triggeredTime = 100
                this.scheduleStart = DateUtils.parseIso8601(start)
                this.scheduleEnd =  DateUtils.parseIso8601(end)
                this.editGracePeriod = TimeUnit.DAYS.toMillis(10)
                this.interval = 500
                this.scheduleType = "deferred"
                this.data = makeDeferredData()
                this.count = 3
                this.executionState = ScheduleState.EXECUTING
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
                startDate = DateUtils.parseIso8601(start).toULong(),
                endDate = DateUtils.parseIso8601(end).toULong(),
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
                interval =  500U,
                data = makeDeferredScheduleData(),
                campaigns = jsonMapOf("campaigns" to "campaigns").toJsonValue(),
                bypassHoldoutGroups = true,
                editGracePeriodDays = 10U,
                metadata = jsonMapOf("meta" to "data").toJsonValue(),
                frequencyConstraintIds = listOf("constraint1", "constraint2"),
                messageType = "cool deferred",
                reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
                productId = "cool-product",
                created = 10000UL
            ),
            scheduleState = AutomationScheduleState.EXECUTING,
            scheduleStateChangeDate = 600,
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
                date = 100
            ),
            triggerSessionId = UUID.randomUUID().toString()
        )

        val migrated = requireNotNull(automationStore.getSchedule("some-schedule"))
        verifySchedule(expected, migrated)
    }

    @Test
    public fun testConvertActionsSchedule(): TestResult = runTest {
        val start = DateUtils.createIso8601TimeStamp(3000)
        val end = DateUtils.createIso8601TimeStamp(5000)
        val legacy = FullSchedule(
            ScheduleEntity().apply {
                this.scheduleId = "some-schedule"
                this.group = "some-group"
                this.metadata = jsonMapOf("meta" to "data")
                this.limit = 1
                this.priority = 2
                this.triggeredTime = 100
                this.scheduleStart = DateUtils.parseIso8601(start)
                this.scheduleEnd =  DateUtils.parseIso8601(end)
                this.editGracePeriod = TimeUnit.DAYS.toMillis(10)
                this.interval = 500
                this.scheduleType = "actions"
                this.data = jsonMapOf("action" to "value").toJsonValue()
                this.count = 3
                this.executionState = ScheduleState.EXECUTING
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
                startDate = DateUtils.parseIso8601(start).toULong(),
                endDate = DateUtils.parseIso8601(end).toULong(),
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
                interval =  500U,
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
                created = 10000UL
            ),
            scheduleState = AutomationScheduleState.EXECUTING,
            scheduleStateChangeDate = 600,
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
                date = 100
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
                interval = 0u,
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
                created = 0U
            ),
            scheduleState = AutomationScheduleState.IDLE,
            scheduleStateChangeDate = 0,
            executionCount = 0,
            triggerInfo = TriggeringInfo(
                context = null,
                date = 0
            ),
            triggerSessionId = UUID.randomUUID().toString()
        )

        val migrated = requireNotNull(automationStore.getSchedule("some-schedule"))
        assert(automationStore.getSchedule("bad-schedule") == null)
        assertEquals(expected, migrated)
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
