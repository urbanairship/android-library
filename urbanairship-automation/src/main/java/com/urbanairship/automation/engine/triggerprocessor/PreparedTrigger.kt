/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine.triggerprocessor

import com.urbanairship.automation.engine.AutomationEvent
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.engine.TriggeringInfo
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.json.JsonValue
import com.urbanairship.util.Clock

internal class PreparedTrigger(
    internal val scheduleId: String,
    internal val executionType: TriggerExecutionType,

    triggerData: TriggerData,
    trigger: AutomationTrigger,
    isActive: Boolean = false,
    startDate: ULong?,
    endDate: ULong?,
    priority: Int,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {

    internal var triggerData = triggerData
        private set
    internal var trigger = trigger
        private set
    internal var isActive = isActive
        private set
    internal var startDate = startDate
        private set
    internal var endDate = endDate
        private set
    internal var priority = priority
        private set

    data class EventProcessResult(
        val triggerData: TriggerData,
        val triggerResult: TriggerResult?,
        val priority: Int
    )

    init {
        trigger.removeStaleChildData(triggerData)
    }

    internal fun process(event: AutomationEvent) : EventProcessResult? {
        if (!isActive || !isWithinDateRange()) {
            return null
        }

        val currentData = triggerData.copy()
        val match = this.trigger.matchEvent(event, currentData, true)

        if (currentData != triggerData || match?.isTriggered == true) {
            triggerData = currentData

            return EventProcessResult(
                triggerData = currentData,
                triggerResult =
                if (match?.isTriggered == true) {
                    if (event.reportPayload() != null) {
                        generateTriggerResult(event.reportPayload()!!)
                    } else {
                        generateTriggerResult(JsonValue.NULL)
                    }
                } else {
                    null
                },
                priority = priority
            )
        } else {
            return null
        }
    }

    internal fun update(
        trigger: AutomationTrigger,
        startDate: ULong?,
        endDate: ULong?,
        priority: Int
    ) {
        this.trigger = trigger
        this.startDate = startDate
        this.endDate = endDate
        this.priority = priority
        trigger.removeStaleChildData(triggerData)
    }

    internal fun activate() {
        if (isActive) { return }

        this.isActive = true

        if (executionType == TriggerExecutionType.DELAY_CANCELLATION) {
            triggerData.resetCounter()
        }
    }

    internal fun disable() {
        this.isActive = false
    }

    private fun generateTriggerResult(event: JsonValue) : TriggerResult {
        return TriggerResult(
            scheduleId = scheduleId,
            triggerExecutionType = executionType,
            triggerInfo = TriggeringInfo(
                context = DeferredTriggerContext(trigger.type, trigger.goal, event),
                date = clock.currentTimeMillis())
            )
    }

    private fun isWithinDateRange() : Boolean {
        val now = clock.currentTimeMillis().toULong()
        if (startDate?.let { it > now } == true) {
            return false
        }
        if (endDate?.let { it < now } == true) {
            return false
        }

        return true
    }
}

internal data class MatchResult(
    val triggerId: String,
    val isTriggered: Boolean
)
