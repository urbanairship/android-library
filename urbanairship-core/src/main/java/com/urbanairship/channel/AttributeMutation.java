/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * A model defining attribute mutations.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class AttributeMutation {
    private static final String ATTRIBUTE_ACTION_REMOVE = "remove";
    private static final String ATTRIBUTE_ACTION_SET = "set";

    static final String ATTRIBUTE_NAME_KEY = "key";
    static final String ATTRIBUTE_VALUE_KEY = "value";
    static final String ATTRIBUTE_ACTION_KEY = "action";
    static final String ATTRIBUTE_TIMESTAMP_KEY = "timestamp";

    private final String action;
    private final String name;
    private Object value;

    /**
     * Default attribute mutation constructor.
     */
    AttributeMutation(@NonNull String action, @NonNull String key, @Nullable Object value) {
        this.action = action;
        this.name = key;
        this.value = value;
    }

    /**
     * Creates a mutation to set a string attribute.
     *
     * @param key The string attribute key.
     * @param string The string attribute value.
     * @return The attribute mutation.
     */
    @NonNull
    static AttributeMutation newSetAttributeMutation(@NonNull String key, @NonNull String string) {
        return new AttributeMutation(ATTRIBUTE_ACTION_SET, key, string);
    }

    /**
     * Creates a mutation to remove a string attribute.
     *
     * @param key The string attribute key.
     * @return The attribute mutation.
     */
    @NonNull
    static AttributeMutation newRemoveAttributeMutation(@NonNull String key) {
        return new AttributeMutation(ATTRIBUTE_ACTION_REMOVE, key, null);
    }

    @NonNull
    JsonValue toJsonValue() {
        JsonMap.Builder builder = JsonMap.newBuilder();

        builder.put(ATTRIBUTE_ACTION_KEY, JsonValue.wrapOpt(getMutationAction()));
        builder.put(ATTRIBUTE_NAME_KEY, JsonValue.wrapOpt(getMutationName()));
        builder.putOpt(ATTRIBUTE_VALUE_KEY, JsonValue.wrapOpt(getMutationValue()));

        return builder.build().toJsonValue();
    }

    /**
     * Gets an attribute mutation's action.
     *
     * @return An attribute mutation's action.
     */
    @NonNull
    String getMutationAction() {
        return action;
    }

    /**
     * Gets an attribute mutation's name.
     *
     * @return An attribute mutation's name.
     */
    @NonNull
    String getMutationName() {
        return name;
    }

    /**
     * Gets an attribute mutation's value.
     *
     * @return An attribute mutation's value.
     */
    @Nullable
    Object getMutationValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AttributeMutation that = (AttributeMutation) o;

        if (!action.equals(that.action)) return false;
        if (!name.equals(that.name)) return false;
        return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        int result = action.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

}
