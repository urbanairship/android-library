/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

import com.urbanairship.audience.DeviceTagSelector;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.Size;
import androidx.core.util.ObjectsCompat;

/**
 * Tag selector.
 */
public class TagSelector implements JsonSerializable {

    // Not used anymore internally, but still around to avoid breaking the public API.

    /*
     * <tag_selector>   := <tag> | <not> | <and> | <or>
     * <tag>            := { "tag": string }
     * <not>            := { "not": <tag_selector> }
     * <and>            := { "and": [<tag_selector>, <tag_selector>, ...] }
     * <or>             := { "or": [<tag_selector>, <tag_selector>, ...] }
     */

    private DeviceTagSelector tagSelector;

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public TagSelector(DeviceTagSelector core) {
        this.tagSelector = core;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public DeviceTagSelector getTagSelector() {
        return tagSelector;
    }

    /**
     * Creates an AND tag selector.
     *
     * @param selectors The selectors to AND together.
     * @return The AND tag selector.
     */
    @NonNull
    public static TagSelector and(@NonNull @Size(min = 1) List<TagSelector> selectors) {
        return new TagSelector(DeviceTagSelector.Companion.and(convert(selectors)));
    }

    /**
     * Creates an AND tag selector.
     *
     * @param selectors The selectors to AND together.
     * @return The AND tag selector.
     */
    @NonNull
    public static TagSelector and(@NonNull @Size(min = 1) TagSelector... selectors) {
        return new TagSelector(DeviceTagSelector.Companion.and(convert(Arrays.asList(selectors))));
    }

    /**
     * Creates an OR tag selector.
     *
     * @param selectors The selectors to OR together.
     * @return The OR tag selector.
     */
    @NonNull
    public static TagSelector or(@NonNull @Size(min = 1) List<TagSelector> selectors) {
        return new TagSelector(DeviceTagSelector.Companion.or(convert(selectors)));
    }

    /**
     * Creates an OR tag selector.
     *
     * @param selectors The selectors to OR together.
     * @return The OR tag selector.
     */
    @NonNull
    public static TagSelector or(@NonNull @Size(min = 1) TagSelector... selectors) {
        return new TagSelector(DeviceTagSelector.Companion.or(convert(Arrays.asList(selectors))));
    }

    /**
     * Creates an NOT tag selector.
     *
     * @param selector The selectors to negate.
     * @return The NOT tag selector.
     */
    @NonNull
    public static TagSelector not(@NonNull TagSelector selector) {
        return new TagSelector(DeviceTagSelector.Companion.not(selector.tagSelector));
    }

    /**
     * Creates a tag selector that checks for tag.
     *
     * @param tag The tag.
     * @return A tag selector.
     */
    @NonNull
    public static TagSelector tag(@NonNull String tag) {
        return new TagSelector(DeviceTagSelector.Companion.tag(tag));
    }

    /**
     * Parses a json value for a tag selector.
     *
     * @param value The json value.
     * @return The parsed tag selector.
     * @throws JsonException If the json value does not contain a valid tag selector.
     */
    @NonNull
    public static TagSelector fromJson(@NonNull JsonValue value) throws JsonException {
        return new TagSelector(DeviceTagSelector.Companion.fromJson(value));
    }

    private static List<DeviceTagSelector> convert(List<TagSelector> input) {
        ArrayList<DeviceTagSelector> result = new ArrayList<>();
        for (TagSelector selector : input) {
            result.add(selector.tagSelector);
        }
        return result;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return tagSelector.toJsonValue();
    }

    /**
     * Applies the tag selector to a collection of tags.
     *
     * @param tags The collection of tags.
     * @return {@code true} if the tag selector matches the tags, otherwise {@code false}.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public boolean apply(@NonNull Collection<String> tags) {
        return tagSelector.apply(tags);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TagSelector that = (TagSelector) o;
        return that.tagSelector.equals(tagSelector);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(tagSelector);
    }

    @NonNull
    @Override
    public String toString() {
        return toJsonValue().toString();
    }
}
