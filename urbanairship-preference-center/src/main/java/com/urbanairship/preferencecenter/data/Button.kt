package com.urbanairship.preferencecenter.data

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.preferencecenter.util.ActionsMap
import com.urbanairship.preferencecenter.util.jsonMapOf
import com.urbanairship.preferencecenter.util.optionalField
import com.urbanairship.preferencecenter.util.requireField

/**
 * Button attributes.
 */
data class Button(
    val text: String,
    val contentDescription: String?,
    val actions: ActionsMap
) {
    companion object {
        private const val KEY_TEXT = "text"
        private const val KEY_CONTENT_DESCRIPTION = "content_description"
        private const val KEY_ACTIONS = "actions"

        /**
         * Parses a `JsonMap` into a `Button` object.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonMap?): Button? = json?.let {
            Button(
                text = it.requireField(KEY_TEXT),
                contentDescription = it.optionalField(KEY_CONTENT_DESCRIPTION),
                actions = it.requireField<JsonMap>(KEY_ACTIONS).map
            )
        }
    }

    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_TEXT to text,
        KEY_CONTENT_DESCRIPTION to contentDescription,
        KEY_ACTIONS to actions
    )
}
