/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.info

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

/**
 * Native control options from layout.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class NativeControlOptions(
    public val stateRestoration: StateRestoration?
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_STATE_RESTORATION to stateRestoration
    ).toJsonValue()

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public data class StateRestoration(
        public val scope: Scope,
        public val restoreId: String
    ) : JsonSerializable {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_SCOPE to scope,
            KEY_RESTORE_ID to restoreId
        ).toJsonValue()

        /**
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public enum class Scope(internal val json: String) : JsonSerializable {
            INSTANCE("instance");

            override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

            public companion object {
                @Throws(JsonException::class)
                public fun fromJson(value: JsonValue): Scope {
                    val content = value.requireString()

                    return entries.find { it.json == content }
                        ?: throw JsonException("Invalid state restoration scope: $value")
                }
            }
        }

        internal companion object {
            private const val KEY_SCOPE = "scope"
            private const val KEY_RESTORE_ID = "restore_id"

            @Throws(JsonException::class)
            internal fun fromJson(json: JsonValue): StateRestoration {
                val content = json.requireMap()

                return StateRestoration(
                    scope = Scope.fromJson(content.require(KEY_SCOPE)),
                    restoreId = content.requireField(KEY_RESTORE_ID)
                )
            }
        }
    }

    internal companion object {
        private const val KEY_STATE_RESTORATION = "state_restoration"

        @Throws(JsonException::class)
        internal fun fromJson(json: JsonValue): NativeControlOptions {
            val content = json.requireMap()

            return NativeControlOptions(
                stateRestoration = content[KEY_STATE_RESTORATION]?.let(StateRestoration::fromJson)
            )
        }
    }
}
