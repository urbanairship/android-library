/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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

import java.util.HashSet;
import java.util.Set;

/**
 * Channel tag editor. See {@link PushManager#editTags()}.
 */
public abstract class TagEditor {

    private boolean clear = false;
    private Set<String> tagsToAdd = new HashSet<>();
    private Set<String> tagsToRemove = new HashSet<>();

    TagEditor() {}

    /**
     * Adds a tag.
     *
     * @param tag Tag to add.
     * @return The TagEditor instance.
     */
    public TagEditor addTag(String tag) {
        tagsToRemove.remove(tag);
        tagsToAdd.add(tag);
        return this;
    }

    /**
     * Adds tags.
     *
     * @param tags Tags to add.
     * @return The TagEditor instance.
     */
    public TagEditor addTags(Set<String> tags) {
        tagsToRemove.removeAll(tags);
        tagsToAdd.addAll(tags);

        return this;
    }

    /**
     * Removes a tag.
     *
     * @param tag Tag to remove.
     * @return The TagEditor instance.
     */
    public TagEditor removeTag(String tag) {
        tagsToAdd.remove(tag);
        tagsToRemove.add(tag);

        return this;
    }

    /**
     * Removes tags.
     *
     * @param tags Tags to remove.
     * @return The TagEditor instance.
     */
    public TagEditor removeTags(Set<String> tags) {
        tagsToAdd.removeAll(tags);
        tagsToRemove.addAll(tags);

        return this;
    }

    /**
     * Clears all tags.
     * <p/>
     * Tags will be cleared first during apply, then the other
     * operations will be applied.
     *
     * @return The TagEditor instance.
     */
    public TagEditor clear() {
        clear = true;

        return this;
    }

    /**
     * Applies the tag changes.
     */
    public void apply() {
        onApply(clear, tagsToAdd, tagsToRemove);
    }

    /**
     * Called when apply is called.
     *
     * @param clear {@code true} to clear all tags, otherwise {@code false}.
     * @param tagsToAdd Tags to add.
     * @param tagsToRemove Tags to remove.
     */
    abstract void onApply(boolean clear, Set<String> tagsToAdd, Set<String> tagsToRemove);
}
