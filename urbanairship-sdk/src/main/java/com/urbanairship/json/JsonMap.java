/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;

import org.json.JSONException;
import org.json.JSONStringer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * An immutable mapping of String keys to JsonValues.
 */
public class JsonMap implements Iterable<Map.Entry<String, JsonValue>>, JsonSerializable {

    public static final JsonMap EMPTY_MAP = new JsonMap(null);

    private final Map<String, JsonValue> map;

    /**
     * Creates a JsonMap from a Map.
     *
     * @param map A map of strings to JsonValues.
     */
    public JsonMap(@Nullable Map<String, JsonValue> map) {
        this.map = map == null ? new HashMap<String, JsonValue>() : new HashMap<>(map);
    }

    /**
     * Factory method to create a new JSON map builder.
     *
     * @return A JSON map builder.
     */
    public static Builder newBuilder() {
        return new JsonMap.Builder();
    }

    /**
     * Returns whether this map contains the specified key.
     *
     * @param key the key to search for.
     * @return {@code true} if this map contains the specified key,
     * {@code false} otherwise.
     */
    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    /**
     * Returns whether this map contains the specified value.
     *
     * @param value the value to search for.
     * @return {@code true} if this map contains the specified value,
     * {@code false} otherwise.
     */
    public boolean containsValue(JsonValue value) {
        return map.containsValue(value);
    }

    /**
     * Returns a set containing all of the mappings in this map. Each mapping is
     * an instance of {@link Map.Entry}. As the set is backed by this map,
     * changes in one will be reflected in the other.
     *
     * @return a set of the mappings.
     */
    public Set<Map.Entry<String, JsonValue>> entrySet() {
        return map.entrySet();
    }

    /**
     * Returns the value of the mapping with the specified key.
     *
     * @param key the key.
     * @return the value of the mapping with the specified key, or {@code null}
     * if no mapping for the specified key is found.
     */
    public JsonValue get(String key) {
        return map.get(key);
    }

    /**
     * Returns the optional value in the map with the specified key. If the value is not in the map
     * {@link JsonValue#NULL} will be returned instead {@code null}.
     *
     * @param key the key.
     * @return the value of the mapping with the specified key, or {@link JsonValue#NULL}
     * if no mapping for the specified key is found.
     */
    public JsonValue opt(String key) {
        JsonValue value = get(key);
        if (value != null) {
            return value;
        }
        return JsonValue.NULL;
    }

    /**
     * Returns whether this map is empty.
     *
     * @return {@code true} if this map has no elements, {@code false}
     * otherwise.
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Returns a set of the keys contained in this map. The set is backed by
     * this map so changes to one are reflected by the other. The set does not
     * support adding.
     *
     * @return a set of the keys.
     */
    public Set<String> keySet() {
        return map.keySet();
    }

    /**
     * Returns the number of elements in this map.
     *
     * @return the number of elements in this map.
     */
    public int size() {
        return map.size();
    }

    /**
     * Returns a collection of the values contained in this map.
     *
     * @return a collection of the values contained in this map.
     */
    public Collection<JsonValue> values() {
        return new ArrayList<>(map.values());
    }

    /**
     * Gets the JsonMap as a Map.
     *
     * @return The JsonMap as a Map.
     */
    public Map<String, JsonValue> getMap() {
        return new HashMap<>(map);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object instanceof JsonMap) {
            return map.equals(((JsonMap) object).map);
        }

        if (object instanceof JsonValue) {
            return map.equals(((JsonValue) object).optMap().map);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    /**
     * Returns the JsonMap as a JSON encoded String.
     *
     * @return The value as a JSON encoded String.
     */
    @Override
    public String toString() {
        try {
            JSONStringer stringer = new JSONStringer();
            write(stringer);
            return stringer.toString();
        } catch (JSONException e) {
            // Should never happen
            Logger.error("JsonMap - Failed to create JSON String.", e);
            return "";
        }
    }

    /**
     * Helper method that is used to write the value as a JSON String.
     *
     * @param stringer The JSONStringer object.
     * @throws org.json.JSONException If the value is unable to be written as JSON.
     */
    void write(JSONStringer stringer) throws JSONException {
        stringer.object();
        for (Map.Entry<String, JsonValue> entry : entrySet()) {
            stringer.key(entry.getKey());
            entry.getValue().write(stringer);
        }
        stringer.endObject();
    }

    @Override
    public Iterator<Map.Entry<String, JsonValue>> iterator() {
        return entrySet().iterator();
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonValue.wrap(this);
    }

    /**
     * Builder class for {@link com.urbanairship.json.JsonMap} Objects.
     */
    public static class Builder {
        private Map<String, JsonValue> map = new HashMap<>();

        private Builder() {}

        /**
         * Add a pre-existing JSON map to the JSON map.
         *
         * @param map A JsonMap instance.
         * @return The JSON map builder.
         */
        public Builder putAll(@NonNull JsonMap map) {
            for (Map.Entry<String, JsonValue> entry : map.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }

            return this;
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a JsonSerializable.
         * @return The JSON map builder.
         */
        public Builder put(@NonNull String key, JsonSerializable value) {
            if (value == null || value.toJsonValue().isNull()) {
                map.remove(key);
            } else {
                map.put(key, value.toJsonValue());
            }

            return this;
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as an Object. If an exception is thrown while attempting to wrap
         * this object as a JsonValue, it will be swallowed and the entry will be dropped from the map.
         * @return The JSON map builder.
         */
        public Builder putOpt(@NonNull String key, Object value) {
            put(key, JsonValue.wrapOpt(value));
            return this;
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a String.
         * @return The JSON map builder.
         */
        public Builder put(@NonNull String key, String value) {
            if (value != null) {
                put(key, JsonValue.wrap(value));
            } else {
                map.remove(key);
            }

            return this;
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a boolean.
         * @return The JSON map builder.
         */
        public Builder put(@NonNull String key, boolean value) {
            return put(key, JsonValue.wrap(value));
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as an int.
         * @return The JSON map builder.
         */
        public Builder put(@NonNull String key, int value) {
            return put(key, JsonValue.wrap(value));
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a long.
         * @return The JSON map builder.
         */
        public Builder put(@NonNull String key, long value) {
            return put(key, JsonValue.wrap(value));
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a double.
         * @return The JSON map builder.
         */
        public Builder put(@NonNull String key, double value) {
            return put(key, JsonValue.wrap(value));
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a char.
         * @return The JSON map builder.
         */
        public Builder put(@NonNull String key, char value) {
            return put(key, JsonValue.wrap(value));
        }

        /**
         * Create the JSON map.
         *
         * @return The created JSON map.
         */
        public JsonMap build() {
            return new JsonMap(map);
        }
    }
}
