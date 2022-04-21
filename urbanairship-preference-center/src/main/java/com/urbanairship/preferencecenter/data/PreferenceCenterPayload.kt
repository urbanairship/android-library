package com.urbanairship.preferencecenter.data

import com.urbanairship.json.JsonMap
import com.urbanairship.preferencecenter.util.jsonMapOf

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
        internal fun parse(json: JsonMap): PreferenceCenterPayload =
            PreferenceCenterPayload(PreferenceCenterConfig.parse(json.opt(KEY_FORM).optMap()))
    }

    internal fun toJson(): JsonMap = jsonMapOf(
        KEY_FORM to config.toJson()
    )
}
