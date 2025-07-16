/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.annotation.RestrictTo
import com.urbanairship.UALog

/**
 * Interface used for modifying tag groups.
 */
public open class TagGroupsEditor
/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor() {

    private val mutations = mutableListOf<TagGroupsMutation>()

    /**
     * Add a tag to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tag The tag string.
     * @return The [TagGroupsEditor].
     */
    public fun addTag(tagGroup: String, tag: String): TagGroupsEditor {
        return addTags(tagGroup, setOf(tag))
    }

    /**
     * Add a set of tags to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The [TagGroupsEditor]
     */
    public fun addTags(tagGroup: String, tags: Set<String>): TagGroupsEditor {
        val trimmed = tagGroup.trim { it <= ' ' }
        if (trimmed.isEmpty()) {
            UALog.e("The tag group ID string cannot be null.")
            return this
        }

        if (!allowTagGroupChange(trimmed)) {
            return this
        }

        val normalizedTags = TagUtils.normalizeTags(tags)
        if (normalizedTags.isEmpty()) {
            return this
        }

        mutations.add(TagGroupsMutation.newAddTagsMutation(trimmed, normalizedTags))
        return this
    }

    /**
     * Set a tag to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tag The tag string.
     * @return The [TagGroupsEditor].
     */
    public fun setTag(tagGroup: String, tag: String): TagGroupsEditor {
        return setTags(tagGroup, setOf(tag))
    }

    /**
     * Set a set of tags to the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The [TagGroupsEditor]
     */
    public fun setTags(tagGroup: String, tags: Set<String>?): TagGroupsEditor {
        val trimmedGroup = tagGroup.trim { it <= ' ' }
        if (trimmedGroup.isEmpty()) {
            UALog.e("The tag group ID string cannot be null.")
            return this
        }

        if (!allowTagGroupChange(trimmedGroup)) {
            return this
        }

        val normalizedTags = tags?.let(TagUtils::normalizeTags) ?: emptySet()

        mutations.add(TagGroupsMutation.newSetTagsMutation(trimmedGroup, normalizedTags))
        return this
    }

    /**
     * Remove a tag from the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tag The tag string.
     * @return The [TagGroupsEditor].
     */
    public fun removeTag(tagGroup: String, tag: String): TagGroupsEditor {
        return removeTags(tagGroup, setOf((tag)))
    }

    /**
     * Remove a set of tags from the tag group.
     *
     * @param tagGroup The tag group string.
     * @param tags The tags set.
     * @return The [TagGroupsEditor].
     */
    public fun removeTags(tagGroup: String, tags: Set<String>): TagGroupsEditor {
        val trimmedGroup = tagGroup.trim { it <= ' ' }
        if (trimmedGroup.isEmpty()) {
            UALog.e("The tag group ID string cannot be null.")
            return this
        }

        if (!allowTagGroupChange(trimmedGroup)) {
            return this
        }

        val normalizedTags = TagUtils.normalizeTags(tags)
        if (normalizedTags.isEmpty()) {
            return this
        }

        mutations.add(TagGroupsMutation.newRemoveTagsMutation(trimmedGroup, normalizedTags))
        return this
    }

    /**
     * Apply the tag group changes.
     */
    public fun apply() {
        val collapsedMutations = TagGroupsMutation.collapseMutations(mutations)
        onApply(collapsedMutations)
    }

    protected open fun allowTagGroupChange(tagGroup: String): Boolean {
        return true
    }

    protected open fun onApply(collapsedMutations: List<TagGroupsMutation>) { }
}
