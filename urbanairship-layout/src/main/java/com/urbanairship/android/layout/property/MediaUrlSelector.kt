/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField

public class MediaUrlSelector public constructor(
    /** What the environment has to match for [url] to apply. */
    public val conditions: SelectorConditions,
    public val url: String,
) {
    public companion object {
        private const val KEY_URL = "url"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): MediaUrlSelector {
            val content = json.requireMap()
            return MediaUrlSelector(
                conditions = SelectorConditions.fromJson(content),
                url = content.requireField(KEY_URL),
            )
        }

        @Throws(JsonException::class)
        public fun fromJsonList(json: JsonList): List<MediaUrlSelector> {
            return json.map(::fromJson)
        }

        /** Returns the first selector's url that [environment] matches, else [url]. */
        internal fun resolve(url: String, urlSelectors: List<MediaUrlSelector>, environment: SelectorEnvironment): String =
            urlSelectors.firstOrNull { it.conditions.matches(environment) }?.url ?: url
    }
}
