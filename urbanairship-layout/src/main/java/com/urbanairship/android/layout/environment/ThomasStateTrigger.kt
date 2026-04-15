package com.urbanairship.android.layout.environment

import com.urbanairship.android.layout.property.Outcome
import com.urbanairship.android.layout.property.OutcomeParams
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.optionalList
import com.urbanairship.json.requireField

internal data class ThomasStateTrigger(
    val id: String,
    val triggerWhenStateMatches: JsonPredicate,
    val resetWhenStateMatches: JsonPredicate? = null,
    val onTrigger: TriggerActions
) {

    companion object {

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): ThomasStateTrigger {
            return ThomasStateTrigger(
                id = json.requireField("identifier"),
                triggerWhenStateMatches = JsonPredicate.parse(json.requireField("trigger_when_state_matches")),
                resetWhenStateMatches = JsonPredicate.parse(json["reset_when_state_matches"]),
                onTrigger = TriggerActions.fromJson(json.requireField("on_trigger"))
            )
        }
    }
}

internal data class TriggerActions(
    val stateActions: List<StateAction>?,
    val outcomes: List<Outcome>? = null
) {
    val outcomeParams: OutcomeParams
        get() = OutcomeParams(outcomes = outcomes, stateActions = stateActions)

    companion object {
        fun fromJson(json: JsonMap): TriggerActions {
            return TriggerActions(
                stateActions = json.optionalList("state_actions")?.map(StateAction::fromJson),
                outcomes = json.optionalList("outcomes")?.let(Outcome::fromList)
            )
        }
    }
}
