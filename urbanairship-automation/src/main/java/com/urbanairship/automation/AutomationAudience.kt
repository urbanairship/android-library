package com.urbanairship.automation

import com.urbanairship.audience.AudienceSelector
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import java.util.Objects

public class AutomationAudience(
    internal val audienceSelector: AudienceSelector,
    internal val missBehavior: MissBehavior? = null
) : JsonSerializable {

    public enum class MissBehavior(internal val json: String): JsonSerializable {
        /// Cancel the schedule
        CANCEL("cancel"),
        /// Skip the execution
        SKIP("skip"),
        /// Skip the execution but count towards the limit
        PENALIZE("penalize");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

        internal companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): MissBehavior {
                val content = value.requireString().lowercase()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("invalid miss behavior $content")
            }
        }
    }

    public companion object {
        private const val MISS_BEHAVIOR = "miss_behavior"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): AutomationAudience {
            return AutomationAudience(
                audienceSelector = AudienceSelector.fromJson(value),
                missBehavior = value.optMap().get(MISS_BEHAVIOR)?.let(MissBehavior.Companion::fromJson)
            )
        }
    }

    override fun toJsonValue(): JsonValue = JsonMap
        .newBuilder()
        .putAll(audienceSelector.toJsonValue().optMap())
        .putOpt(MISS_BEHAVIOR, missBehavior)
        .build()
        .toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AutomationAudience

        if (audienceSelector != other.audienceSelector) return false
        return missBehavior == other.missBehavior
    }

    override fun hashCode(): Int {
        return Objects.hash(audienceSelector, missBehavior)
    }
}
