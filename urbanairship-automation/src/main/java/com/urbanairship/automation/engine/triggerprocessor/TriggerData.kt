package com.urbanairship.automation.engine.triggerprocessor

import androidx.annotation.RestrictTo
import com.urbanairship.automation.TriggerableState
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import java.util.Objects
import kotlin.jvm.Throws

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TriggerData(
    public val scheduleID: String,
    public val triggerID: String,
    private var triggerCount: Double = .0,
    children: Map<String, TriggerData> = emptyMap(),
    public var lastTriggerableState: TriggerableState? = null
) : JsonSerializable {

    private val mutableChildren: MutableMap<String, TriggerData> = children.toMutableMap()
    public val children: Map<String, TriggerData> get() { return mutableChildren.toMap() }
    public val count: Double get() { return triggerCount }

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
        return mutableChildren.getOrPut(triggerID) { TriggerData(scheduleID, triggerID, 0.0) }
    }

    internal fun copy(): TriggerData {
        return TriggerData(
            scheduleID = scheduleID,
            triggerID = triggerID,
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
                scheduleID = content.requireField(SCHEDULE_ID),
                triggerID = content.requireField(TRIGGER_ID),
                triggerCount = content.requireField(COUNT),
                children = children,
                lastTriggerableState = content.get(LAST_TRIGGERABLE_STATE)?.let(TriggerableState::fromJson)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        SCHEDULE_ID to scheduleID,
        TRIGGER_ID to triggerID,
        COUNT to count,
        CHILDREN to children,
        LAST_TRIGGERABLE_STATE to lastTriggerableState
    ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TriggerData

        if (scheduleID != other.scheduleID) return false
        if (triggerID != other.triggerID) return false
        if (lastTriggerableState != other.lastTriggerableState) return false
        if (count != other.count) return false
        if (mutableChildren != other.mutableChildren) return false
        return children == other.children
    }

    override fun hashCode(): Int {
        return Objects.hash(scheduleID, triggerID, lastTriggerableState, count, children)
    }
}
