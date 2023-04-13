package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.info.Identifiable
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

internal data class AutomatedAction(
    override val identifier: String,
    val delay: Int = 0,
    val actions: Map<String, JsonValue>? = null,
    val behaviors: List<ButtonClickBehaviorType>? = null
) : Identifiable {
    companion object {
        fun from(json: JsonMap): AutomatedAction = AutomatedAction(
            identifier = json.requireField("identifier"),
            delay = json.optionalField<Int>("delay") ?: 0,
            actions = json.optionalField<JsonMap>("actions")?.map,
            behaviors = json.optionalField<JsonList>("behaviors")?.let {
                ButtonClickBehaviorType.fromList(it)
            }
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
    get() = firstOrNull { it.behaviors?.hasStoryNavigationBehavior ?: false }
