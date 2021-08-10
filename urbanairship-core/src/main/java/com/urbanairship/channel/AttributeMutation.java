/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A model defining attribute mutations.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AttributeMutation implements JsonSerializable {

    public static final String ATTRIBUTE_ACTION_REMOVE = "remove";
    public static final String ATTRIBUTE_ACTION_SET = "set";

    static final String ATTRIBUTE_NAME_KEY = "key";
    static final String ATTRIBUTE_VALUE_KEY = "value";
    static final String ATTRIBUTE_ACTION_KEY = "action";
    static final String ATTRIBUTE_TIMESTAMP_KEY = "timestamp";

    public final String action;
    public final String name;
    public final JsonValue value;
    public final String timestamp;

    /**
     * Default attribute mutation constructor.
     */
    AttributeMutation(@NonNull String action,
                      @NonNull String key,
                      @Nullable JsonValue value,
                      @Nullable String timestamp) {
        this.action = action;
        this.name = key;
        this.value = value;
        this.timestamp = timestamp;
    }

    /**
     * Creates a mutation to set a string attribute.
     *
     * @param key The string attribute key.
     * @param jsonValue The json value.
     * @param timestamp The timestamp in milliseconds.
     * @return The attribute mutation.
     */
    @NonNull
    public static AttributeMutation newSetAttributeMutation(@NonNull String key, @NonNull JsonValue jsonValue, long timestamp) {
        if (jsonValue.isNull() || jsonValue.isJsonList() || jsonValue.isJsonMap() || jsonValue.isBoolean()) {
            throw new IllegalArgumentException("Invalid attribute value: " + jsonValue);
        }

        return new AttributeMutation(ATTRIBUTE_ACTION_SET, key, jsonValue, DateUtils.createIso8601TimeStamp(timestamp));
    }

    /**
     * Creates a mutation to remove a string attribute.
     *
     * @param key The string attribute key.
     * @param timestamp The timestamp in milliseconds.
     * @return The attribute mutation.
     */
    @NonNull
    public static AttributeMutation newRemoveAttributeMutation(@NonNull String key, long timestamp) {
        return new AttributeMutation(ATTRIBUTE_ACTION_REMOVE, key, null, DateUtils.createIso8601TimeStamp(timestamp));
    }

    @NonNull
    static AttributeMutation fromJsonValue(@NonNull JsonValue jsonValue) throws JsonException {
        JsonMap mutation = jsonValue.optMap();

        String action = mutation.opt(ATTRIBUTE_ACTION_KEY).getString();
        String name = mutation.opt(ATTRIBUTE_NAME_KEY).getString();
        JsonValue value = mutation.get(ATTRIBUTE_VALUE_KEY);
        String timestamp = mutation.opt(ATTRIBUTE_TIMESTAMP_KEY).getString();

        if (action == null || name == null || (value != null && !isValidValue(value))) {
            throw new JsonException("Invalid attribute mutation: " + mutation);
        }

        return new AttributeMutation(action, name, value, timestamp);
    }

    @NonNull
    public static List<AttributeMutation> fromJsonList(@NonNull JsonList jsonList) {
        List<AttributeMutation> mutations = new ArrayList<>();

        for (JsonValue value : jsonList) {
            try {
                mutations.add(fromJsonValue(value));
            } catch (JsonException e) {
                Logger.error(e, "Invalid attribute.");
            }
        }

        return mutations;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(ATTRIBUTE_ACTION_KEY, action)
                      .put(ATTRIBUTE_NAME_KEY, name)
                      .put(ATTRIBUTE_VALUE_KEY, value)
                      .put(ATTRIBUTE_TIMESTAMP_KEY, timestamp)
                      .build()
                      .toJsonValue();
    }

    /**
     * Collapses a collection of mutation payloads to a single mutation payload.
     *
     * @param mutations a list of attribute mutation instances to collapse.
     * @return An attribute mutations instance.
     */
    @NonNull
    public static List<AttributeMutation> collapseMutations(@NonNull List<AttributeMutation> mutations) {
        List<AttributeMutation> result = new ArrayList<>();

        // Reverse the mutations payloads
        List<AttributeMutation> reversed = new ArrayList<>(mutations);
        Collections.reverse(reversed);

        Set<String> mutationNames = new HashSet<>();

        for (AttributeMutation mutation : reversed) {
            if (!mutationNames.contains(mutation.name)) {
                result.add(0, mutation);
                mutationNames.add(mutation.name);
            }
        }

        return result;
    }

    private static boolean isValidValue(@NonNull JsonValue jsonValue) {
        return !(jsonValue.isNull() || jsonValue.isJsonList() || jsonValue.isJsonMap() || jsonValue.isBoolean());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AttributeMutation mutation = (AttributeMutation) o;

        if (!action.equals(mutation.action)) return false;
        if (!name.equals(mutation.name)) return false;
        if (value != null ? !value.equals(mutation.value) : mutation.value != null) return false;
        return timestamp.equals(mutation.timestamp);
    }

    @Override
    public int hashCode() {
        int result = action.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + timestamp.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "AttributeMutation{" +
                "action='" + action + '\'' +
                ", name='" + name + '\'' +
                ", value=" + value +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }

}
