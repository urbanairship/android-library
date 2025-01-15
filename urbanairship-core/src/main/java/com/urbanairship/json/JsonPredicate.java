/* Copyright Airship and Contributors */

package com.urbanairship.json;

import com.urbanairship.Predicate;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

/**
 * Class abstracting a JSON predicate. The predicate is contained to the following schema:
 * <p>
 * <predicate>         := <json_matcher> | <not> | <and> | <or>
 * <not>               := { "not": { <predicate> } }
 * <and>               := { "and": [<predicate>, <predicate>, …] }
 * <or>                := { "or": [<predicate>, <predicate>, …] }
 * <p>
 * <json_matcher>      := { <selector>, "value": { <value_matcher> }} | { "value": {<value_matcher>}}
 * <selector>          := <scope>, "key": string | "key": string | <scope>
 * <scope>             := "scope": string | "scope": [string, string, …]
 * <p>
 * <value_matcher>     := <numeric_matcher> | <equals_matcher> | <presence_matcher> | <version_matcher> | <array_matcher>
 * <array_matcher>     := "array_contains": <predicate> | "array_contains": <predicate>, "index": number
 * <numeric_matcher>   := "at_least": number | "at_most": number | "at_least": number, "at_most": number
 * <equals_matcher>    := "equals": number | string | boolean | object | array
 * <presence_matcher>  := "is_present": boolean
 * <version_matcher>   := "version_matches": version matcher
 */
public class JsonPredicate implements JsonSerializable, Predicate<JsonSerializable> {

    @NonNull
    public static final String OR_PREDICATE_TYPE = "or";

    @NonNull
    public static final String AND_PREDICATE_TYPE = "and";

    @NonNull
    public static final String NOT_PREDICATE_TYPE = "not";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({ OR_PREDICATE_TYPE, AND_PREDICATE_TYPE, NOT_PREDICATE_TYPE })
    public @interface PredicateType {}

    private final List<Predicate<JsonSerializable>> items;
    private final String type;

    private JsonPredicate(Builder builder) {
        this.items = builder.items;
        this.type = builder.type;
    }

    /**
     * Builder factory method.
     *
     * @return A new builder instance.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder().put(type, JsonValue.wrapOpt(items))
                      .build()
                      .toJsonValue();
    }

    /**
     * Parses a JsonValue object into a JsonPredicate.
     *
     * @param jsonValue The predicate as a JsonValue.
     * @return The parsed JsonPredicate.
     * @throws JsonException If the jsonValue defines invalid JsonPredicate.
     */
    @NonNull
    public static JsonPredicate parse(@Nullable JsonValue jsonValue) throws JsonException {
        if (jsonValue == null || !jsonValue.isJsonMap() || jsonValue.optMap().isEmpty()) {
            throw new JsonException("Unable to parse empty JsonValue: " + jsonValue);
        }

        JsonMap map = jsonValue.optMap();

        JsonPredicate.Builder builder = JsonPredicate.newBuilder();

        String type = getPredicateType(map);
        if (type != null) {

            builder.setPredicateType(type);

            JsonValue subpredicatesList = map.opt(type);
            JsonList subpredicates = subpredicatesList.optList();

            if (NOT_PREDICATE_TYPE.equals(type) && subpredicatesList.isJsonMap()) {
                subpredicates = new JsonList(Collections.singletonList(subpredicatesList.optMap().toJsonValue()));
            }

            for (JsonValue child : subpredicates) {
                if (!child.isJsonMap()) {
                    continue;
                }

                // If the child contains a predicate type then its predicate
                if (getPredicateType(child.optMap()) != null) {
                    builder.addPredicate(parse(child));
                    continue;
                }

                // Otherwise its a matcher
                builder.addMatcher(JsonMatcher.parse(child));
            }
        } else {
            builder.addMatcher(JsonMatcher.parse(jsonValue));
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Unable to parse JsonPredicate.", e);
        }
    }

    @PredicateType
    @Nullable
    private static String getPredicateType(@NonNull JsonMap jsonMap) {
        if (jsonMap.containsKey(AND_PREDICATE_TYPE)) {
            return AND_PREDICATE_TYPE;
        }

        if (jsonMap.containsKey(OR_PREDICATE_TYPE)) {
            return OR_PREDICATE_TYPE;
        }

        if (jsonMap.containsKey(NOT_PREDICATE_TYPE)) {
            return NOT_PREDICATE_TYPE;
        }

        return null;
    }

    @Override
    public boolean apply(@Nullable JsonSerializable value) {
        if (items.size() == 0) {
            return true;
        }

        switch (type) {
            case NOT_PREDICATE_TYPE:
                return !items.get(0).apply(value);

            case AND_PREDICATE_TYPE:
                for (Predicate<JsonSerializable> item : items) {
                    if (!item.apply(value)) {
                        return false;
                    }
                }

                return true;

            case OR_PREDICATE_TYPE:
            default:
                for (Predicate<JsonSerializable> item : items) {
                    if (item.apply(value)) {
                        return true;
                    }
                }

                return false;
        }

    }

    /**
     * Builder class.
     */
    public static class Builder {

        private String type = OR_PREDICATE_TYPE;
        private final List<Predicate<JsonSerializable>> items = new ArrayList<>();

        /**
         * Sets the predicate type. If type NOT, only one matcher or predicate is
         * allowed to be added.
         *
         * @param type The predicate type.
         * @return The builder instance.
         */
        @NonNull
        public Builder setPredicateType(@NonNull @PredicateType String type) {
            this.type = type;
            return this;
        }

        /**
         * Adds a JsonMatcher.
         *
         * @param matcher The JsonMatcher instance.
         * @return The builder instance.
         */
        @NonNull
        public Builder addMatcher(@NonNull JsonMatcher matcher) {
            items.add(matcher);
            return this;
        }

        /**
         * Adds a JsonPredicate.
         *
         * @param predicate The JsonPredicate instance.
         * @return The builder instance.
         */
        @NonNull
        public Builder addPredicate(@NonNull JsonPredicate predicate) {
            items.add(predicate);
            return this;
        }

        /**
         * Builds the JsonPredicate instance.
         *
         * @return The JsonPredicate instance.
         * @throws IllegalArgumentException if a NOT predicate has more than one matcher or predicate
         * defined, or if the predicate does not contain at least 1 child predicate or matcher.
         */
        @NonNull
        public JsonPredicate build() {
            if (type.equals(NOT_PREDICATE_TYPE) && items.size() > 1) {
                throw new IllegalArgumentException("`NOT` predicate type only supports a single matcher or predicate.");
            }

            if (items.isEmpty()) {
                throw new IllegalArgumentException("Predicate must contain at least 1 matcher or child predicate.");
            }

            return new JsonPredicate(this);
        }

    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JsonPredicate that = (JsonPredicate) o;

        if (items != null ? !items.equals(that.items) : that.items != null) {
            return false;
        }
        return type != null ? type.equals(that.type) : that.type == null;

    }

    @Override
    public int hashCode() {
        int result = items != null ? items.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

}
