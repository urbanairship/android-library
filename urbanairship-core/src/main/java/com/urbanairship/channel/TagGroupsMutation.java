/* Copyright Airship and Contributors */

package com.urbanairship.channel;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Defines a tag group mutations.
 *
 * @hide
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
    private TagGroupsMutation(@Nullable Map<String, Set<String>> addTags, @Nullable Map<String, Set<String>> removeTags, @Nullable Map<String, Set<String>> setTags) {
        this.addTags = addTags == null ? Collections.<String, Set<String>>emptyMap() : addTags;
        this.removeTags = removeTags == null ? Collections.<String, Set<String>>emptyMap() : removeTags;
        this.setTags = setTags == null ? Collections.<String, Set<String>>emptyMap() : setTags;
    }

    /**
     * Creates a mutation to add tags to a group.
     *
     * @param group Group ID.
     * @param tags Tags to add.
     * @return Tag group mutation.
     */
    @NonNull
    public static TagGroupsMutation newAddTagsMutation(@NonNull String group, @NonNull Set<String> tags) {
        HashMap<String, Set<String>> tagMap = new HashMap<>();
        tagMap.put(group, new HashSet<>(tags));

        return new TagGroupsMutation(tagMap, null, null);
    }

    /**
     * Creates a mutation to remove tags to a group.
     *
     * @param group Group ID.
     * @param tags Tags to remove.
     * @return Tag group mutation.
     */
    @NonNull
    public static TagGroupsMutation newRemoveTagsMutation(@NonNull String group, @NonNull Set<String> tags) {
        HashMap<String, Set<String>> tagMap = new HashMap<>();
        tagMap.put(group, new HashSet<>(tags));

        return new TagGroupsMutation(null, tagMap, null);
    }

    /**
     * Creates a mutation to set tags to a group.
     *
     * @param group Group ID.
     * @param tags Tags to set.
     * @return Tag group mutation.
     */
    @NonNull
    public static TagGroupsMutation newSetTagsMutation(@NonNull String group, @NonNull Set<String> tags) {
        HashMap<String, Set<String>> tagMap = new HashMap<>();
        tagMap.put(group, new HashSet<>(tags));

        return new TagGroupsMutation(null, null, tagMap);
    }

    /**
     * Collapses mutations down to a minimum set of mutations.
     *
     * @param mutations List of mutations to collapse.
     * @return A new list of collapsed mutations.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static List<TagGroupsMutation> collapseMutations(@Nullable List<TagGroupsMutation> mutations) {
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
                    Set<String> existingSetTags = setTags.get(group);
                    if (existingSetTags != null) {
                        existingSetTags.addAll(tags);
                        continue;
                    }

                    // Remove from remove tag groups
                    Set<String> existingRemoveTags = removeTags.get(group);
                    if (existingRemoveTags != null) {
                        existingRemoveTags.removeAll(tags);
                        if (existingRemoveTags.isEmpty()) {
                            removeTags.remove(group);
                        }
                    }

                    // Add to add tags
                    Set<String> existingAddTags = addTags.get(group);
                    if (existingAddTags == null) {
                        existingAddTags = new HashSet<>();
                        addTags.put(group, existingAddTags);
                    }
                    existingAddTags.addAll(tags);

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
                    Set<String> existingSetTags = setTags.get(group);
                    if (existingSetTags != null) {
                        existingSetTags.removeAll(tags);
                        continue;
                    }

                    // Remove from add tag groups
                    Set<String> existingAddTags = addTags.get(group);
                    if (existingAddTags != null) {
                        existingAddTags.removeAll(tags);
                        if (existingAddTags.isEmpty()) {
                            addTags.remove(group);
                        }
                    }

                    // Add to remove tags
                    Set<String> existingRemoveTags = removeTags.get(group);
                    if (existingRemoveTags == null) {
                        existingRemoveTags = new HashSet<>();
                        removeTags.put(group, existingRemoveTags);
                    }
                    existingRemoveTags.addAll(tags);
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

    @NonNull
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

    @NonNull
    public static TagGroupsMutation fromJsonValue(@NonNull JsonValue jsonValue) {
        JsonMap jsonMap = jsonValue.optMap();

        Map<String, Set<String>> addTags = TagUtils.convertToTagsMap(jsonMap.opt(ADD_KEY));
        Map<String, Set<String>> removeTags = TagUtils.convertToTagsMap(jsonMap.opt(REMOVE_KEY));
        Map<String, Set<String>> setTags = TagUtils.convertToTagsMap(jsonMap.opt(SET_KEY));

        return new TagGroupsMutation(addTags, removeTags, setTags);
    }

    @NonNull
    public static List<TagGroupsMutation> fromJsonList(@NonNull JsonList jsonList) {
        List<TagGroupsMutation> mutations = new ArrayList<>();

        for (JsonValue value : jsonList) {
            TagGroupsMutation mutation = fromJsonValue(value);
            mutations.add(mutation);
        }

        return mutations;
    }

    boolean isEmpty() {
        if (addTags != null && !addTags.isEmpty()) {
            return false;
        }

        if (removeTags != null && !removeTags.isEmpty()) {
            return false;
        }

        if (setTags != null && !setTags.isEmpty()) {
            return false;
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TagGroupsMutation mutation = (TagGroupsMutation) o;

        if (addTags != null ? !addTags.equals(mutation.addTags) : mutation.addTags != null)
            return false;
        if (removeTags != null ? !removeTags.equals(mutation.removeTags) : mutation.removeTags != null)
            return false;
        return setTags != null ? setTags.equals(mutation.setTags) : mutation.setTags == null;
    }

    @Override
    public int hashCode() {
        int result = addTags != null ? addTags.hashCode() : 0;
        result = 31 * result + (removeTags != null ? removeTags.hashCode() : 0);
        result = 31 * result + (setTags != null ? setTags.hashCode() : 0);
        return result;
    }

    public void apply(@NonNull Map<String, Set<String>> tagGroups) {
        // Add tags
        if (addTags != null) {
            for (Map.Entry<String, Set<String>> entry : addTags.entrySet()) {
                Set<String> tags = tagGroups.get(entry.getKey());
                if (tags == null) {
                    tags = new HashSet<>();
                    tagGroups.put(entry.getKey(), tags);
                }

                tags.addAll(entry.getValue());
            }
        }

        // Remove tags
        if (removeTags != null) {
            for (Map.Entry<String, Set<String>> entry : removeTags.entrySet()) {
                Set<String> tags = tagGroups.get(entry.getKey());
                if (tags != null) {
                    tags.removeAll(entry.getValue());
                }
            }
        }

        // Set tags
        if (setTags != null) {
            for (Map.Entry<String, Set<String>> entry : setTags.entrySet()) {
                tagGroups.put(entry.getKey(), entry.getValue());
            }
        }
    }

}
