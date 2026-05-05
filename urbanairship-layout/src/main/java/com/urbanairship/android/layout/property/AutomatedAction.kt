package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.info.Identifiable
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.requireField

internal data class AutomatedAction(
    override val identifier: String,
    val delay: Int = 0,
    val outcomes: List<Outcome>,
    val reportingMetadata: JsonValue? = null
) : Identifiable {
    companion object {
        fun from(json: JsonMap): AutomatedAction = AutomatedAction(
            identifier = json.requireField("identifier"),
            delay = json.optionalField<Int>("delay") ?: 0,
            outcomes = OutcomeResolver.resolve(
                outcomes = json.optionalList("outcomes")?.let(Outcome::fromList),
                behaviors = json.optionalField<JsonList>("behaviors")
                    ?.let { ButtonClickBehaviorType.fromList(it) },
                actions = json.optionalField<JsonMap>("actions")?.map,
            ),
            reportingMetadata = json.optionalField<JsonValue>("reporting_metadata")
        )

        fun fromList(json: JsonList): List<AutomatedAction> =
            json.map { from(it.optMap()) }.sortedBy { it.delay }
    }
}

/**
 * Returns the `AutomatedAction` with the earliest `delay` that has a behavior of:
 *
 * - `PAGER_NEXT`
 * - `PAGER_NEXT_OR_DISMISS`
 * - `PAGER_NEXT_OR_CANCEL`
 * - `PAGER_PREVIOUS`
 * - `DISMISS`
 * - `CANCEL`
 *
 * If no such action exists, returns `null`.
 */
internal val List<AutomatedAction>.earliestNavigationAction: AutomatedAction?
    get() = firstOrNull { it.outcomes.firstOrNull { outcome -> outcome.isNavigationOutcome } != null }

/**
 * Returns true if the automated action has pause or resume behaviors
 */
internal val List<AutomatedAction>.hasPagerPauseOrResumeAction: Boolean
    get() = any {
        it.outcomes.any { outcome ->
            outcome is Outcome.PagerPlayback &&
                    (outcome.command == Outcome.PagerPlayback.Command.PAUSE ||
                        outcome.command == Outcome.PagerPlayback.Command.RESUME)
        }
    }

private val Outcome.isNavigationOutcome: Boolean
    get() = when (this) {
        is Outcome.PagerStepNavigation -> true
        is Outcome.PagerJumpNavigation -> true
        is Outcome.Dismiss -> true
        else -> false
    }
