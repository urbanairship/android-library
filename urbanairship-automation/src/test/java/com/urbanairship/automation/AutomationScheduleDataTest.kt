package com.urbanairship.automation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.automation.engine.AutomationScheduleData
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.TriggeringInfo
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.json.JsonValue
import com.urbanairship.util.minus
import com.urbanairship.util.plus
import java.time.Instant
import java.util.UUID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
public class AutomationScheduleDataTest {

    private val clock = TestClock()

    private val triggeringInfo = TriggeringInfo(
        context = null,
        date = clock.currentTime
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
        assertTrue(makeData().isActive(clock.currentTime))

        // startDates in the future
        var data = makeData(startDate = (clock.currentTime + 1.milliseconds))
        assertFalse(data.isActive(clock.currentTime))

        // startDates now
        val current = clock.currentTime
        data = makeData(startDate = current)
        assertTrue(data.isActive(current))

        // ends in the past
        data.updateEndDate((clock.currentTime - 1.milliseconds))
        assertFalse(data.isActive(clock.currentTime))

        // ends now
        data.updateEndDate(clock.currentTime)
        assertFalse(data.isActive(clock.currentTime))

        // ends in the future
        data.updateEndDate((clock.currentTime + 1.milliseconds))
        assertTrue(data.isActive(clock.currentTime))
    }

    @Test
    public fun testIsExpired() {
        val data = makeData()
        // no end set
        assertFalse(data.isExpired(clock.currentTime))

        // ends in the past
        data.updateEndDate((clock.currentTime - 1.milliseconds))
        assertTrue(data.isExpired(clock.currentTime))

        // ends now
        data.updateEndDate((clock.currentTime))
        assertTrue(data.isExpired(clock.currentTime))

        // ends in the future
        data.updateEndDate((clock.currentTime + 1.milliseconds))
        assertFalse(data.isExpired(clock.currentTime))
    }

    @Test
    public fun testFinished() {
        val data = makeData(
            triggeringInfo = triggeringInfo,
            preparedScheduleInfo = preparedScheduleInfo
        )

        assertNotNull(data.triggerInfo)
        assertNotNull(data.preparedScheduleInfo)

        data.finished(clock.currentTime + 100.milliseconds)

        assertNull(data.preparedScheduleInfo)
        assertNull(data.triggerInfo)
        assertEquals(AutomationScheduleState.FINISHED, data.scheduleState)
        assertEquals(clock.currentTime + 100.milliseconds, data.scheduleStateChangeDate)
    }

    @Test
    public fun testIdle() {
        val data = makeData(
            scheduleState = AutomationScheduleState.FINISHED,
            triggeringInfo = triggeringInfo,
            preparedScheduleInfo = preparedScheduleInfo
        )

        data.idle(clock.currentTime + 100.milliseconds)

        assertNull(data.preparedScheduleInfo)
        assertNull(data.triggerInfo)
        assertEquals(AutomationScheduleState.IDLE, data.scheduleState)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testPaused() {
        val data = makeData(
            triggeringInfo = triggeringInfo,
            preparedScheduleInfo = preparedScheduleInfo
        )

        data.paused(clock.currentTime + 100.milliseconds)

        assertNull(data.preparedScheduleInfo)
        assertNull(data.triggerInfo)
        assertEquals(data.scheduleState, AutomationScheduleState.PAUSED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testUpdateStateFinishesOverLimit() {
        val data = makeData()

        data.updateState(clock.currentTime + 100.milliseconds, isOverLimit = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testUpdateStateExpired() {
        val data = makeData()
        data.updateEndDate(clock.currentTime)

        data.updateState(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testUpdateFinishedToIdle() {
        val data = makeData(scheduleState = AutomationScheduleState.FINISHED)

        data.updateState(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testUpdateStateFinished() {
        val data = makeData()

        data.updateState(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime)
    }

    @Test
    public fun testPrepareCancelledPenalize() {
        val  data = makeData(limit = 2U, scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepareCancelled(clock.currentTime + 100.milliseconds, penalize = true, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 1)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testPrepareCancelled() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepareCancelled(clock.currentTime + 100.milliseconds, penalize = false, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 0)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testPrepareCancelledOverLimit() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepareCancelled(clock.currentTime + 100.milliseconds, penalize = true, isOverLimit = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 1)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testPrepareCancelledExpired() {
        val data = makeData(limit = 2U, scheduleState = AutomationScheduleState.TRIGGERED)
        data.updateEndDate(clock.currentTime)

        data.prepareCancelled(clock.currentTime + 100.milliseconds, penalize = true, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 1)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testPrepareInterrupted() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED)

        data.prepareInterrupted(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.TRIGGERED)
        assertEquals(data.executionCount, 0)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testTriggeredScheduleInterrupted() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepareInterrupted(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.TRIGGERED)
        assertEquals(data.executionCount, 0)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime)
    }

    @Test
    public fun testPrepareInterruptedOverLimit() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepareInterrupted(clock.currentTime + 100.milliseconds, isOverLimit = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testPrepareInterruptedExpired() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)
        data.updateEndDate(clock.currentTime)

        data.prepareInterrupted(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 0)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionCancelled() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED)

        data.executionCancelled(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 0)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionCancelledTriggered() {
        val data = makeData(
            scheduleState = AutomationScheduleState.TRIGGERED,
            triggeringInfo = triggeringInfo
        )

        data.executionCancelled(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 0)
        assertNull(data.triggerInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionCancelledIgnoresOtherStates() {
        val states = listOf(
            AutomationScheduleState.IDLE,
            AutomationScheduleState.EXECUTING,
            AutomationScheduleState.PAUSED,
            AutomationScheduleState.FINISHED
        )

        for (state in states) {
            val data = makeData(scheduleState = state)

            data.executionCancelled(clock.currentTime + 100.milliseconds, isOverLimit = false)
            assertEquals(data.scheduleState, state)
        }
    }

    @Test
    public fun testExecutionCancelledOverLimit() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED)

        data.executionCancelled(clock.currentTime + 100.milliseconds, isOverLimit = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionCancelledExpired() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED, endDate = clock.currentTime)

        data.executionCancelled(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testPrepared() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepared(info = preparedScheduleInfo, clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.PREPARED)
        assertEquals(data.preparedScheduleInfo, preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testPreparedOverLimit() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED)

        data.prepared(info = preparedScheduleInfo, clock.currentTime + 100.milliseconds, isOverLimit = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testPreparedExpired() {
        val data = makeData(scheduleState = AutomationScheduleState.TRIGGERED, endDate = clock.currentTime)

        data.prepared(info = preparedScheduleInfo, clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionSkipped() {
        val data = makeData(limit = 2U, scheduleState = AutomationScheduleState.PREPARED)
        data.setExecutionCount(1)

        data.executionSkipped(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.executionCount, 1)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionSkippedOverLimit() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED)

        data.executionSkipped(clock.currentTime + 100.milliseconds, isOverLimit = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionSkippedExpired() {
        val data = makeData(endDate = clock.currentTime, scheduleState = AutomationScheduleState.PREPARED)

        data.executionSkipped(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionInvalidated() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED)

        data.executionInvalidated(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.TRIGGERED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionInvalidatedOverLimit() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED)

        data.executionInvalidated(clock.currentTime + 100.milliseconds, isOverLimit = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionInvalidatedExpired() {
        val data = makeData(endDate = clock.currentTime, scheduleState = AutomationScheduleState.PREPARED)

        data.executionInvalidated(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecuting() {
        val data = makeData(scheduleState = AutomationScheduleState.PREPARED)

        data.executing(clock.currentTime + 100.milliseconds)
        assertEquals(data.scheduleState, AutomationScheduleState.EXECUTING)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionInterrupted() {
        val data = makeData(limit = 3U, scheduleState = AutomationScheduleState.EXECUTING)
        data.setExecutionCount(1)

        data.executionInterrupted(clock.currentTime + 100.milliseconds, retry = false, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 2)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionInterruptedRetry() {
        val data = makeData(limit = 3U,
            interval = 10.seconds,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo,
            endDate = clock.currentTime)
        data.setExecutionCount(1)

        data.executionInterrupted(clock.currentTime + 100.milliseconds, retry = true, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 1)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionInterruptedOverLimit() {
        val data = makeData(
            limit = 2U,
            interval = 10.seconds,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo)
        data.setExecutionCount(1)

        data.executionInterrupted(clock.currentTime + 100.milliseconds, retry =  false, isOverLimit = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 2)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionInterruptedExpired() {
        val data = makeData(
            limit = 3U,
            interval = 10.seconds,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo,
            endDate = clock.currentTime
        )
        data.setExecutionCount(1)

        data.executionInterrupted(clock.currentTime + 100.milliseconds, retry =  true, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 1)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testExecutionInterruptedInterval() {
        val data = makeData(
            limit = 3U,
            scheduleState = AutomationScheduleState.EXECUTING,
            interval = 10.seconds,
            preparedScheduleInfo = preparedScheduleInfo
        )
        data.setExecutionCount(1)

        data.executionInterrupted(clock.currentTime + 100.milliseconds, retry =  false, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.PAUSED)
        assertEquals(data.executionCount, 2)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testFinishedExecuting() {
        val data = makeData(
            limit = 3U,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo
        )
        data.setExecutionCount(1)

        data.finishedExecuting(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleState, AutomationScheduleState.IDLE)
        assertEquals(data.executionCount, 2)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testFinishedExecutingOverLimit() {
        val data = makeData(
            limit = 2U,
            interval = 10.seconds,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo
        )
        data.setExecutionCount(1)

        data.finishedExecuting(clock.currentTime + 100.milliseconds, isOverLimit = true)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 2)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testFinishedExecutingExpired() {
        val data = makeData(
            limit = 3U,
            interval = 10.seconds,
            scheduleState = AutomationScheduleState.EXECUTING,
            preparedScheduleInfo = preparedScheduleInfo,
            endDate = clock.currentTime
        )
        data.setExecutionCount(1)

        data.finishedExecuting(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.executionCount, 2)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testFinishedExecutingInterval() {
        val data = makeData(
            limit = 3U,
            scheduleState = AutomationScheduleState.EXECUTING,
            interval = 10.seconds,
            preparedScheduleInfo = preparedScheduleInfo
        )
        data.setExecutionCount(1)

        data.finishedExecuting(clock.currentTime + 100.milliseconds, isOverLimit = false)
        assertEquals(data.scheduleState, AutomationScheduleState.PAUSED)
        assertEquals(data.executionCount, 2)
        assertNull(data.preparedScheduleInfo)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testShouldDelete() {
        var data = makeData()

        assertFalse(data.shouldDelete(clock.currentTime))

        data = makeData(scheduleState = AutomationScheduleState.FINISHED)
        assertTrue(data.shouldDelete(clock.currentTime))

        data = makeData(editGracePeriodDays = 10U, scheduleState = AutomationScheduleState.FINISHED)
        assertFalse(data.shouldDelete(clock.currentTime))
        assertFalse(data.shouldDelete(clock.currentTime + 1000.milliseconds * 10 * 60 * 60 * 24 - 1.milliseconds))
        assertTrue(data.shouldDelete(clock.currentTime + 1000.milliseconds * 10 * 60 * 60 * 24))
    }

    @Test
    public fun testTriggered() {
        val context = DeferredTriggerContext(
            type = "some-type",
            goal = 10.0,
            event = JsonValue.wrap("event"))
        val data = makeData()
        val previousTriggerSessionId = data.triggerSessionId
        val date = clock.currentTime
        data.triggered(TriggeringInfo(context, date), date + 100.milliseconds, isOverLimit = false)

        assertEquals(data.triggerInfo?.context, context)
        assertEquals(data.triggerInfo?.date, clock.currentTime)
        assertEquals(data.scheduleState, AutomationScheduleState.TRIGGERED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
        assertFalse(data.triggerSessionId == previousTriggerSessionId)
    }

    @Test
    public fun testTriggeredOverLimit() {
        val data = makeData()

        val context = DeferredTriggerContext(
            type = "some-type",
            goal = 10.0,
            event = JsonValue.wrap("event"))
        val date = clock.currentTime
        data.triggered(TriggeringInfo(context, date), date + 100.milliseconds, isOverLimit = true)

        assertNull(data.triggerInfo)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    @Test
    public fun testTriggeredExpired() {
        val data = makeData(endDate = clock.currentTime)

        val context = DeferredTriggerContext(
            type = "some-type",
            goal = 10.0,
            event = JsonValue.wrap("event"))
        val date = clock.currentTime
        data.triggered(TriggeringInfo(context, date), date + 100.milliseconds, isOverLimit = false)

        assertNull(data.triggerInfo)
        assertEquals(data.scheduleState, AutomationScheduleState.FINISHED)
        assertEquals(data.scheduleStateChangeDate, clock.currentTime + 100.milliseconds)
    }

    private fun makeData(
        identifier: String = "neat",
        triggers: List<AutomationTrigger> = listOf(),
        group: String? = null,
        priority: Int? = null,
        limit: UInt? = null,
        startDate: Instant? = null,
        endDate: Instant? = null,
        audience: AutomationAudience? = null,
        compoundAudience: AutomationCompoundAudience? = null,
        delay: AutomationDelay? = null,
        interval: Duration? = null,
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
        created: Instant = clock.currentTime,
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
            scheduleStateChangeDate = clock.now(),
            executionCount = 0,
            triggerInfo = triggeringInfo,
            preparedScheduleInfo = preparedScheduleInfo,
            triggerSessionId = UUID.randomUUID().toString()
        )
    }

    private fun AutomationScheduleData.updateEndDate(endDate: Instant?) {
        setSchedule(schedule.copyWith(endDate = endDate))
    }
}
