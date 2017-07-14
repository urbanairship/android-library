/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.Logger;
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


    private final List<TagGroupsMutation> mutations = new ArrayList<>();

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public TagGroupsEditor() {

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

        if (!allowTagGroupChange(tagGroup)) {
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

        if (!allowTagGroupChange(tagGroup)) {
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

        if (!allowTagGroupChange(tagGroup)) {
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
        onApply(collapsedMutations);
    }

    protected boolean allowTagGroupChange(String tagGroup) {
        return true;
    }

    protected void onApply(List<TagGroupsMutation> collapsedMutations) {};

}
