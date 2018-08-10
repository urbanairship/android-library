/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.tags;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/***
 * Utils for Tag Groups.
 */
class TagGroupUtils {

    /**
     * Checks if lh argument contains all the tags in the rh argument.
     *
     * @param lh Map of tags groups to tags.
     * @param rh Map of tags groups to tags.
     * @return {@code true} if the lh contains all the rh tags.
     */
    static boolean containsAll(@NonNull Map<String, Set<String>> lh, @NonNull Map<String, Set<String>> rh) {
        for (Map.Entry<String, Set<String>> entry : rh.entrySet()) {

            if (!lh.containsKey(entry.getKey())) {
                return false;
            }

            if (!lh.get(entry.getKey()).containsAll(entry.getValue())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns all the tags that are in both maps.
     *
     * @param lh Map of tags groups to tags.
     * @param rh Map of tags groups to tags.
     * @return A new map tag groups that are contained in both arguments.
     */
    static Map<String, Set<String>> intersect(@NonNull Map<String, Set<String>> lh, @NonNull Map<String, Set<String>> rh) {
        Map<String, Set<String>> result = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : lh.entrySet()) {
            String tagGroup = entry.getKey();

            if (rh.containsKey(tagGroup)) {
                Set<String> tags = new HashSet<>(rh.get(tagGroup));
                tags.retainAll(entry.getValue());
                result.put(tagGroup, tags);
            }
        }

        return result;
    }

    /**
     * Parses tags as {@code Map<String, Set<String>>} from a json value.
     *
     * @param value The json value.
     * @return The parsed tags.
     */
    @NonNull
    static Map<String, Set<String>> parseTags(@Nullable JsonValue value) {
        Map<String, Set<String>> tagGroups = new HashMap<>();

        if (value == null) {
            return tagGroups;
        }

        for (Map.Entry<String, JsonValue> entry : value.optMap()) {
            if (!tagGroups.containsKey(entry.getKey())) {
                tagGroups.put(entry.getKey(), new HashSet<String>());
            }

            Set<String> tagSet = tagGroups.get(entry.getKey());

            for (JsonValue tag : entry.getValue().optList()) {
                if (tag.isString()) {
                    tagSet.add(tag.getString());
                }
            }
        }

        return tagGroups;
    }

}
