/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.annotation.RestrictTo

/**
 * Channel tag editor. See [AirshipChannel.editTags].
 */
public abstract class TagEditor
/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) protected constructor() {

    private var clear = false
    private val tagsToAdd = mutableSetOf<String>()
    private val tagsToRemove = mutableSetOf<String>()

    /**
     * Adds a tag.
     *
     * @param tag Tag to add.
     * @return The [TagEditor] instance.
     */
    public fun addTag(tag: String): TagEditor {
        return this.also {
            it.tagsToRemove.remove(tag)
            it.tagsToAdd.add(tag)
        }
    }

    /**
     * Adds tags.
     *
     * @param tags Tags to add.
     * @return The [TagEditor] instance.
     */
    public fun addTags(tags: Set<String>): TagEditor {
        return this.also {
            it.tagsToRemove.removeAll(tags)
            it.tagsToAdd.addAll(tags)
        }
    }

    /**
     * Removes a tag.
     *
     * @param tag Tag to remove.
     * @return The [TagEditor] instance.
     */
    public fun removeTag(tag: String): TagEditor {
        return this.also {
            it.tagsToAdd.remove(tag)
            it.tagsToRemove.add(tag)
        }
    }

    /**
     * Removes tags.
     *
     * @param tags Tags to remove.
     * @return The [TagEditor] instance.
     */
    public fun removeTags(tags: Set<String>): TagEditor {
        return this.also {
            it.tagsToAdd.removeAll(tags)
            it.tagsToRemove.addAll(tags)
        }
    }

    /**
     * Clears all tags.
     *
     *
     * Tags will be cleared first during apply, then the other
     * operations will be applied.
     *
     * @return The [TagEditor] instance.
     */
    public fun clear(): TagEditor {
        return this.also { it.clear = true }
    }

    /**
     * Applies the tag changes.
     */
    public fun apply() {
        onApply(clear, tagsToAdd, tagsToRemove)
    }

    /**
     * Called when apply is called.
     *
     * @param clear `true` to clear all tags, otherwise `false`.
     * @param tagsToAdd Tags to add.
     * @param tagsToRemove Tags to remove.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected abstract fun onApply(
        clear: Boolean, tagsToAdd: Set<String>, tagsToRemove: Set<String>
    )
}
