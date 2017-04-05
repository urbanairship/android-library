/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import com.urbanairship.Predicate;

import java.util.ArrayList;
import java.util.List;

/**
 * Class representing the leaf node of a JsonPredicate that contains the relevant field matching info.
 */
public class JsonMatcher implements JsonSerializable, Predicate<JsonSerializable> {

    private static final String VALUE_KEY = "value";
    private static final String FIELD_KEY = "key";
    private static final String SCOPE_KEY = "scope";

    private final String key;
    private final List<String> scopeList;
    private final ValueMatcher value;

    private JsonMatcher(Builder builder) {
        this.key = builder.key;
        this.scopeList = builder.scope;
        this.value = builder.valueMatcher == null ? ValueMatcher.newIsPresentMatcher() : builder.valueMatcher;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                .putOpt(FIELD_KEY, key)
                .putOpt(SCOPE_KEY, scopeList)
                .put(VALUE_KEY, value)
                .build()
                .toJsonValue();
    }

    @Override
    public boolean apply(JsonSerializable jsonSerializable) {
        JsonValue jsonValue = jsonSerializable == null ? JsonValue.NULL : jsonSerializable.toJsonValue();
        if (jsonValue == null) {
            jsonValue = JsonValue.NULL;
        }

        for (String scope : scopeList) {
            jsonValue = jsonValue.optMap().opt(scope);
            if (jsonValue.isNull()) {
                break;
            }
        }

        if (key != null) {
            jsonValue = jsonValue.optMap().opt(key);
        }

        return value.apply(jsonValue);
    }

    /**
     * Parses a JsonValue object into a JsonMatcher.
     *
     * @param jsonValue The predicate as a JsonValue.
     * @return The parsed JsonMatcher.
     * @throws JsonException If the JSON is invalid.
     */
    public static JsonMatcher parse(JsonValue jsonValue) throws JsonException {
        if (jsonValue == null || !jsonValue.isJsonMap() || jsonValue.optMap().isEmpty()) {
            throw new JsonException("Unable to parse empty JsonValue: " + jsonValue);
        }

        JsonMap map = jsonValue.optMap();

        if (!map.containsKey(VALUE_KEY)) {
            throw new JsonException("JsonMatcher must contain a value matcher.");
        }

        JsonMatcher.Builder builder = JsonMatcher.newBuilder()
                .setKey(map.opt(FIELD_KEY).getString(null))
                .setValueMatcher(ValueMatcher.parse(map.get(VALUE_KEY)));

        JsonValue scope = map.opt(SCOPE_KEY);
        if (scope.isString()) {
            builder.setScope(scope.getString());
        } else if (scope.isJsonList()) {
            for (JsonValue field : scope.optList().getList()) {
                builder.setScope(field.getString());
            }
        }

        return builder.build();
    }

    /**
     * Builder factory method.
     *
     * @return A new builder instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder class.
     */
    public static class Builder {

        private ValueMatcher valueMatcher;
        private List<String> scope = new ArrayList<>(1);
        private String key;

        private Builder() {

        }

        /**
         * Sets the ValueMatcher.
         *
         * @param valueMatcher The ValueMatcher instance.
         * @return The Builder instance.
         */
        public Builder setValueMatcher(ValueMatcher valueMatcher) {
            this.valueMatcher = valueMatcher;
            return this;
        }

        /**
         * Sets the scope.
         *
         * @param scope The scope as a list of fields.
         * @return The Builder instance.
         */
        public Builder setScope(List<String> scope) {
            this.scope = new ArrayList<>();
            this.scope.addAll(scope);
            return this;
        }

        /**
         * Sets the scope.
         *
         * @param scope The scope as a single field.
         * @return The Builder instance.
         */
        public Builder setScope(String scope) {
            this.scope = new ArrayList<>();
            this.scope.add(scope);
            return this;
        }

        /**
         * Sets the key.
         *
         * @param key The key.
         * @return The Builder instance.
         */
        public Builder setKey(String key) {
            this.key = key;
            return this;
        }

        /**
         * Builds the JsonMatcher instance.
         *
         * @return The JsonMatcher instance.
         */
        public JsonMatcher build() {
            return new JsonMatcher(this);
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

        JsonMatcher matcher = (JsonMatcher) o;

        if (key != null ? !key.equals(matcher.key) : matcher.key != null) {
            return false;
        }
        if (scopeList != null ? !scopeList.equals(matcher.scopeList) : matcher.scopeList != null) {
            return false;
        }
        return value != null ? value.equals(matcher.value) : matcher.value == null;

    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (scopeList != null ? scopeList.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}




