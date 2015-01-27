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

package com.urbanairship.actions;

import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An ActionValue is a representation of any value that can be described using JSON. It can contain one
 * of the following: a Map of Strings to ActionValues, a List of ActionValues, a Number,
 * a Boolean, String, or it can contain null.
 * </p>
 * ActionValues can be created from Java Objects by calling {@link #wrap(Object)} or from a JSON
 * String by calling{@link #parseString(String)}. The ActionValue {@link #toString()} returns the
 * JSON String representation of the object.
 */
public class ActionValue {

    /**
     * A null representation of the ActionValue.
     */
    public final static ActionValue NULL = new ActionValue(null);

    private final Object value;

    /**
     * Constructs a new action value.
     *
     * @param value The wrapped value.
     */
    private ActionValue(Object value) {
        this.value = value;
    }

    /**
     * Gets the contained value as a String.
     *
     * @return The value as a String, or null if the value is not a String.
     */
    public String getString() {
        return getString(null);
    }

    /**
     * Gets the contained values as a String.
     *
     * @param defaultValue The default value if the contained value is not a String.
     * @return The value as a String, or the defaultValue if the value is not a String.
     */
    public String getString(String defaultValue) {
        if (isNull()) {
            return null;
        }

        if (value instanceof String) {
            return (String) value;
        }

        return defaultValue;
    }

    /**
     * Gets the contained values as an int.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as an int, or the defaultValue if the value is not a number.
     */
    public int getInt(int defaultValue) {
        if (isNull()) {
            return defaultValue;
        }

        if (value instanceof Integer) {
            return (Integer) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        return defaultValue;
    }

    /**
     * Gets the contained values as an double.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as a double, or the defaultValue if the value is not a number.
     */
    public double getDouble(double defaultValue) {
        if (isNull()) {
            return defaultValue;
        }

        if (value instanceof Double) {
            return (Double) value;
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        return defaultValue;
    }

    /**
     * Gets the contained values as an long.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as a long, or the defaultValue if the value is not a number.
     */
    public long getLong(long defaultValue) {
        if (isNull()) {
            return defaultValue;
        }

        if (value instanceof Long) {
            return (Long) value;
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return defaultValue;
    }


    /**
     * Gets the contained values as a boolean.
     *
     * @param defaultValue The default value if the contained value is not a boolean.
     * @return The value as a boolean, or the defaultValue if the value is not a boolean.
     */
    public boolean getBoolean(boolean defaultValue) {
        if (isNull()) {
            return defaultValue;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        return defaultValue;
    }

    /**
     * Gets the contained values as a List of ActionValues.
     *
     * @return The value as a List, or null if the value is not a List.
     */
    @SuppressWarnings("unchecked")
    public List<ActionValue> getList() {
        if (!isNull() && value instanceof List) {
            return (List<ActionValue>) value;
        }

        return null;
    }

    /**
     * Gets the contained values as a Map of ActionValues.
     *
     * @return The value as a Map, or null if the value is not a Map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, ActionValue> getMap() {
        if (!isNull() && value instanceof Map) {
            return (Map<String, ActionValue>) value;
        }

        return null;
    }

    /**
     * If the contained value is null.
     *
     * @return <code>true</code> if the contained value is null, otherwise <code>false</code>.
     */
    public boolean isNull() {
        return value == null;
    }

    /**
     * Parse a JSON encoded String.
     *
     * @param jsonString The json encoded String.
     * @return An ActionValue from the encoded String.
     * @throws ActionValueException If the JSON was unable to be parsed
     */
    public static ActionValue parseString(String jsonString) throws ActionValueException {
        if (UAStringUtil.isEmpty(jsonString)) {
            return ActionValue.NULL;
        }

        JSONTokener tokener = new JSONTokener(jsonString);

        try {
            return ActionValue.wrap(tokener.nextValue());
        } catch (JSONException e) {
            throw new ActionValueException("Unable to parse string", e);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ActionValue)) {
            return false;
        }

        ActionValue o = (ActionValue) object;

        if (isNull()) {
            return o.isNull();
        }

        return value.equals(o.value);
    }

    @Override
    public int hashCode() {
        int result = 17;
        if (value != null) {
            result = 31 * result + value.hashCode();
        }
        return result;
    }

    /**
     * Returns the ActionValue as a JSON encoded String.
     *
     * @return The value as a JSON encoded String.
     */
    @Override
    public String toString() {
        if (isNull()) {
            return "null";
        }

        try {
            if (value instanceof String) {
                return JSONObject.quote((String) value);
            }

            if (value instanceof Number) {
                return JSONObject.numberToString((Number) value);
            }

            if (value instanceof Map || value instanceof List) {
                JSONStringer stringer = new JSONStringer();
                write(stringer);
                return stringer.toString();
            }

            return String.valueOf(value);
        } catch (JSONException e) {
            Logger.error("Failed to create ActionValue to json.", e);
            return "";
        }
    }

    /**
     * Helper method that is used to write the value as a JSON String.
     *
     * @param stringer The JSONStringer object.
     * @throws JSONException If the value is unable to be written as JSON.
     */
    private void write(JSONStringer stringer) throws JSONException {
        if (isNull()) {
            stringer.value(JSONObject.NULL);
        }

        if (value instanceof List) {
            stringer.array();
            for (ActionValue actionValue : getList()) {
                actionValue.write(stringer);
            }
            stringer.endArray();
        } else if (value instanceof Map) {
            stringer.object();
            for (Map.Entry<String, ActionValue> entry : getMap().entrySet()) {
                stringer.key(entry.getKey());
                entry.getValue().write(stringer);
            }
            stringer.endObject();
        } else {
            stringer.value(value);
        }
    }

    /**
     * Wraps any valid object into an ActionValue. If the object is unable to be wrapped, the default
     * value will be returned. See {@link #wrap(Object)} for rules on object wrapping.
     *
     * @param object The object to wrap.
     * @param defaultValue The default value if the object is unable to be wrapped.
     * @return The object wrapped in an ActionValue or the default value if the object is unable to be wrapped.
     */
    public static ActionValue wrap(Object object, ActionValue defaultValue) {
        try {
            return wrap(object);
        } catch (ActionValueException ex) {
            return defaultValue;
        }
    }


    /**
     * Wraps any valid object into an ActionValue.
     * <p/>
     * Objects will be wrapped with the following rules:
     * <ul>
     * <li>JSONObject.NULL or null will result in {@link ActionValue#NULL}.</li>
     * <li>Collections, arrays, JSONArray values will be wrapped into a List of ActionValues.</li>
     * <li>Maps with String keys will be wrapped into a Map with the values wrapped in an ActionValue.</li>
     * <li>String and primitive wrapper objects will be wrapped directly into an ActionValue</li>
     * <li>ActionValues will be unmodified.</li>
     * </ul>
     *
     * @param object The object to wrap.
     * @return The object wrapped in an ActionValue.
     * @throws ActionValueException If the object is not a supported type or contains an unsupported type.
     */
    public static ActionValue wrap(Object object) throws ActionValueException {
        if (object == null || object == JSONObject.NULL) {
            return NULL;
        }

        if (object instanceof ActionValue) {
            return (ActionValue) object;
        }

        if (object instanceof Byte || object instanceof Short) {
            return new ActionValue(((Number) object).intValue());
        }

        if (object instanceof Character) {
            Character character = (Character) object;
            return new ActionValue(character.toString());
        }

        if (object instanceof Float) {
            return new ActionValue(((Number) object).doubleValue());
        }

        if (object instanceof Double) {
            Double d = (Double) object;
            if (d.isInfinite() || d.isNaN()) {
                throw new ActionValueException("Invalid Double value: " + d);
            }

            return new ActionValue(object);
        }

        if (object instanceof Boolean ||
                object instanceof Integer ||
                object instanceof Long ||
                object instanceof String) {
            return new ActionValue(object);
        }

        try {
            if (object instanceof JSONArray) {
                return wrapJSONArray((JSONArray) object);
            }

            if (object instanceof JSONObject) {
                return wrapJSONObject((JSONObject) object);
            }

            if (object instanceof Collection) {
                return wrapCollection((Collection) object);
            }

            if (object.getClass().isArray()) {
                return wrapArray(object);
            }

            if (object instanceof Map) {
                return wrapMap((Map) object);
            }
        } catch (ActionValueException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ActionValueException("Failed to wrap value.", exception);
        }

        throw new ActionValueException("Illegal object: " + object);
    }

    /**
     * Helper method to wrap an array.
     *
     * @param array The array to wrap.
     * @return The wrapped array.
     * @throws ActionValueException If the array contains an unwrappable object.
     */
    private static ActionValue wrapArray(Object array) throws ActionValueException {
        final int length = Array.getLength(array);
        List<ActionValue> values = new ArrayList<>(length);

        for (int i = 0; i < length; ++i) {
            values.add(wrap(Array.get(array, i)));
        }

        return new ActionValue(values);
    }

    /**
     * Helper method to wrap a collection.
     *
     * @param collection The collection to wrap.
     * @return The wrapped array.
     * @throws ActionValueException If the collection contains an unwrappable object.
     */
    private static ActionValue wrapCollection(Collection collection) throws ActionValueException {
        List<ActionValue> values = new ArrayList<>();

        for (Object obj : collection) {
            values.add(wrap(obj));
        }

        return new ActionValue(Collections.unmodifiableList(values));
    }

    /**
     * Helper method to wrap a Map.
     *
     * @param map The map to wrap.
     * @return The wrapped map.
     * @throws ActionValueException If the collection contains an unwrappable object.
     */
    private static ActionValue wrapMap(Map<?, ?> map) throws ActionValueException {
        Map<String, ActionValue> actionValueMap = new HashMap<>();

        for (Map.Entry entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new ActionValueException("Only string map keys are accepted.");
            }

            actionValueMap.put((String) entry.getKey(), wrap(entry.getValue()));
        }

        return new ActionValue(Collections.unmodifiableMap(actionValueMap));
    }

    /**
     * Helper method to wrap a JSONArray.
     *
     * @param jsonArray The JSONArray to wrap.
     * @return The wrapped JSONArray.
     * @throws ActionValueException If the collection contains an unwrappable object.
     */
    private static ActionValue wrapJSONArray(JSONArray jsonArray) throws ActionValueException {
        List<ActionValue> actionValueList = new ArrayList<>(jsonArray.length());

        for (int i = 0; i < jsonArray.length(); i++) {
            if (jsonArray.isNull(i)) {
                actionValueList.add(NULL);
                continue;
            }

            actionValueList.add(wrap(jsonArray.opt(i)));
        }

        return new ActionValue(Collections.unmodifiableList(actionValueList));
    }


    /**
     * Helper method to wrap a JSONObject.
     *
     * @param jsonObject The JSONObject to wrap.
     * @return The wrapped JSONObject.
     * @throws ActionValueException If the collection contains an unwrappable object.
     */
    private static ActionValue wrapJSONObject(JSONObject jsonObject) throws ActionValueException {
        Map<String, ActionValue> actionValueMap = new HashMap<>();

        if (jsonObject == null || jsonObject.length() == 0) {
            return new ActionValue(actionValueMap);
        }

        Iterator iterator = jsonObject.keys();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();

            if (jsonObject.isNull(key)) {
                actionValueMap.put(key, NULL);
                continue;
            }

            actionValueMap.put(key, wrap(jsonObject.opt(key)));
        }

        return new ActionValue(Collections.unmodifiableMap(actionValueMap));
    }

    /**
     * Thrown when an ActionValue is unable to wrap an object or Unable to parse a JSON encoded String.
     */
    public static class ActionValueException extends Throwable {
        public ActionValueException(String message) {
            super(message);
        }

        public ActionValueException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
