package com.urbanairship.automation.rewrite

import com.urbanairship.automation.rewrite.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
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
            return Event(EventAutomationTrigger(
                type = EventAutomationTriggerType.ACTIVE_SESSION,
                goal = count.toDouble()
                ))
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
    public val goal: Double,
    public val predicate: JsonPredicate?,
    id: String,
    allowBackfill: Boolean = false
) : JsonSerializable {

    internal var id: String = id
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
                children = json.require(KEY_CHILDREN).requireList().map(Child::fromJson)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_ID to id,
        KEY_TYPE to type,
        KEY_GOAL to goal,
        KEY_CHILDREN to children
    ).toJsonValue()
}
