/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.support.annotation.StringDef;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Tag selector.
 */
public class TagSelector implements JsonSerializable {

    /*
     * <tag_selector>   := <tag> | <not> | <and> | <or>
     * <tag>            := { "tag": string }
     * <not>            := { "not": <tag_selector> }
     * <and>            := { "and": [<tag_selector>, <tag_selector>, ...] }
     * <or>             := { "or": [<tag_selector>, <tag_selector>, ...] }
     */

    @StringDef({ OR, AND, NOT, TAG })
    @Retention(RetentionPolicy.SOURCE)
    private @interface Type {}
    private static final String OR = "or";
    private static final String AND = "and";
    private static final String NOT = "not";
    private static final String TAG = "tag";

    private String type;
    private String tag;
    private List<TagSelector> selectors;

    /**
     * Creates a tag selector that matches a single tag.
     *
     * @param tag The tag.
     */
    private TagSelector(String tag) {
        this.type = TAG;
        this.tag = tag;
    }

    /**
     * Creates a complex tag selector.
     *
     * @param type The selector type.
     * @param selectors The sub tag selectors.
     */
    private TagSelector(@Type @NonNull String type, @NonNull @Size(min = 1) List<TagSelector> selectors) {
        this.type = type;
        this.selectors = new ArrayList<>(selectors);
    }

    /**
     * Creates an AND tag selector.
     *
     * @param selectors The selectors to AND together.
     * @return The AND tag selector.
     */
    public static TagSelector and(@NonNull @Size(min = 1) List<TagSelector> selectors) {
        return new TagSelector(AND, selectors);
    }

    /**
     * Creates an AND tag selector.
     *
     * @param selectors The selectors to AND together.
     * @return The AND tag selector.
     */
    public static TagSelector and(@Size(min = 1) TagSelector... selectors) {
        return new TagSelector(AND, Arrays.asList(selectors));
    }


    /**
     * Creates an OR tag selector.
     *
     * @param selectors The selectors to OR together.
     * @return The OR tag selector.
     */
    public static TagSelector or(@NonNull @Size(min = 1) List<TagSelector> selectors) {
        return new TagSelector(OR, selectors);
    }

    /**
     * Creates an OR tag selector.
     *
     * @param selectors The selectors to OR together.
     * @return The OR tag selector.
     */
    public static TagSelector or(@Size(min = 1) TagSelector... selectors) {
        return new TagSelector(OR,  Arrays.asList(selectors));
    }

    /**
     * Creates an NOT tag selector.
     *
     * @param selector The selectors to negate.
     * @return The NOT tag selector.
     */
    public static TagSelector not(@NonNull TagSelector selector) {
        return new TagSelector(NOT, Collections.singletonList(selector));
    }

    /**
     * Creates a tag selector that checks for tag.
     *
     * @param tag The tag.
     * @return A tag selector.
     */
    public static TagSelector tag(String tag) {
        return new TagSelector(tag);
    }

    /**
     * Parses a json value for a tag selector.
     *
     * @param jsonValue The json value.
     * @return The parsed tag selector.
     * @throws JsonException If the json value does not contain a valid tag selector.
     */
    public static TagSelector parseJson(JsonValue jsonValue) throws JsonException {
        JsonMap jsonMap = jsonValue.optMap();

        if (jsonMap.containsKey(TAG)) {
            String tag = jsonMap.opt(TAG).getString();
            if (tag == null) {
                throw new JsonException("Tag selector expected a tag: " + jsonMap.opt(TAG));
            }
            return tag(tag);
        }


        if (jsonMap.containsKey(OR)) {
            JsonList selectors = jsonMap.opt(OR).getList();
            if (selectors == null) {
                throw new JsonException("OR selector expected array of tag selectors: " + jsonMap.opt(OR));
            }

            return or(parseSelectors(selectors));
        }

        if (jsonMap.containsKey(AND)) {
            JsonList selectors = jsonMap.opt(AND).getList();
            if (selectors == null) {
                throw new JsonException("AND selector expected array of tag selectors: " + jsonMap.opt(AND));
            }

            return and(parseSelectors(selectors));
        }

        if (jsonMap.containsKey(NOT)) {
            JsonValue selector = jsonMap.opt(NOT);
            if (selector == null) {
                throw new JsonException("NOT selector expected single tag selector of selectors: " + jsonMap.opt(NOT));
            }

            return not(parseJson(selector));
        }

        throw new JsonException("Json value did not contain a valid selector: " + jsonValue);
    }

    /**
     * Helper method to parse a list of selectors.
     *
     * @param jsonList The json list.
     * @return A list of tag selectors.
     * @throws JsonException If the json contains an invalid selector.
     */
    private static List<TagSelector> parseSelectors(JsonList jsonList) throws JsonException {
        List<TagSelector> selectors = new ArrayList<>();
        for (JsonValue jsonValue : jsonList) {
            selectors.add(parseJson(jsonValue));
        }

        if (selectors.isEmpty()) {
            throw new JsonException("Expected 1 or more selectors");
        }

        return selectors;
    }

    @Override
    public JsonValue toJsonValue() {
        JsonMap.Builder builder = JsonMap.newBuilder();

        switch (type) {
            case TAG:
                builder.put(type, tag);
                break;

            case NOT:
                builder.put(type, selectors.get(0));
                break;

            case OR:
            case AND:
            default:
                builder.put(type, JsonValue.wrapOpt(selectors));
                break;
        }

        return builder.build().toJsonValue();
    }

    /**
     * Applies the tag selector to a collection of tags.
     *
     * @param tags The collection of tags.
     * @return {@code true} if the tag selector matches the tags, otherwise {@code false}.
     */
    public boolean apply(@NonNull Collection<String> tags) {
        switch (type) {
            case TAG:
                return tags.contains(tag);

            case NOT:
                return !selectors.get(0).apply(tags);

            case AND:
                for (TagSelector selector : selectors) {
                    if (!selector.apply(tags)) {
                        return false;
                    }
                }

                return true;

            case OR:
            default:
                for (TagSelector selector : selectors) {
                    if (selector.apply(tags)) {
                        return true;
                    }
                }

                return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TagSelector selector = (TagSelector) o;

        if (type != null ? !type.equals(selector.type) : selector.type != null) {
            return false;
        }
        if (tag != null ? !tag.equals(selector.tag) : selector.tag != null) {
            return false;
        }
        return selectors != null ? selectors.equals(selector.selectors) : selector.selectors == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (tag != null ? tag.hashCode() : 0);
        result = 31 * result + (selectors != null ? selectors.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return toJsonValue().toString();
    }
}
