/* Copyright Airship and Contributors */
package com.urbanairship.automation

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField

/**
 * Schedule-level AI suppression config (`ai_suppression`).
 *
 * Evaluated at prepare time: the SDK asks the model whether [condition] holds for the current
 * user and suppresses the schedule when it decides the condition does not. It lives on the
 * schedule so it applies to any automation type, and each type builds its own usage and subject
 * — in-app messages for now.
 *
 * @param condition Describes when the message should be shown, e.g. "user has not purchased in
 * the last 30 days". Injected into the SDK's instruction template.
 * @param subjectHints Extra data handed to the app's context provider alongside the subject.
 * Never rendered into the prompt — the app decides whether to use it.
 * @param missBehavior What to do when the model suppresses. Defaults to
 * [AutomationAudience.MissBehavior.SKIP] — eligible again on the next trigger.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class AutomationAiSuppression internal constructor(
    internal val condition: String,
    internal val subjectHints: Map<String, String>? = null,
    internal val missBehavior: AutomationAudience.MissBehavior? = null
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        CONDITION to condition,
        SUBJECT_HINTS to subjectHints?.let { hints ->
            JsonMap(hints.mapValues { JsonValue.wrap(it.value) })
        },
        MISS_BEHAVIOR to missBehavior
    ).toJsonValue()

    internal companion object {
        private const val CONDITION = "condition"
        private const val SUBJECT_HINTS = "subject_hints"
        private const val MISS_BEHAVIOR = "miss_behavior"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): AutomationAiSuppression {
            val content = value.requireMap()

            return AutomationAiSuppression(
                condition = content.requireField(CONDITION),
                // A non-string hint drops that hint rather than the schedule: `requireString`
                // would throw past `AutomationSchedule.fromJson`, where a parse failure costs
                // the whole automation. Hints are the softest part of this payload, and the
                // rest of the feature fails open.
                subjectHints = content.optionalMap(SUBJECT_HINTS)
                    ?.map
                    ?.mapNotNull { (key, value) -> value.string?.let { key to it } }
                    ?.toMap(),
                missBehavior = content[MISS_BEHAVIOR]
                    ?.let(AutomationAudience.MissBehavior.Companion::fromJson)
            )
        }
    }
}
