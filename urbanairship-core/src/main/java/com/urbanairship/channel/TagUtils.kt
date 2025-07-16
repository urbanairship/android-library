/* Copyright Airship and Contributors */
package com.urbanairship.channel

import com.urbanairship.UALog
import com.urbanairship.json.JsonValue

/**
 * A class containing utility methods related to tags.
 * @hide
 */
internal object TagUtils {

    private const val MAX_TAG_LENGTH = 127

    /**
     * Normalizes a set of tags. Each tag will be trimmed of white space and any tag that
     * is empty, null, or exceeds [MAX_TAG_LENGTH] will be dropped.
     *
     * @param tags The set of tags to normalize.
     * @return The set of normalized, valid tags.
     */
    fun normalizeTags(tags: Set<String>): Set<String> {
        return tags
            .mapNotNull { entry ->
                val tag = entry.trim { it <= ' ' }
                if (tag.isEmpty() || tag.length > MAX_TAG_LENGTH) {
                    UALog.e("Tag with zero or greater than max length was removed from set: $tag")
                    null
                } else {
                    tag
                }
            }
            .toSet()
    }

    /**
     * Converts a [JsonValue] to a Tags Map.
     *
     * @param jsonValue The value to convert.
     * @return A tag group map.
     */
    fun convertToTagsMap(jsonValue: JsonValue?): Map<String, Set<String>>? {
        val content = jsonValue?.optMap() ?: return null

        val result = content.map { (group, value) ->
            val tags = value.optList().mapNotNull { it.string }
            group to tags.toSet()
        }

        if (result.isEmpty()) {
            return null
        }

        return result.toMap()
    }
}
