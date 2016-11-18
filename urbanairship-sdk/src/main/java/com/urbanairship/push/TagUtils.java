/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A class containing utility methods related to tags.
 */
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
                Logger.error("Tag with zero or greater than max length was removed from set: " + tag);
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
    static Map<String, Set<String>> convertToTagsMap(JsonValue jsonValue) {
        if (jsonValue == null || jsonValue.isNull()) {
            return null;
        }

        Map<String, Set<String>> tagGroups = new HashMap<>();

        if (jsonValue.isJsonMap()) {
            for (Map.Entry<String, JsonValue> groupEntry : jsonValue.getMap()) {
                Set<String> tags = new HashSet<>();
                for (JsonValue tag : groupEntry.getValue().getList()) {
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

    /**
     * Converts the old tag group store to tag mutations.
     *
     * @param dataStore The data store.
     * @param pendingAddTagsKey The old pending add tags key.
     * @param pendingRemoveTagsKey The old pending remove tags key.
     * @param tagsMutationKey The new mutation key.
     */
    static void migrateTagGroups(PreferenceDataStore dataStore, String pendingAddTagsKey, String pendingRemoveTagsKey, String tagsMutationKey) {
        JsonValue pendingAddTags = dataStore.getJsonValue(pendingAddTagsKey);
        JsonValue pendingRemoveTags = dataStore.getJsonValue(pendingRemoveTagsKey);

        if (pendingAddTags.isNull() && pendingRemoveTags.isNull()) {
            return;
        }

        Map<String, Set<String>> addTags = TagUtils.convertToTagsMap(pendingAddTags);
        Map<String, Set<String>> removeTags = TagUtils.convertToTagsMap(pendingRemoveTags);

        TagGroupMutation mutation = TagGroupMutation.newAddRemoveMutation(addTags, removeTags);
        List<TagGroupMutation> mutations = Collections.singletonList(mutation);

        dataStore.put(tagsMutationKey, JsonValue.wrapOpt(mutations));

        dataStore.remove(pendingAddTagsKey);
        dataStore.remove(pendingRemoveTagsKey);
    }
}
