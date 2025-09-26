package com.urbanairship.preferencecenter.data

import android.os.Parcelable
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import kotlinx.parcelize.Parcelize

/**
 * Button attributes.
 */
@Parcelize
public data class Button(
    val text: String,
    val contentDescription: String?,
    val actions: Map<String, JsonValue>
): Parcelable {
    internal companion object {
        private const val KEY_TEXT = "text"
        private const val KEY_CONTENT_DESCRIPTION = "content_description"
        private const val KEY_ACTIONS = "actions"

        /**
         * Parses a `JsonMap` into a `Button` object.
         *
         * @hide
         * @throws JsonException
         */
        @Throws(JsonException::class)
        internal fun parse(json: JsonMap?): Button? = json?.let {
            Button(
                text = it.requireField(KEY_TEXT),
                contentDescription = it.optionalField(KEY_CONTENT_DESCRIPTION),
                actions = it.requireField<JsonMap>(KEY_ACTIONS).map
            )
        }
    }

    @Throws(JsonException::class)
    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_TEXT to text,
        KEY_CONTENT_DESCRIPTION to contentDescription,
        KEY_ACTIONS to actions
    )
}
