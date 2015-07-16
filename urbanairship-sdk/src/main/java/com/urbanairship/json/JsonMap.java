/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.json;

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
public class JsonMap implements Iterable<Map.Entry<String, JsonValue>> {

    private Map<String, JsonValue> map;

    /**
     * Creates a JsonMap from a Map.
     *
     * @param map A map of strings to JsonValues.
     */
    public JsonMap(@Nullable Map<String, JsonValue> map) {
        this.map = map == null ? new HashMap<String, JsonValue>() : new HashMap<>(map);
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
}
