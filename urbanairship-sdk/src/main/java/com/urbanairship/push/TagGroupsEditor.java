/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.os.Bundle;
import android.support.annotation.NonNull;

import com.urbanairship.AirshipComponent;
import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Interface used for modifying tag groups.
 */
public class TagGroupsEditor {

    /**
     * Extra containing tag groups to remove from channel tag groups or named user tags.
     */
    static final String EXTRA_REMOVE_TAG_GROUPS = "EXTRA_REMOVE_TAG_GROUPS";

    /**
     * Extra containing tag groups to add to channel tag groups or named user tags.
     */
    static final String EXTRA_ADD_TAG_GROUPS = "EXTRA_ADD_TAG_GROUPS";

    private final String action;
    protected final Map<String, Set<String>> tagsToAdd = new HashMap<>();
    protected final Map<String, Set<String>> tagsToRemove = new HashMap<>();
    private final JobDispatcher jobDispatcher;
    private final Class<? extends AirshipComponent> component;

    TagGroupsEditor(String action, Class<? extends AirshipComponent> component, JobDispatcher jobDispatcher) {
        this.action = action;
        this.jobDispatcher = jobDispatcher;
        this.component = component;
    }

    /**
     * Add a tag to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tag The tag string.
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor addTag(@NonNull String tagGroup, @NonNull String tag) {
        return addTags(tagGroup, new HashSet<>(Arrays.asList(tag)));
    }

    /**
     * Add a set of tags to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The TagGroupsEditor
     */
    public TagGroupsEditor addTags(@NonNull String tagGroup, @NonNull Set<String> tags) {
        if (!isValid(tagGroup, tags)) {
            return this;
        }
        updateTags(tagsToAdd, tagsToRemove, tagGroup, tags);
        return this;
    }

    /**
     * Remove a tag from the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tag The tag string.
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor removeTag(@NonNull String tagGroup, @NonNull String tag) {
        return removeTags(tagGroup, new HashSet<>(Arrays.asList(tag)));
    }

    /**
     * Remove a set of tags from the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor removeTags(@NonNull String tagGroup, @NonNull Set<String> tags) {
        if (!isValid(tagGroup, tags)) {
            return this;
        }
        updateTags(tagsToRemove, tagsToAdd, tagGroup, tags);
        return this;
    }

    /**
     * Apply the tag group changes.
     */
    public void apply() {
        if (tagsToAdd.isEmpty() && tagsToRemove.isEmpty()) {
            Logger.info("Skipping tag group update because tags to add and tags to remove are both empty.");
            return;
        }

        Job job = Job.newBuilder(action)
                     .setAirshipComponent(component)
                     .putExtra(EXTRA_ADD_TAG_GROUPS, convertToBundle(tagsToAdd))
                     .putExtra(EXTRA_REMOVE_TAG_GROUPS, convertToBundle(tagsToRemove))
                     .build();

        jobDispatcher.dispatch(job);
    }

    /**
     * Convert map to bundle.
     *
     * @param map The map to convert.
     * @return The bundle.
     */
    Bundle convertToBundle(@NonNull Map<String, Set<String>> map) {
        Bundle tagsBundle = new Bundle();
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            tagsBundle.putStringArrayList(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return tagsBundle;
    }

    /**
     * Check for valid values.
     *
     * @param tagGroup The tag group string.
     * @param tags The set of tags.
     */
    boolean isValid(String tagGroup, Set<String> tags) {
        boolean valid = true;
        if (UAStringUtil.isEmpty(tagGroup)) {
            Logger.warn("The tag group ID string cannot be null.");
            valid = false;
        }

        Set<String> normalizedTags = TagUtils.normalizeTags(tags);
        if (normalizedTags.isEmpty()) {
            Logger.warn("The tags cannot be empty");
            valid = false;
        }
        return valid;
    }

    /**
     * Update tagsToAdd and tagsToRemove.
     *
     * @param tagsToAdd The tags to be added to.
     * @param tagsToRemove The tags to be removed from.
     * @param tagGroup The tag group string.
     * @param tags The set of tags.
     */
    void updateTags(Map<String, Set<String>> tagsToAdd,
                    Map<String, Set<String>> tagsToRemove,
                    String tagGroup,
                    Set<String> tags) {

        Set<String> normalizedTags = TagUtils.normalizeTags(tags);

        // Check if tagsToRemove contain any tags to add.
        if (tagsToRemove.containsKey(tagGroup)) {
            tagsToRemove.get(tagGroup).removeAll(normalizedTags);
            if (tagsToRemove.get(tagGroup).size() == 0) {
                tagsToRemove.remove(tagGroup);
            }
        }

        // Combine the tags to be added with tagsToAdd.
        if (tagsToAdd.containsKey(tagGroup)) {
            tagsToAdd.get(tagGroup).addAll(normalizedTags);
            if (tagsToAdd.get(tagGroup).size() == 0) {
                tagsToAdd.remove(tagGroup);
            }
        } else {
            tagsToAdd.put(tagGroup, new HashSet<>(normalizedTags));
        }
    }
}
