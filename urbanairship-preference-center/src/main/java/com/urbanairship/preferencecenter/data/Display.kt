package com.urbanairship.preferencecenter.data

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.util.jsonMapOf
import com.urbanairship.preferencecenter.util.optionalField

/**
 * Common display attributes.
 */
data class CommonDisplay(
    val name: String? = null,
    val description: String? = null
) {
    companion object {
        @JvmStatic val EMPTY = CommonDisplay(null, null)

        private const val KEY_NAME = "name"
        private const val KEY_DESCRIPTION = "description"

        /**
         * Parses a `JsonMap` into a `CommonDisplay` object.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonMap): CommonDisplay =
            CommonDisplay(
                name = json.optionalField(KEY_NAME),
                description = json.optionalField(KEY_DESCRIPTION)
            )

        /**
         * Parses a `JsonValue` into a `CommonDisplay` object.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonValue?): CommonDisplay =
            json?.map?.let { parse(it) } ?: EMPTY
    }

    internal fun isEmpty(): Boolean =
        name.isNullOrEmpty() && description.isNullOrEmpty()

    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_NAME to name,
        KEY_DESCRIPTION to description
    )
}

/**
 * Icon display attributes.
 */
data class IconDisplay(
    val icon: String? = null,
    val name: String? = null,
    val description: String? = null
) {
    companion object {
        private const val KEY_ICON = "icon"
        private const val KEY_NAME = "name"
        private const val KEY_DESCRIPTION = "description"

        /**
         * Parses a `JsonMap` into a `IconDisplay` object.
         *
         * @hide
         * @throws JsonException
         */
        internal fun parse(json: JsonMap): IconDisplay =
            IconDisplay(
                icon = json.optionalField(KEY_ICON),
                name = json.optionalField(KEY_NAME),
                description = json.optionalField(KEY_DESCRIPTION)
            )
    }

    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_NAME to name,
        KEY_DESCRIPTION to description,
        KEY_ICON to icon
    )
}
