/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine.triggerprocessor

import com.urbanairship.automation.engine.TriggerableState
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import java.util.Objects
import kotlin.jvm.Throws

internal class TriggerData(
    internal val scheduleId: String,
    internal val triggerId: String,
    private var triggerCount: Double = .0,
    children: Map<String, TriggerData> = emptyMap(),
    internal var lastTriggerableState: TriggerableState? = null
) : JsonSerializable {

    private val mutableChildren: MutableMap<String, TriggerData> = children.toMutableMap()
    val children: Map<String, TriggerData> get() { return mutableChildren.toMap() }
    val count: Double get() { return triggerCount }

    internal fun incrementCount(value: Double) {
        triggerCount += value
    }

    internal fun resetCounter() {
        triggerCount = .0
    }

    internal fun resetChildrenData() {
        mutableChildren.clear()
    }

    internal fun childDate(triggerID: String): TriggerData {
        return mutableChildren.getOrPut(triggerID) { TriggerData(scheduleId, triggerID, 0.0) }
    }

    internal fun copy(): TriggerData {
        return TriggerData(
            scheduleId = scheduleId,
            triggerId = triggerId,
            triggerCount = triggerCount,
            children = mutableChildren.mapValues { it.value.copy() }.toMap(),
            lastTriggerableState = lastTriggerableState
        )
    }

    internal companion object {
        private const val SCHEDULE_ID = "scheduleID"
        private const val TRIGGER_ID = "triggerID"
        private const val COUNT = "count"
        private const val CHILDREN = "children"
        private const val LAST_TRIGGERABLE_STATE = "lastTriggerableState"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): TriggerData {
            val content = value.requireMap()

            val children = content
                .require(CHILDREN)
                .requireMap()
                .map
                .mapValues { fromJson(it.value) }

            return TriggerData(
                scheduleId = content.requireField(SCHEDULE_ID),
                triggerId = content.requireField(TRIGGER_ID),
                triggerCount = content.requireField(COUNT),
                children = children,
                lastTriggerableState = content.get(LAST_TRIGGERABLE_STATE)?.let(TriggerableState::fromJson)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        SCHEDULE_ID to scheduleId,
        TRIGGER_ID to triggerId,
        COUNT to count,
        CHILDREN to children,
        LAST_TRIGGERABLE_STATE to lastTriggerableState
    ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TriggerData

        if (scheduleId != other.scheduleId) return false
        if (triggerId != other.triggerId) return false
        if (lastTriggerableState != other.lastTriggerableState) return false
        if (count != other.count) return false
        if (mutableChildren != other.mutableChildren) return false
        return children == other.children
    }

    override fun hashCode(): Int {
        return Objects.hash(scheduleId, triggerId, lastTriggerableState, count, children)
    }
}
