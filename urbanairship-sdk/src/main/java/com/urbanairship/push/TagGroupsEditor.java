/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interface used for modifying tag groups.
 */
public class TagGroupsEditor {

    /**
     * Extra containing tag group mutations
     */
    static final String EXTRA_TAG_GROUP_MUTATIONS = "EXTRA_TAG_GROUP_MUTATIONS";

    private final String action;
    private final List<TagGroupsMutation> mutations = new ArrayList<>();
    private final JobDispatcher jobDispatcher;
    private final Class<? extends AirshipComponent> component;

    public TagGroupsEditor(String action, Class<? extends AirshipComponent> component, JobDispatcher jobDispatcher) {
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
        return addTags(tagGroup, Collections.singleton(tag));
    }

    /**
     * Add a set of tags to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The TagGroupsEditor
     */
    public TagGroupsEditor addTags(@NonNull String tagGroup, @NonNull Set<String> tags) {
        tagGroup = tagGroup.trim();
        if (UAStringUtil.isEmpty(tagGroup)) {
            Logger.warn("The tag group ID string cannot be null.");
            return this;
        }

        tags = TagUtils.normalizeTags(tags);
        if (tags.isEmpty()) {
            Logger.warn("The tags cannot be empty");
            return this;
        }

        mutations.add(TagGroupsMutation.newAddTagsMutation(tagGroup, tags));
        return this;
    }

    /**
     * Set a tag to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tag The tag string.
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor setTag(@NonNull String tagGroup, @NonNull String tag) {
        return setTags(tagGroup, Collections.singleton(tag));
    }

    /**
     * Set a set of tags to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The TagGroupsEditor
     */
    public TagGroupsEditor setTags(@NonNull String tagGroup, Set<String> tags) {
        tagGroup = tagGroup.trim();
        if (UAStringUtil.isEmpty(tagGroup)) {
            Logger.warn("The tag group ID string cannot be null.");
            return this;
        }

        if (tags == null) {
            tags = new HashSet<>();
        } else {
            tags = TagUtils.normalizeTags(tags);
        }

        mutations.add(TagGroupsMutation.newSetTagsMutation(tagGroup, tags));
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
        return removeTags(tagGroup, Collections.singleton((tag)));
    }

    /**
     * Remove a set of tags from the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor removeTags(@NonNull String tagGroup, @NonNull Set<String> tags) {
        tagGroup = tagGroup.trim();
        if (UAStringUtil.isEmpty(tagGroup)) {
            Logger.warn("The tag group ID string cannot be null.");
            return this;
        }

        tags = TagUtils.normalizeTags(tags);
        if (tags.isEmpty()) {
            Logger.warn("The tags cannot be empty");
            return this;
        }

        mutations.add(TagGroupsMutation.newRemoveTagsMutation(tagGroup, tags));
        return this;
    }

    /**
     * Apply the tag group changes.
     */
    public void apply() {
        List<TagGroupsMutation> collapsedMutations = TagGroupsMutation.collapseMutations(mutations);
        if (mutations.isEmpty()) {
            return;
        }

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(action)
                                 .setAirshipComponent(component)
                                 .putExtra(EXTRA_TAG_GROUP_MUTATIONS, JsonValue.wrapOpt(collapsedMutations).toString())
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }
}
