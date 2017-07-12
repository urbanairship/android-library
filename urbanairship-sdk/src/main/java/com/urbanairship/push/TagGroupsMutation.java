/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defines a tag group mutations.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TagGroupsMutation implements JsonSerializable {

    private static final String ADD_KEY = "add";
    private static final String REMOVE_KEY = "remove";
    private static final String SET_KEY = "set";

    private final Map<String, Set<String>> addTags;
    private final Map<String, Set<String>> removeTags;
    private final Map<String, Set<String>> setTags;

    /**
     * Default constructor.
     *
     * @param addTags Map of pending add tags.
     * @param removeTags Map of pending remove tags.
     * @param setTags Map of pending set tags.
     */
    private TagGroupsMutation(Map<String, Set<String>> addTags, Map<String, Set<String>> removeTags, Map<String, Set<String>> setTags) {
        this.addTags = addTags;
        this.removeTags = removeTags;
        this.setTags = setTags;
    }

    /**
     * Creates a mutation to add tags to a group.
     *
     * @param group Group ID.
     * @param tags Tags to add.
     * @return Tag group mutation.
     */
    static TagGroupsMutation newAddTagsMutation(@NonNull String group, @NonNull Set<String> tags) {
        HashMap<String, Set<String>> tagMap = new HashMap<>();
        tagMap.put(group, tags);

        return new TagGroupsMutation(tagMap, null, null);
    }

    /**
     * Creates a mutation to remove tags to a group.
     *
     * @param group Group ID.
     * @param tags Tags to remove.
     * @return Tag group mutation.
     */
    static TagGroupsMutation newRemoveTagsMutation(@NonNull String group, @NonNull Set<String> tags) {
        HashMap<String, Set<String>> tagMap = new HashMap<>();
        tagMap.put(group, tags);

        return new TagGroupsMutation(null, tagMap, null);
    }

    /**
     * Creates a mutation to set tags to a group.
     *
     * @param group Group ID.
     * @param tags Tags to set.
     * @return Tag group mutation.
     */
    static TagGroupsMutation newSetTagsMutation(@NonNull String group, @NonNull Set<String> tags) {
        HashMap<String, Set<String>> tagMap = new HashMap<>();
        tagMap.put(group, tags);

        return new TagGroupsMutation(null, null, tagMap);
    }

    /**
     * Creates a mutation from a set of pending add tag groups and pending remove tag groups.
     *
     * @param pendingAddTags Map of pending add tags.
     * @param pendingRemoveTags Map of pending remove tags.
     * @return Tag group mutation.
     */
    static TagGroupsMutation newAddRemoveMutation(Map<String, Set<String>> pendingAddTags, Map<String, Set<String>> pendingRemoveTags) {
        Map<String, Set<String>> normalizedPendingAddTags = new HashMap<>();
        Map<String, Set<String>> normalizedPendingRemoveTags = new HashMap<>();

        if (pendingAddTags != null) {
            for (Map.Entry<String, Set<String>> entry : pendingAddTags.entrySet()) {
                String group = entry.getKey().trim();
                if (group.isEmpty()) {
                    continue;
                }

                if (entry.getValue() == null) {
                    continue;
                }

                Set<String> tags = TagUtils.normalizeTags(entry.getValue());
                if (tags.isEmpty()) {
                    continue;
                }

                normalizedPendingAddTags.put(entry.getKey(), tags);
            }
        }

        if (pendingRemoveTags != null) {
            for (Map.Entry<String, Set<String>> entry : pendingRemoveTags.entrySet()) {
                String group = entry.getKey().trim();
                if (group.isEmpty()) {
                    continue;
                }

                if (entry.getValue() == null) {
                    continue;
                }

                Set<String> tags = TagUtils.normalizeTags(entry.getValue());
                if (tags.isEmpty()) {
                    continue;
                }

                normalizedPendingRemoveTags.put(entry.getKey(), tags);
            }
        }

        return new TagGroupsMutation(normalizedPendingAddTags, normalizedPendingRemoveTags, null);
    }

    /**
     * Collapses mutations down to a minimum set of mutations.
     *
     * @param mutations List of mutations to collapse.
     * @return A new list of collapsed mutations.
     */
    static List<TagGroupsMutation> collapseMutations(List<TagGroupsMutation> mutations) {
        if (mutations == null || mutations.isEmpty()) {
            return Collections.emptyList();
        }

        HashMap<String, Set<String>> addTags = new HashMap<>();
        HashMap<String, Set<String>> removeTags = new HashMap<>();
        HashMap<String, Set<String>> setTags = new HashMap<>();

        for (TagGroupsMutation mutation : mutations) {
            // Add tags
            if (mutation.addTags != null) {
                for (Map.Entry<String, Set<String>> entry : mutation.addTags.entrySet()) {
                    Set<String> tags = entry.getValue();
                    String group = entry.getKey().trim();

                    if (group.isEmpty() || tags == null || tags.isEmpty()) {
                        continue;
                    }

                    // Add to the set tag groups if we can
                    if (setTags.containsKey(group)) {
                        setTags.get(group).addAll(tags);
                        continue;
                    }

                    // Remove from remove tag groups
                    if (removeTags.containsKey(group)) {
                        removeTags.get(group).removeAll(tags);

                        if (removeTags.get(group).isEmpty()) {
                            removeTags.remove(group);
                        }
                    }

                    // Add to add tags
                    if (!addTags.containsKey(group)) {
                        addTags.put(group, new HashSet<String>());
                    }

                    addTags.get(group).addAll(tags);
                }
            }

            // Remove tags
            if (mutation.removeTags != null) {
                for (Map.Entry<String, Set<String>> entry : mutation.removeTags.entrySet()) {
                    Set<String> tags = entry.getValue();
                    String group = entry.getKey().trim();

                    if (group.isEmpty() || tags == null || tags.isEmpty()) {
                        continue;
                    }

                    // Remove from the set tag groups if we can
                    if (setTags.containsKey(group)) {
                        setTags.get(group).removeAll(tags);
                        continue;
                    }

                    // Remove from add tag groups
                    if (addTags.containsKey(group)) {
                        addTags.get(group).removeAll(tags);

                        if (addTags.get(group).isEmpty()) {
                            addTags.remove(group);
                        }
                    }

                    // Add to remove tags
                    if (!removeTags.containsKey(group)) {
                        removeTags.put(group, new HashSet<String>());
                    }

                    removeTags.get(group).addAll(tags);
                }
            }

            // Set tags
            if (mutation.setTags != null) {
                for (Map.Entry<String, Set<String>> entry : mutation.setTags.entrySet()) {
                    Set<String> tags = entry.getValue();
                    String group = entry.getKey().trim();

                    if (group.isEmpty()) {
                        continue;
                    }

                    // Set tags
                    setTags.put(group, tags == null ? new HashSet<String>() : new HashSet<>(tags));

                    // Remove from add and remove tags
                    removeTags.remove(group);
                    addTags.remove(group);
                }
            }
        }

        List<TagGroupsMutation> collapsedMutations = new ArrayList<>();

        // Set must be a separate mutation
        if (!setTags.isEmpty()) {
            TagGroupsMutation mutation = new TagGroupsMutation(null, null, setTags);
            collapsedMutations.add(mutation);
        }

        // Add and remove can be collapsed into one mutation
        if (!addTags.isEmpty() || !removeTags.isEmpty()) {
            TagGroupsMutation mutation = new TagGroupsMutation(addTags, removeTags, null);
            collapsedMutations.add(mutation);
        }

        return collapsedMutations;
    }

    @Override
    public JsonValue toJsonValue() {
        JsonMap.Builder builder = JsonMap.newBuilder();

        if (addTags != null && !addTags.isEmpty()) {
            builder.put(ADD_KEY, JsonValue.wrapOpt(addTags));
        }

        if (removeTags != null && !removeTags.isEmpty()) {
            builder.put(REMOVE_KEY, JsonValue.wrapOpt(removeTags));
        }

        if (setTags != null && !setTags.isEmpty()) {
            builder.put(SET_KEY, JsonValue.wrapOpt(setTags));
        }

        return builder.build().toJsonValue();
    }

    @Nullable
    public static TagGroupsMutation fromJsonValue(JsonValue jsonValue) {
        JsonMap jsonMap = jsonValue.optMap();

        Map<String, Set<String>> addTags = TagUtils.convertToTagsMap(jsonMap.get(ADD_KEY));
        Map<String, Set<String>> removeTags = TagUtils.convertToTagsMap(jsonMap.get(REMOVE_KEY));
        Map<String, Set<String>> setTags = TagUtils.convertToTagsMap(jsonMap.get(SET_KEY));

        if (addTags == null && removeTags == null && setTags == null) {
            return null;
        }

        return new TagGroupsMutation(addTags, removeTags, setTags);
    }

    @NonNull
    public static List<TagGroupsMutation> fromJsonList(JsonList jsonList) {
        List<TagGroupsMutation> mutations = new ArrayList<>();

        if (jsonList != null) {
            for (JsonValue value : jsonList) {
                TagGroupsMutation mutation = fromJsonValue(value);
                if (mutation != null) {
                    mutations.add(mutation);
                }
            }
        }

        return mutations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TagGroupsMutation mutation = (TagGroupsMutation) o;

        return mutation.toJsonValue().equals(mutation.toJsonValue());

    }

    @Override
    public int hashCode() {
        int result = addTags != null ? addTags.hashCode() : 0;
        result = 31 * result + (removeTags != null ? removeTags.hashCode() : 0);
        result = 31 * result + (setTags != null ? setTags.hashCode() : 0);
        return result;
    }
}
