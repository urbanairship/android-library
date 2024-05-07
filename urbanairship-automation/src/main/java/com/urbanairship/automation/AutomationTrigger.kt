package com.urbanairship.automation

import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.automation.engine.triggerprocessor.MatchResult
import com.urbanairship.automation.engine.triggerprocessor.TriggerData
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.util.UAStringUtil
import java.util.Objects
import java.util.UUID

public enum class EventAutomationTriggerType(internal val value: String) : JsonSerializable {
    /// Foreground
    FOREGROUND("foreground"),

    /// Background
    BACKGROUND("background"),

    /// Screen view
    SCREEN("screen"),

    /// Version update
    VERSION("version"),

    /// App init
    APP_INIT("app_init"),

    // Region enter
    REGION_ENTER("region_enter"),

    /// Region exit
    REGION_EXIT("region_exit"),

    /// Custom event count
    CUSTOM_EVENT_COUNT("custom_event_count"),

    /// Custom event value
    CUSTOM_EVENT_VALUE("custom_event_value"),

    /// Feature flag interaction
    FEATURE_FLAG_INTERACTION("feature_flag_interaction"),

    /// Active session
    ACTIVE_SESSION("active_session");

    internal companion object {

        @Throws(JsonException::class)
        fun from(value: String): EventAutomationTriggerType? {
            return entries.firstOrNull { it.value == value }
        }
    }

    override fun toJsonValue(): JsonValue = JsonValue.wrap(value)
}

public enum class CompoundAutomationTriggerType(internal val value: String) : JsonSerializable {
    OR("or"),
    AND("and"),
    CHAIN("chain");

    internal companion object {

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): CompoundAutomationTriggerType? {
            val content = value.optString()
            return entries.firstOrNull { it.value == content }
        }
    }

    override fun toJsonValue(): JsonValue = JsonValue.wrap(value)
}

public sealed class AutomationTrigger(
    public val id: String,
    public val goal: Double,
    public val type: String,
    internal val shouldBackfill: Boolean
) : JsonSerializable {

    internal open fun backfilledIdentifier(executionType: TriggerExecutionType) {}

    public class Event(internal val trigger: EventAutomationTrigger) :
        AutomationTrigger(trigger.id, trigger.goal, trigger.type.value, trigger.allowBackfill) {

        override fun toJsonValue(): JsonValue = trigger.toJsonValue()

        override fun backfilledIdentifier(executionType: TriggerExecutionType) {
            trigger.backfillIdentifier(executionType)
        }
    }

    public class Compound(internal val trigger: CompoundAutomationTrigger) :
        AutomationTrigger(trigger.id, trigger.goal, trigger.type.value, false) {

        override fun toJsonValue(): JsonValue = trigger.toJsonValue()
    }

    internal fun matchEvent(
        event: AutomationEvent,
        data: TriggerData,
        resetOnTrigger: Boolean): MatchResult? {

        val result = when(this) {
            is Compound -> trigger.matchEvent(event, data)
            is Event -> trigger.matchEvent(event, data)
        }

        if (resetOnTrigger && result?.isTriggered == true) {
            data.resetCounter()
        }

        return result
    }

    internal fun isTriggered(data: TriggerData): Boolean {
        return data.count >= this.goal
    }

    internal fun removeStaleChildData(data: TriggerData) {
        when(this) {
            is Compound -> trigger.removeStaleChildData(data)
            is Event -> {}
        }
    }

    internal companion object {
        private const val KEY_TYPE = "type"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): AutomationTrigger {
            val type = CompoundAutomationTriggerType.fromJson(value.requireMap().require(KEY_TYPE))

            return if (type != null) {
                Compound(CompoundAutomationTrigger.fromJson(value))
            } else {
                Event(EventAutomationTrigger.fromJson(value))
            }
        }

        fun activeSession(count: UInt): AutomationTrigger {
            return Event(
                EventAutomationTrigger(
                type = EventAutomationTriggerType.ACTIVE_SESSION,
                goal = count.toDouble()
                )
            )
        }

        fun foreground(count: UInt): AutomationTrigger {
            return Event(
                EventAutomationTrigger(
                    type = EventAutomationTriggerType.FOREGROUND,
                    goal = count.toDouble()
                )
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AutomationTrigger

        if (id != other.id) return false
        if (goal != other.goal) return false
        if (type != other.type) return false
        return shouldBackfill == other.shouldBackfill
    }

    override fun hashCode(): Int {
        return Objects.hash(id, goal, type, shouldBackfill)
    }
}

public class EventAutomationTrigger internal constructor(
    public val type: EventAutomationTriggerType,
    public var goal: Double,
    public val predicate: JsonPredicate?,
    public var id: String,
    allowBackfill: Boolean = false
) : JsonSerializable {

    internal var allowBackfill: Boolean = allowBackfill

    public constructor(
        type: EventAutomationTriggerType,
        goal: Double,
        predicate: JsonPredicate? = null
    ) : this (
        type = type,
        goal = goal,
        predicate = predicate,
        id = UUID.randomUUID().toString(),
        // Programatically generated triggers should not allow backfilling the ID
        // even though we generated an ID. These triggers are not created from
        // remote-data so we just need to ensure they are unique.
        allowBackfill = false)

    internal companion object {
        private const val KEY_ID = "id"
        private const val KEY_TYPE = "type"
        private const val KEY_GOAL = "goal"
        private const val KEY_PREDICATE = "predicate"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): EventAutomationTrigger {
            val json = value.requireMap()
            val type = EventAutomationTriggerType.from(json.requireField(KEY_TYPE))
                ?: throw JsonException("invalid compound trigger type $json")

            val id: String? = json.optionalField(KEY_ID)

            return EventAutomationTrigger(
                type = type,
                goal = json.requireField(KEY_GOAL),
                predicate = json.get(KEY_PREDICATE)?.let(JsonPredicate::parse),
                id = id ?: UUID.randomUUID().toString(),
                allowBackfill = id == null
            )
        }
    }

    internal fun matchEvent(event: AutomationEvent, data: TriggerData): MatchResult? {
        return when(event) {
            is AutomationEvent.StateChanged -> stateTriggerMatch(event.state, data)

            AutomationEvent.AppInit -> {
                if (this.type != EventAutomationTriggerType.APP_INIT) {
                    return null
                }
                evaluateResults(data, 1.0)
            }

            AutomationEvent.Background -> {
                if (this.type != EventAutomationTriggerType.BACKGROUND) {
                    return null
                }
                evaluateResults(data, 1.0)
            }

            AutomationEvent.Foreground -> {
                if (this.type != EventAutomationTriggerType.FOREGROUND) {
                    return null
                }
                evaluateResults(data, 1.0)
            }

            is AutomationEvent.CoreEvent -> {
                when (event.airshipEvent) {
                    is AirshipEventFeed.Event.CustomEvent -> {
                        customEvenTriggerMatch(
                            event.airshipEvent.data.toJsonValue(),
                            event.airshipEvent.value,
                            data
                        )
                    }
                    is AirshipEventFeed.Event.FeatureFlagInteracted -> {
                        if (this.type != EventAutomationTriggerType.FEATURE_FLAG_INTERACTION) {
                            return null
                        }
                        if (!isPredicatedMatching(event.airshipEvent.data)) {
                            return null
                        }

                        evaluateResults(data, 1.0)
                    }
                    is AirshipEventFeed.Event.RegionEnter -> {
                        if (this.type != EventAutomationTriggerType.REGION_ENTER) {
                            return null
                        }
                        if (!isPredicatedMatching(event.airshipEvent.data)) {
                            return null
                        }

                        evaluateResults(data, 1.0)
                    }
                    is AirshipEventFeed.Event.RegionExit -> {
                        if (this.type != EventAutomationTriggerType.REGION_EXIT) {
                            return null
                        }
                        if (!isPredicatedMatching(event.airshipEvent.data)) {
                            return null
                        }

                        evaluateResults(data, 1.0)
                    }
                    is AirshipEventFeed.Event.ScreenTracked -> {
                        if (this.type != EventAutomationTriggerType.SCREEN) {
                            return null
                        }
                        if (!isPredicatedMatching(JsonValue.wrap(event.airshipEvent.name))) {
                            return null
                        }

                        evaluateResults(data, 1.0)
                    }
                }
            }
        }
    }

    private fun customEvenTriggerMatch(eventData: JsonValue, value: Double?, data: TriggerData): MatchResult? {
        return when(this.type) {
            EventAutomationTriggerType.CUSTOM_EVENT_COUNT -> {
                return if (isPredicatedMatching(eventData)) {
                    evaluateResults(data, 1.0)
                } else {
                    null
                }
            }

            EventAutomationTriggerType.CUSTOM_EVENT_VALUE -> {
                return if (isPredicatedMatching(eventData)) {
                    evaluateResults(data, value ?: 1.0)
                } else {
                    null
                }
            }

            else -> null
        }
    }

    private fun stateTriggerMatch(state: TriggerableState, data: TriggerData): MatchResult? {
        return when(this.type) {
            EventAutomationTriggerType.VERSION -> {
                val updatedVersion = state.versionUpdated ?: return null
                if (updatedVersion == data.lastTriggerableState?.versionUpdated) {
                    return null
                }

                if (!isPredicatedMatching(JsonValue.wrap(updatedVersion))) {
                    return null
                }

                data.lastTriggerableState = state
                evaluateResults(data, 1.0)
            }
            EventAutomationTriggerType.ACTIVE_SESSION -> {
                val session = state.appSessionID ?: return null
                if (session == data.lastTriggerableState?.appSessionID) {
                    return null
                }

                data.lastTriggerableState = state
                evaluateResults(data, 1.0)
            }
            else -> null
        }
    }

    private fun isPredicatedMatching(value: JsonSerializable): Boolean {
        return this.predicate?.apply(value) ?: true
    }

    private fun evaluateResults(data: TriggerData, increment: Double): MatchResult {
        data.incrementCount(increment)
        return MatchResult(this.id, data.count >= this.goal)
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_ID to id,
        KEY_TYPE to type,
        KEY_GOAL to goal,
        KEY_PREDICATE to predicate
    ).toJsonValue()

    internal fun backfillIdentifier(executionType: TriggerExecutionType) {
        if (!allowBackfill) { return }

        val components = mutableListOf(type.value, goal.toString(), executionType.value)
        if (predicate != null) {
            components.add(predicate.toJsonValue().toString(true))
        }

        id = UAStringUtil.sha256(components.joinToString(":"))
            ?: throw RuntimeException("failed to generate sha256 hash")
        allowBackfill = false
    }
}

public class CompoundAutomationTrigger internal constructor(
    public val id: String,
    public val type: CompoundAutomationTriggerType,
    public val goal: Double,
    internal val children: List<Child>
) : JsonSerializable {
    internal data class Child(
        val trigger: AutomationTrigger,
        val isSticky: Boolean?,
        val resetOnIncrement: Boolean?
    ) : JsonSerializable {
        companion object {
            private const val KEY_TRIGGER = "trigger"
            private const val KEY_IS_STICKY = "is_sticky"
            private const val KEY_RESET_ON_INCREMENT = "reset_on_increment"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Child {
                val json = value.requireMap()
                return Child(
                    trigger = AutomationTrigger.fromJson(json.require(KEY_TRIGGER)),
                    isSticky = json.optionalField(KEY_IS_STICKY),
                    resetOnIncrement = json.optionalField(KEY_RESET_ON_INCREMENT)
                )
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_TRIGGER to trigger,
            KEY_IS_STICKY to isSticky,
            KEY_RESET_ON_INCREMENT to resetOnIncrement
        ).toJsonValue()
    }

    internal fun matchEvent(event: AutomationEvent, data: TriggerData): MatchResult? {
        val triggeredChildren = triggeredChildrendCount(data)

        var childResults = matchChildren(event, data)

        //resend state event if children is triggered for chain triggers
        val state = data.lastTriggerableState
        if (this.type == CompoundAutomationTriggerType.CHAIN &&
            state != null && !event.isStateEvent() &&
            triggeredChildren != triggeredChildrendCount(data)) {

            childResults = matchChildren(AutomationEvent.StateChanged(state), data)
        } else if (event is AutomationEvent.StateChanged) {
            data.lastTriggerableState = event.state
        }

        when(this.type) {
            CompoundAutomationTriggerType.AND, CompoundAutomationTriggerType.CHAIN -> {
                val shouldIncrement = childResults.all { it.isTriggered }

                if (shouldIncrement) {
                    children.forEach { child ->
                        if (child.isSticky != true) {
                            data.childDate(child.trigger.id).resetCounter()
                        }
                    }
                    data.incrementCount(1.0)
                }
            }

            CompoundAutomationTriggerType.OR -> {
                val shouldIncrement = childResults.any { it.isTriggered }
                if (shouldIncrement) {
                    children.forEach { child ->
                        val childData = data.childDate(child.trigger.id)

                        // Reset the child if it reached the goal or if we are resetting it
                        // on increment
                        if (childData.count >= child.trigger.goal || child.resetOnIncrement == true) {
                            childData.resetCounter()
                        }
                    }
                    data.incrementCount(1.0)
                }
            }
        }

        return MatchResult(triggerId = this.id, isTriggered = data.count >= goal)
    }

    private fun matchChildren(event: AutomationEvent, data: TriggerData): List<MatchResult> {
        var evaluateRemaining = true

        return children.map { child ->
            val childData = data.childDate(child.trigger.id)

            var matchResult: MatchResult? = null
            if (evaluateRemaining) {
                // Match the child without resetting it on trigger. We will process resets
                // after we get all the child results
                matchResult = child.trigger.matchEvent(event, childData, false)
            }

            val result = matchResult
                ?: MatchResult(
                    triggerId = child.trigger.id,
                    isTriggered = child.trigger.isTriggered(childData))

            if (this.type == CompoundAutomationTriggerType.CHAIN && evaluateRemaining && !result.isTriggered) {
                evaluateRemaining = false
            }

            result
        }
    }

    private fun triggeredChildrendCount(data: TriggerData): Int {
        return children.filter { child ->
            val state = data.children[child.trigger.id] ?: return@filter false
            return@filter child.trigger.isTriggered(state)
        }.size
    }

    internal companion object {
        private const val KEY_ID = "id"
        private const val KEY_TYPE = "type"
        private const val KEY_GOAL = "goal"
        private const val KEY_CHILDREN = "children"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): CompoundAutomationTrigger {
            val json = value.requireMap()

            val type = CompoundAutomationTriggerType.fromJson(json.require(KEY_TYPE))
                ?: throw JsonException("invalid compound trigger type $json")

            return CompoundAutomationTrigger(
                id = json.requireField(KEY_ID),
                type = type,
                goal = json.requireField(KEY_GOAL),
                children = json.require(KEY_CHILDREN).requireList().map(Child.Companion::fromJson)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_ID to id,
        KEY_TYPE to type,
        KEY_GOAL to goal,
        KEY_CHILDREN to children
    ).toJsonValue()

    internal fun removeStaleChildData(data: TriggerData) {
        if (children.isEmpty()) {
            return
        }

        data.resetChildrenData()

        children.forEach {
            val childData = data.childDate(it.trigger.id)
            it.trigger.removeStaleChildData(childData)
        }
    }
}
