package com.urbanairship.automation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.automation.engine.AutomationScheduleData
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.TriggeringInfo
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.json.JsonValue
import java.util.UUID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationScheduleDataTest {

    private val clock = TestClock()

    private val triggeringInfo = TriggeringInfo(
        context = null,
        date = clock.currentTimeMillis
    )

    private val preparedScheduleInfo = PreparedScheduleInfo(
        scheduleId = UUID.randomUUID().toString(),
        triggerSessionId = UUID.randomUUID().toString(),
        additionalAudienceCheckResult = true,
        priority = 0
    )

    @Test
    public fun testIsInState() {
        val data = makeData()
        assertTrue(data.isInState(listOf(AutomationScheduleState.IDLE)))
        assertFalse(data.isInState(listOf()))
        assertFalse(data.isInState(listOf(AutomationScheduleState.EXECUTING)))
        assertFalse(data.isInState(listOf(
            AutomationScheduleState.EXECUTING,
            AutomationScheduleState.FINISHED,
            AutomationScheduleState.PREPARED,
            AutomationScheduleState.PAUSED
        )))
        assertTrue(data.isInState(listOf(
            AutomationScheduleState.IDLE,
            AutomationScheduleState.EXECUTING,
            AutomationScheduleState.FINISHED,
            AutomationScheduleState.PREPARED,
            AutomationScheduleState.PAUSED
        )))
    }

    @Test
    public fun testIsActive() {
        // no startDate or end
        assertTrue(makeData().isActive(clock.currentTimeMillis))

        // startDates in the future
        var data = makeData(startDate = (clock.currentTimeMillis + 1).toULong())
        assertFalse(data.isActive(clock.currentTimeMillis))

        // startDates now
        val current = clock.currentTimeMillis.toULong()
        data = makeData(startDate = current)
        assertTrue(data.isActive(current.toLong()))

        // ends in the past
        data.updateEndDate((clock.currentTimeMillis - 1).toULong())
        assertFalse(data.isActive(clock.currentTimeMillis))

        // ends now
        data.updateEndDate(clock.currentTimeMillis.toULong())
        assertFalse(data.isActive(clock.currentTimeMillis))

        // ends in the future
        data.updateEndDate((clock.currentTimeMillis + 1).toULong())
        assertTrue(data.isActive(clock.currentTimeMillis))
    }

    @Test
    public fun testIsExpired() {
        val data = makeData()
        // no end set
        assertFalse(data.isExpired(clock.currentTimeMillis))

        // ends in the past
        data.updateEndDate((clock.currentTimeMillis - 1).toULong())
        assertTrue(data.isExpired(clock.currentTimeMillis))

        // ends now
        data.updateEndDate((clock.currentTimeMillis).toULong())
        assertTrue(data.isExpired(clock.currentTimeMillis))

        // ends in the future
        data.updateEndDate((clock.currentTimeMillis + 1).toULong())
        assertFalse(data.isExpired(clock.currentTimeMillis))
    }

    @Test
    public fun testOverLimitNotSetDefaultsTo1() {
        val data = makeData()
        data.setExecutionCount(0)
        assertFalse(data.isOverLimit())

        data.setExecutionCount(1)
        assertTrue(data.isOverLimit())
    }

    @Test
    public fun testOverLimitUnlimited() {
        val data = makeData(limit = 0U)

        data.setExecutionCount(0)
        assertFalse(data.isOverLimit())

        data.setExecutionCount(1)
        assertFalse(data.isOverLimit())

        data.setExecutionCount(100)
        assertFalse(data.isOverLimit())
    }

    @Test
    public fun testOverLimit() {
        val data = makeData(limit = 10U)

        data.setExecutionCount(0)
        assertFalse(data.isOverLimit())

        data.setExecutionCount(9)
        assertFalse(data.isOverLimit())

        data.setExecutionCount(10)
        assertTrue(data.isOverLimit())

        data.setExecutionCount(11)
        assertTrue(data.isOverLimit())
    }

    @Test
    public fun testFinished() {
        val data = makeData(
            triggeringInfo = triggeringInfo,
            preparedScheduleInfo = preparedScheduleInfo
        )

        assertNotNull(data.triggerInfo)
        assertNotNull(data.preparedScheduleInfo)

        data.finished(clock.currentTimeMillis + 100)

        assertNull(data.preparedScheduleInfo)
        assertNull(data.triggerInfo)
        assertEquals(AutomationScheduleState.FINISHED, data.scheduleState)
        assertEquals(clock.currentTimeMillis + 100, data.scheduleStateChangeDate)
    }

    @Test
    public fun testIdle() {
        val data = makeData(
            scheduleState = AutomationScheduleState.FINISHED,
            triggeringInfo = triggeringInfo,
            preparedScheduleInfo = preparedScheduleInfo
        )

        data.idle(clock.currentTimeMillis + 100)

        assertNull(data.preparedScheduleInfo)
        assertNull(data.triggerInfo)
        assertEquals(AutomationScheduleState.IDLE, data.scheduleState)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testPaused() {
        val data = makeData(
            triggeringInfo = triggeringInfo,
            preparedScheduleInfo = preparedScheduleInfo
        )

        data.paused(clock.currentTimeMillis + 100)

        assertNull(data.preparedScheduleInfo)
        assertNull(data.triggerInfo)
        assertEquals(data.scheduleState, AutomationScheduleState.PAUSED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testUpdateStateFinishesOverLimit() {
        val data = makeData(limit = 1U)
        data.setExecutionCount(1)

        data.updateState(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testUpdateStateExpired() {
        val data = makeData()
        data.updateEndDate(clock.currentTimeMillis.toULong())

        data.updateState(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testUpdateFinishedToIdle() {
        val data = makeData(scheduleState = AutomationScheduleState.FINISHED)

        data.updateState(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testUpdateStateFinished() {
        val data = makeData()

        data.updateState(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis)
    }

    @Test
    public fun testPrepareCancelledPenalize() {
        val  data = makeData(limit = 2U, scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepareCancelled(clock.currentTimeMillis + 100, penalize = true)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 1)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testPrepareCancelled() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepareCancelled(clock.currentTimeMillis + 100, penalize = false)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 0)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testPrepareCancelledOverLimit() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepareCancelled(clock.currentTimeMillis + 100, penalize = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 1)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testPrepareCancelledExpired() {
        val data = makeData(limit = 2U, scheduleState = AutomationScheduleState.TRIGGERED)
        data.updateEndDate(clock.currentTimeMillis.toULong())

        data.prepareCancelled(clock.currentTimeMillis + 100, penalize = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 1)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testPrepareInterrupted() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED)

        data.prepareInterrupted(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.TRIGGERED)
        assertEquals(data.executionCount, 0)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testTriggeredScheduleInterrupted() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepareInterrupted(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.TRIGGERED)
        assertEquals(data.executionCount, 0)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis)
    }

    @Test
    public fun testPrepareInterruptedOverLimit() {
        val data = makeData(limit = 1U, scheduleState = AutomationScheduleState.TRIGGERED)
        data.setExecutionCount(1)

        data.prepareInterrupted(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testPrepareInterruptedExpired() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)
        data.updateEndDate(clock.currentTimeMillis.toULong())

        data.prepareInterrupted(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 0)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionCancelled() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED)

        data.executionCancelled(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 0)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionCancelledOverLimit() {
        val data = makeData(limit = 1U, scheduleState = AutomationScheduleState.PREPARED)
        data.setExecutionCount(1)

        data.executionCancelled(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionCancelledExpired() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED, endDate = clock.currentTimeMillis.toULong())

        data.executionCancelled(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testPrepared() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepared(info = preparedScheduleInfo, clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.PREPARED)
        assertEquals(data.preparedScheduleInfo, preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testPreparedOverLimit() {
        val data = makeData(limit = 1U, scheduleState = AutomationScheduleState.TRIGGERED)
        data.setExecutionCount(1)

        data.prepared(info = preparedScheduleInfo, clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testPreparedExpired() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED, endDate = clock.currentTimeMillis.toULong())

        data.prepared(info = preparedScheduleInfo, clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionSkipped() {
        val data = makeData(limit = 2U, scheduleState = AutomationScheduleState.PREPARED)
        data.setExecutionCount(1)

        data.executionSkipped(clock.currentTimeMillis + 100)
        assertEquals(data.executionCount, 1)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionSkippedOverLimit() {
        val data = makeData(limit = 1U, scheduleState = AutomationScheduleState.PREPARED)
        data.setExecutionCount(1)

        data.executionSkipped(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionSkippedExpired() {
        val data = makeData(limit = 2U, endDate = clock.currentTimeMillis.toULong(), scheduleState = AutomationScheduleState.PREPARED)
        data.setExecutionCount(1)

        data.executionSkipped(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionInvalidated() {
        val data = makeData(limit = 2U, scheduleState = AutomationScheduleState.PREPARED)
        data.setExecutionCount(1)

        data.executionInvalidated(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.TRIGGERED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionInvalidatedOverLimit() {
        val data = makeData(limit = 1U, scheduleState = AutomationScheduleState.PREPARED)
        data.setExecutionCount(1)

        data.executionInvalidated(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionInvalidatedExpired() {
        val data = makeData(limit = 2U, endDate = clock.currentTimeMillis.toULong(), scheduleState = AutomationScheduleState.PREPARED)
        data.setExecutionCount(1)

        data.executionInvalidated(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecuting() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED)
        data.setExecutionCount(1)

        data.executing(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.EXECUTING)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionInterrupted() {
        val data = makeData(limit = 3U, scheduleState = AutomationScheduleState.EXECUTING)
        data.setExecutionCount(1)

        data.executionInterrupted(clock.currentTimeMillis + 100, retry = false)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 2)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionInterruptedRetry() {
        val data = makeData(limit = 3U,
            interval = 10U,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo,
            endDate = clock.currentTimeMillis.toULong())
        data.setExecutionCount(1)

        data.executionInterrupted(clock.currentTimeMillis + 100, retry = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 1)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionInterruptedOverLimit() {
        val data = makeData(
            limit = 2U,
            interval = 10U,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo)
        data.setExecutionCount(1)

        data.executionInterrupted(clock.currentTimeMillis + 100, retry =  false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 2)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionInterruptedExpired() {
        val data = makeData(
            limit = 3U,
            interval = 10U,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo,
            endDate = clock.currentTimeMillis.toULong()
        )
        data.setExecutionCount(1)

        data.executionInterrupted(clock.currentTimeMillis + 100, retry =  true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 1)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testExecutionInterruptedInterval() {
        val data = makeData(
            limit = 3U,
            scheduleState = AutomationScheduleState.EXECUTING,
            interval = 10U,
            preparedScheduleInfo = preparedScheduleInfo
        )
        data.setExecutionCount(1)

        data.executionInterrupted(clock.currentTimeMillis + 100, retry =  false)
        assertEquals(data.scheduleState, AutomationScheduleState.PAUSED)
        assertEquals(data.executionCount, 2)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testFinishedExecuting() {
        val data = makeData(
            limit = 3U,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo
        )
        data.setExecutionCount(1)

        data.finishedExecuting(clock.currentTimeMillis + 100)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 2)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testFinishedExecutingOverLimit() {
        val data = makeData(
            limit = 2U,
            interval = 10U,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo
        )
        data.setExecutionCount(1)

        data.finishedExecuting(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 2)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testFinishedExecutingExpired() {
        val data = makeData(
            limit = 3U,
            interval = 10U,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo,
            endDate = clock.currentTimeMillis.toULong()
        )
        data.setExecutionCount(1)

        data.finishedExecuting(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 2)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testFinishedExecutingInterval() {
        val data = makeData(
            limit = 3U,
            scheduleState = AutomationScheduleState.EXECUTING,
            interval = 10U,
            preparedScheduleInfo = preparedScheduleInfo
        )
        data.setExecutionCount(1)

        data.finishedExecuting(clock.currentTimeMillis + 100)
        assertEquals(data.scheduleState, AutomationScheduleState.PAUSED)
        assertEquals(data.executionCount, 2)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testShouldDelete() {
        var data = makeData()

        assertFalse(data.shouldDelete(clock.currentTimeMillis))

        data = makeData(scheduleState = AutomationScheduleState.FINISHED)
        assertTrue(data.shouldDelete(clock.currentTimeMillis))

        data = makeData(editGracePeriodDays = 10U, scheduleState = AutomationScheduleState.FINISHED)
        assertFalse(data.shouldDelete(clock.currentTimeMillis))
        assertFalse(data.shouldDelete(clock.currentTimeMillis + 1000 * 10 * 60 * 60 * 24 - 1))
        assertTrue(data.shouldDelete(clock.currentTimeMillis + 1000 * 10 * 60 * 60 * 24))
    }

    @Test
    public fun testTriggered() {
        val context = DeferredTriggerContext(
            type = "some-type",
            goal = 10.0,
            event = JsonValue.wrap("event"))
        val data = makeData()
        val previousTriggerSessionId = data.triggerSessionId
        val date = clock.currentTimeMillis
        data.triggered(TriggeringInfo(context, date), date + 100)

        assertEquals(data.triggerInfo?.context, context)
        assertEquals(data.triggerInfo?.date, clock.currentTimeMillis)
        assertEquals(data.scheduleState, AutomationScheduleState.TRIGGERED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
        assertFalse(data.triggerSessionId == previousTriggerSessionId)
    }

    @Test
    public fun testTriggeredOverLimit() {
        val data = makeData(limit = 1U)
        data.setExecutionCount(1)

        val context = DeferredTriggerContext(
            type = "some-type",
            goal = 10.0,
            event = JsonValue.wrap("event"))
        val date = clock.currentTimeMillis
        data.triggered(TriggeringInfo(context, date), date + 100)

        assertNull(data.triggerInfo)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    @Test
    public fun testTriggeredExpired() {
        val data = makeData(limit = 2U, endDate = clock.currentTimeMillis.toULong())

        val context = DeferredTriggerContext(
            type = "some-type",
            goal = 10.0,
            event = JsonValue.wrap("event"))
        val date = clock.currentTimeMillis
        data.triggered(TriggeringInfo(context, date), date + 100)

        assertNull(data.triggerInfo)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTimeMillis + 100)
    }

    private fun makeData(
        identifier: String = "neat",
        triggers: List<AutomationTrigger> = listOf(),
        group: String? = null,
        priority: Int? = null,
        limit: UInt? = null,
        startDate: ULong? = null,
        endDate: ULong? = null,
        audience: AutomationAudience? = null,
        compoundAudience: AutomationCompoundAudience? = null,
        delay: AutomationDelay? = null,
        interval: ULong? = null,
        data: AutomationSchedule.ScheduleData = AutomationSchedule.ScheduleData.Actions(JsonValue.wrap("actions")),
        bypassHoldoutGroups: Boolean? = null,
        editGracePeriodDays: ULong? = null,
        metadata: JsonValue? = null,
        frequencyConstraintIDs: List<String>? = null,
        messageType: String? = null,
        campaigns: JsonValue? = null,
        reportingContext: JsonValue? = null,
        productId: String? = null,
        minSDKVersion: String? = null,
        created: ULong = clock.currentTimeMillis.toULong(),
        queue: String? = null,
        triggeringInfo: TriggeringInfo? = null,
        preparedScheduleInfo: PreparedScheduleInfo? = null,
        scheduleState: AutomationScheduleState = AutomationScheduleState.IDLE
    ): AutomationScheduleData {
        return AutomationScheduleData(
            schedule = AutomationSchedule(identifier, triggers, group, priority, limit, startDate,
                endDate, audience, compoundAudience, delay, interval, data, bypassHoldoutGroups, editGracePeriodDays,
                metadata, frequencyConstraintIDs, messageType, campaigns, reportingContext,
                productId, minSDKVersion, created, queue),
            scheduleState = scheduleState,
            scheduleStateChangeDate = clock.currentTimeMillis(),
            executionCount = 0,
            triggerInfo = triggeringInfo,
            preparedScheduleInfo = preparedScheduleInfo,
            triggerSessionId = UUID.randomUUID().toString()
        )
    }

    private fun AutomationScheduleData.updateEndDate(endDate: ULong?) {
        setSchedule(schedule.copyWith(endDate = endDate))
    }
}
