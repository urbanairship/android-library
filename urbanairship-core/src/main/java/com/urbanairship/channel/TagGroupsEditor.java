/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

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
    @NonNull
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
    @NonNull
    public TagGroupsEditor addTags(@NonNull String tagGroup, @NonNull Set<String> tags) {
        tagGroup = tagGroup.trim();
        if (UAStringUtil.isEmpty(tagGroup)) {
            Logger.error("The tag group ID string cannot be null.");
            return this;
        }

        if (!allowTagGroupChange(tagGroup)) {
            return this;
        }

        tags = TagUtils.normalizeTags(tags);
        if (tags.isEmpty()) {
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
    @NonNull
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
    @NonNull
    public TagGroupsEditor setTags(@NonNull String tagGroup, @Nullable Set<String> tags) {
        tagGroup = tagGroup.trim();
        if (UAStringUtil.isEmpty(tagGroup)) {
            Logger.error("The tag group ID string cannot be null.");
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
    @NonNull
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
    @NonNull
    public TagGroupsEditor removeTags(@NonNull String tagGroup, @NonNull Set<String> tags) {
        tagGroup = tagGroup.trim();
        if (UAStringUtil.isEmpty(tagGroup)) {
            Logger.error("The tag group ID string cannot be null.");
            return this;
        }

        if (!allowTagGroupChange(tagGroup)) {
            return this;
        }

        tags = TagUtils.normalizeTags(tags);
        if (tags.isEmpty()) {
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

    protected boolean allowTagGroupChange(@NonNull String tagGroup) {
        return true;
    }

    protected void onApply(@NonNull List<TagGroupsMutation> collapsedMutations) {
    }

}
