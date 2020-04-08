/* Copyright Airship and Contributors */

package com.urbanairship.iam.tags;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/***
 * Utils for Tag Groups.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TagGroupUtils {

    /**
     * Creates a new map of the union of the two provided maps.
     *
     * @param lh The left map.
     * @param rh The right map.
     * @return A new map that contains all the elements from both maps.
     */
    @NonNull
    public static Map<String, Set<String>> union(@NonNull Map<String, Set<String>> lh, @NonNull Map<String, Set<String>> rh) {
        Map<String, Set<String>> result = new HashMap<>();
        addAll(result, lh);
        addAll(result, rh);
        return result;
    }

    /**
     * Adds all tags from the right map to the left map. Use this instead of {@link Map#putAll(Map)}
     * to combine the tag sets if the two maps contain the same groups.
     * <p>
     * This method will mutate the left map. To avoid mutating the parameters, use {@link #union(Map, Map)}.
     *
     * @param lh The map that will have all the elements.
     * @param rh The elements to add.
     */
    public static void addAll(@NonNull Map<String, Set<String>> lh, @NonNull Map<String, Set<String>> rh) {
        for (Map.Entry<String, Set<String>> entry : rh.entrySet()) {
            Set<String> tags = lh.get(entry.getKey());
            if (tags == null) {
                tags = new HashSet<>();
                lh.put(entry.getKey(), tags);
            }
            tags.addAll(entry.getValue());
        }
    }

    /**
     * Checks if lh argument contains all the tags in the rh argument.
     *
     * @param lh Map of tags groups to tags.
     * @param rh Map of tags groups to tags.
     * @return {@code true} if the lh contains all the rh tags.
     */
    public static boolean containsAll(@NonNull Map<String, Set<String>> lh, @NonNull Map<String, Set<String>> rh) {
        for (Map.Entry<String, Set<String>> entry : rh.entrySet()) {

            Set<String> tags = lh.get(entry.getKey());
            if (tags == null || !tags.containsAll(entry.getValue())) {
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
    @NonNull
    public static Map<String, Set<String>> intersect(@NonNull Map<String, Set<String>> lh, @NonNull Map<String, Set<String>> rh) {
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
    public static Map<String, Set<String>> parseTags(@Nullable JsonValue value) {
        Map<String, Set<String>> tagGroups = new HashMap<>();

        if (value == null) {
            return tagGroups;
        }

        for (Map.Entry<String, JsonValue> entry : value.optMap()) {
            Set<String> tagSet = tagGroups.get(entry.getKey());

            if (tagSet == null) {
                tagSet = new HashSet<>();
                tagGroups.put(entry.getKey(), tagSet);
            }

            for (JsonValue tag : entry.getValue().optList()) {
                if (tag.isString()) {
                    tagSet.add(tag.getString());
                }
            }
        }

        return tagGroups;
    }

}
