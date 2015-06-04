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

import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Interface used for modifying tag groups.
 */
public abstract class TagGroupsEditor {

    protected Map<String, Set<String>> tagsToAdd = new HashMap<>();
    protected Map<String, Set<String>> tagsToRemove = new HashMap<>();

    TagGroupsEditor() {}

    /**
     * Add tags to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags string.
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor addTags(String tagGroup, String... tags) {
        return addTags(tagGroup, new HashSet<>(Arrays.asList(tags)));
    }

    /**
     * Add a set of tags to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The TagGroupsEditor
     */
    public TagGroupsEditor addTags(String tagGroup, Set<String> tags) {
        if (UAStringUtil.isEmpty(tagGroup)) {
            Logger.warn("The tag group ID string cannot be null.");
            return this;
        }

        if (tags.isEmpty()) {
            Logger.warn("The tags cannot be empty");
            return this;
        }

        Set<String> normalizedTags = UAStringUtil.normalizeTags(tags);

        if (tagsToRemove.containsKey(tagGroup)) {
            tagsToRemove.get(tagGroup).removeAll(normalizedTags);
            if (tagsToRemove.get(tagGroup).size() == 0) {
                tagsToRemove.remove(tagGroup);
            }
        }

        if (tagsToAdd.containsKey(tagGroup)) {
            tagsToAdd.get(tagGroup).addAll(normalizedTags);
            if (tagsToAdd.get(tagGroup).size() == 0) {
                tagsToAdd.remove(tagGroup);
            }
        }

        tagsToAdd.put(tagGroup, new HashSet<>(normalizedTags));

        return this;
    }

    /**
     * Remove tags from the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags string.
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor removeTags(String tagGroup, String... tags) {
        return removeTags(tagGroup, new HashSet<>(Arrays.asList(tags)));
    }

    /**
     * Remove a set of tags from the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor removeTags(String tagGroup, Set<String> tags) {
        if (UAStringUtil.isEmpty(tagGroup)) {
            Logger.warn("The tag group ID string cannot be null.");
            return this;
        }

        if (tags.isEmpty()) {
            Logger.warn("The tags cannot be empty");
            return this;
        }

        Set<String> normalizedTags = UAStringUtil.normalizeTags(tags);

        if (tagsToAdd.containsKey(tagGroup)) {
            tagsToAdd.get(tagGroup).removeAll(normalizedTags);
            if (tagsToAdd.get(tagGroup).size() == 0) {
                tagsToAdd.remove(tagGroup);
            }
        }

        if (tagsToRemove.containsKey(tagGroup)) {
            tagsToRemove.get(tagGroup).addAll(normalizedTags);
            if (tagsToRemove.get(tagGroup).size() == 0) {
                tagsToRemove.remove(tagGroup);
            }
        }

        tagsToRemove.put(tagGroup, new HashSet<>(normalizedTags));

        return this;
    }

    /**
     * Apply the tag group changes.
     */
    public abstract void apply();
}
