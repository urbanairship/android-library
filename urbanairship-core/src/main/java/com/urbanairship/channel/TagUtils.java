/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * A class containing utility methods related to tags.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class TagUtils {

    private static final int MAX_TAG_LENGTH = 127;

    /**
     * Normalizes a set of tags. Each tag will be trimmed of white space and any tag that
     * is empty, null, or exceeds {@link #MAX_TAG_LENGTH} will be dropped.
     *
     * @param tags The set of tags to normalize.
     * @return The set of normalized, valid tags.
     */
    @NonNull
    static Set<String> normalizeTags(@NonNull Set<String> tags) {
        Set<String> normalizedTags = new HashSet<>();

        for (String tag : tags) {
            if (tag == null) {
                Logger.debug("Null tag was removed from set.");
                continue;
            }

            tag = tag.trim();
            if (tag.length() <= 0 || tag.length() > MAX_TAG_LENGTH) {
                Logger.error("Tag with zero or greater than max length was removed from set: %s", tag);
                continue;
            }

            normalizedTags.add(tag);
        }

        return normalizedTags;
    }

    /**
     * Converts a JSONValue to a Tags Map.
     *
     * @param jsonValue The value to convert.
     * @return A tag group map.
     */
    @Nullable
    static Map<String, Set<String>> convertToTagsMap(@Nullable JsonValue jsonValue) {
        if (jsonValue == null || jsonValue.isNull()) {
            return null;
        }

        Map<String, Set<String>> tagGroups = new HashMap<>();

        if (jsonValue.isJsonMap()) {
            for (Map.Entry<String, JsonValue> groupEntry : jsonValue.optMap()) {
                Set<String> tags = new HashSet<>();
                for (JsonValue tag : groupEntry.getValue().optList()) {
                    if (tag.isString()) {
                        tags.add(tag.getString());
                    }
                }

                tagGroups.put(groupEntry.getKey(), tags);
            }
        }

        if (tagGroups.isEmpty()) {
            return null;
        }

        return tagGroups;
    }

}
