/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.push;

import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
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

    private final String action;
    protected Map<String, Set<String>> tagsToAdd = new HashMap<>();
    protected Map<String, Set<String>> tagsToRemove = new HashMap<>();

    TagGroupsEditor(String action) {
        this.action = action;
    }

    /**
     * Add a tag to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tag The tag string.
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor addTag(String tagGroup, String tag) {
        return addTags(tagGroup, new HashSet<>(Arrays.asList(tag)));
    }

    /**
     * Add a set of tags to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The TagGroupsEditor
     */
    public TagGroupsEditor addTags(String tagGroup, Set<String> tags) {
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
    public TagGroupsEditor removeTag(String tagGroup, String tag) {
        return removeTags(tagGroup, new HashSet<>(Arrays.asList(tag)));
    }

    /**
     * Remove a set of tags from the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor removeTags(String tagGroup, Set<String> tags) {
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

        Intent i = new Intent(UAirship.getApplicationContext(), PushService.class)
                .setAction(action)
                .putExtra(PushService.EXTRA_ADD_TAG_GROUPS, convertToBundle(tagsToAdd))
                .putExtra(PushService.EXTRA_REMOVE_TAG_GROUPS, convertToBundle(tagsToRemove));

        UAirship.getApplicationContext().startService(i);
    }

    /**
     * Convert map to bundle.
     *
     * @param map The map to convert.
     * @return The bundle.
     */
    Bundle convertToBundle(Map<String, Set<String>> map) {
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
