package com.urbanairship.preferencecenter.data

import android.os.Parcelable
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField
import com.urbanairship.preferencecenter.data.Condition.OptInStatus.Status
import com.urbanairship.preferencecenter.data.Condition.OptInStatus.Status.OPT_IN
import com.urbanairship.preferencecenter.data.Condition.OptInStatus.Status.OPT_OUT
import kotlinx.parcelize.Parcelize

internal typealias Conditions = List<Condition>

/**
 * Evaluates this list of conditions against the given [state].
 *
 * Returns `true` if:
 * - Any condition evaluates to `true`.
 * - The conditions list is empty.
 */
internal fun Conditions.evaluate(state: Condition.State): Boolean =
    isEmpty() || any { it.evaluate(state) }

/** Base condition, used to determine visibility of preference sections and items. */
public sealed class Condition(private val type: String) {

    /** Notification opt-in status condition. */
    public data class OptInStatus(
        val status: Status
    ) : Condition(TYPE_NOTIFICATION_OPT_IN) {

        public enum class Status(public val jsonValue: String) {
            OPT_IN("opt_in"),
            OPT_OUT("opt_out");

            public companion object {
                internal fun parse(json: String) =
                    valueOf(json.uppercase())
            }
        }

        public override fun evaluate(state: State): Boolean = when (status) {
            OPT_IN -> state.isOptedIn
            OPT_OUT -> !state.isOptedIn
        }

        override fun toJson() = jsonMapBuilder()
            .put(KEY_STATUS, status.jsonValue)
            .build()
    }

    @Parcelize
    public data class State(
        val isOptedIn: Boolean
    ): Parcelable

    public abstract fun evaluate(state: State): Boolean

    internal abstract fun toJson(): JsonMap

    protected fun jsonMapBuilder(): JsonMap.Builder =
        JsonMap.newBuilder().put(KEY_TYPE, type)

    internal companion object {
        private const val TYPE_NOTIFICATION_OPT_IN = "notification_opt_in"
        private const val TYPE_SMS_OPT_IN = "sms_opt_in"
        private const val TYPE_EMAIL_OPT_IN = "email_opt_in"

        private const val KEY_TYPE = "type"
        private const val KEY_STATUS = "when_status"

        @Throws(JsonException::class)
        internal fun parse(json: JsonMap): Condition {
            return when (val type = json.get(KEY_TYPE)?.string) {
                TYPE_NOTIFICATION_OPT_IN -> OptInStatus(
                    status = json.requireField<String>(KEY_STATUS).let { Status.parse(it) }
                )
                TYPE_SMS_OPT_IN -> OptInStatus(
                    status = json.requireField<String>(KEY_STATUS).let { Status.parse(it) }
                )
                TYPE_EMAIL_OPT_IN -> OptInStatus(
                    status = json.requireField<String>(KEY_STATUS).let { Status.parse(it) }
                )
                else -> throw JsonException("Unknown Condition type: '$type'")
            }
        }

        @Throws(JsonException::class)
        internal fun parse(json: JsonValue?): Conditions =
            json?.list?.map { parse(it.optMap()) } ?: emptyList()
    }
}
