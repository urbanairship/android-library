package com.urbanairship.preferencecenter.data

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.data.Condition.NotificationOptIn.Status
import com.urbanairship.preferencecenter.data.Condition.NotificationOptIn.Status.OPT_IN
import com.urbanairship.preferencecenter.data.Condition.NotificationOptIn.Status.OPT_OUT
import com.urbanairship.preferencecenter.util.requireField

typealias Conditions = List<Condition>

/**
 * Evaluates this list of conditions against the given [state].
 *
 * Returns `true` if:
 * - Any condition evaluates to `true`.
 * - The conditions list is empty.
 */
fun Conditions.evaluate(state: Condition.State): Boolean =
    isEmpty() || any { it.evaluate(state) }

/** Base condition, used to determine visibility of preference sections and items. */
sealed class Condition(private val type: String) {

    /** Notification opt-in status condition. */
    data class NotificationOptIn(
        val status: Status
    ) : Condition(TYPE_NOTIFICATION_OPT_IN) {

        enum class Status(val jsonValue: String) {
            OPT_IN("opt_in"),
            OPT_OUT("opt_out");

            companion object {
                internal fun parse(json: String) =
                    valueOf(json.uppercase())
            }
        }

        override fun evaluate(state: State) = when (status) {
            OPT_IN -> state.isOptedIn
            OPT_OUT -> !state.isOptedIn
        }

        override fun toJson() = jsonMapBuilder()
            .put(KEY_STATUS, status.jsonValue)
            .build()
    }

    data class State(
        val isOptedIn: Boolean
    )

    abstract fun evaluate(state: State): Boolean

    internal abstract fun toJson(): JsonMap

    protected fun jsonMapBuilder(): JsonMap.Builder =
        JsonMap.newBuilder().put(KEY_TYPE, type)

    companion object {
        private const val TYPE_NOTIFICATION_OPT_IN = "notification_opt_in"

        private const val KEY_TYPE = "type"
        private const val KEY_STATUS = "when_status"

        internal fun parse(json: JsonMap): Condition {
            return when (val type = json.get(KEY_TYPE)?.string) {
                TYPE_NOTIFICATION_OPT_IN -> NotificationOptIn(
                    status = json.requireField<String>(KEY_STATUS).let { Status.parse(it) }
                )
                else -> throw JsonException("Unknown Condition type: '$type'")
            }
        }

        internal fun parse(json: JsonValue?): Conditions =
            json?.list?.map { parse(it.optMap()) } ?: emptyList()
    }
}
