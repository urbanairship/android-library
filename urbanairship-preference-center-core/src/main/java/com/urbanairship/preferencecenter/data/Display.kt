package com.urbanairship.preferencecenter.data

import android.os.Parcelable
import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import kotlin.jvm.Throws
import kotlinx.parcelize.Parcelize

/**
 * Common display attributes.
 */
public data class CommonDisplay(
    val name: String? = null,
    val description: String? = null
) {
    public companion object {
        public val EMPTY = CommonDisplay(null, null)

        private const val KEY_NAME = "name"
        private const val KEY_DESCRIPTION = "description"

        /**
         * Parses a `JsonMap` into a `CommonDisplay` object.
         *
         * @hide
         * @throws JsonException
         */
        @Throws(JsonException::class)
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
        @Throws(JsonException::class)
        internal fun parse(json: JsonValue?): CommonDisplay =
            json?.map?.let { parse(it) } ?: EMPTY
    }

    public fun isEmpty(): Boolean =
        name.isNullOrEmpty() && description.isNullOrEmpty()

    @Throws(JsonException::class)
    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_NAME to name,
        KEY_DESCRIPTION to description
    )
}

/**
 * Icon display attributes.
 */
public data class IconDisplay(
    val icon: String? = null,
    val name: String? = null,
    val description: String? = null
) {
    internal companion object {
        private const val KEY_ICON = "icon"
        private const val KEY_NAME = "name"
        private const val KEY_DESCRIPTION = "description"

        /**
         * Parses a `JsonMap` into a `IconDisplay` object.
         *
         * @hide
         * @throws JsonException
         */
        @Throws(JsonException::class)
        internal fun parse(json: JsonMap): IconDisplay =
            IconDisplay(
                icon = json.optionalField(KEY_ICON),
                name = json.optionalField(KEY_NAME),
                description = json.optionalField(KEY_DESCRIPTION)
            )
    }

    @Throws(JsonException::class)
    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_NAME to name,
        KEY_DESCRIPTION to description,
        KEY_ICON to icon
    )
}
