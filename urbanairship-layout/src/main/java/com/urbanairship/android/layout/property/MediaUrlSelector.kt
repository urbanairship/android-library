/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

public class MediaUrlSelector public constructor(
    public val platform: Platform?,
    public val darkMode: Boolean?,
    public val url: String,
) {
    public companion object {
        private const val KEY_PLATFORM = "platform"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_URL = "url"

        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): MediaUrlSelector {
            val content = json.requireMap()
            return MediaUrlSelector(
                platform = content[KEY_PLATFORM]?.let(Platform::from),
                darkMode = content.optionalField(KEY_DARK_MODE),
                url = content.requireField(KEY_URL),
            )
        }

        @Throws(JsonException::class)
        public fun fromJsonList(json: JsonList): List<MediaUrlSelector> {
            return json
                .map(::fromJson)
                .filter { it.platform == null || it.platform == Platform.ANDROID }
        }

        public fun resolve(url: String, urlSelectors: List<MediaUrlSelector>, isDarkMode: Boolean): String {
            for (selector in urlSelectors) {
                val modeMatches = selector.darkMode == null || selector.darkMode == isDarkMode
                if (modeMatches) return selector.url
            }
            return url
        }
    }
}
