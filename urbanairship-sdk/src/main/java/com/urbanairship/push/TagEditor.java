/* Copyright 2017 Urban Airship and Contributors */

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
