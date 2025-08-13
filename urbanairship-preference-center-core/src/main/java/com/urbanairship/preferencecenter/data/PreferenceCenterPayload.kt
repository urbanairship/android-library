package com.urbanairship.preferencecenter.data

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf

/**
 * Preference Center Payload from Remote Data.
 */
internal data class PreferenceCenterPayload(
    val config: PreferenceCenterConfig
) {
    companion object {
        internal const val KEY_FORM = "form"

        /**
         * Parses a `JsonMap` into a `PreferenceCenterPayload` object.
         *
         * @hide
         * @throws JsonException
         */
        @Throws(JsonException::class)
        internal fun parse(json: JsonMap): PreferenceCenterPayload =
            PreferenceCenterPayload(PreferenceCenterConfig.parse(json.opt(KEY_FORM).optMap()))
    }

    @Throws(JsonException::class)
    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_FORM to config.toJson()
    )
}
