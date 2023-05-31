/* Copyright Airship and Contributors */

package com.urbanairship.channel

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.util.Clock

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class LiveUpdateMutation(
    private val action: String,
) : JsonSerializable {
    protected abstract val name: String
    protected abstract val startTime: Long
    protected abstract val actionTime: Long

    public class Set(
        override val name: String,
        override val startTime: Long,
        override val actionTime: Long = Clock.DEFAULT_CLOCK.currentTimeMillis()
    ) : LiveUpdateMutation(ACTION_SET)

    public class Remove(
        override val name: String,
        override val startTime: Long,
        override val actionTime: Long = Clock.DEFAULT_CLOCK.currentTimeMillis()
    ) : LiveUpdateMutation(ACTION_REMOVE)

    override fun toJsonValue(): JsonValue =
        jsonMapOf(
            KEY_ACTION to action,
            KEY_NAME to name,
            KEY_START_TS to startTime,
            KEY_ACTION_TS to actionTime
        ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LiveUpdateMutation

        if (action != other.action) return false
        if (name != other.name) return false
        if (startTime != other.startTime) return false
        if (actionTime != other.actionTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = action.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + startTime.hashCode()
        result = 31 * result + actionTime.hashCode()
        return result
    }

    internal companion object {
        private const val KEY_ACTION = "action"
        private const val KEY_NAME = "name"
        private const val KEY_START_TS = "start_ts_ms"
        private const val KEY_ACTION_TS = "action_ts_ms"
        private const val ACTION_SET = "set"
        private const val ACTION_REMOVE = "remove"

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): LiveUpdateMutation {
            val action: String = json.requireField(KEY_ACTION)
            val name: String = json.requireField(KEY_NAME)
            val startTime: Long = json.requireField(KEY_START_TS)
            val actionTime: Long = json.requireField(KEY_ACTION_TS)

            return when (action) {
                ACTION_SET -> Set(name, startTime, actionTime)
                ACTION_REMOVE -> Remove(name, startTime, actionTime)
                else -> throw JsonException("Failed to parse LiveUpdateMutation json: $json")
            }
        }
    }
}
