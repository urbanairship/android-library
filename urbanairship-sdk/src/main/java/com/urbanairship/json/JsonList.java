/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import android.support.annotation.Nullable;

import com.urbanairship.Logger;

import org.json.JSONException;
import org.json.JSONStringer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An immutable list of JsonValues.
 */
public class JsonList implements Iterable<JsonValue>, JsonSerializable {

    static final JsonList EMPTY_LIST = new JsonList(null);

    private final List<JsonValue> list;

    /**
     * Default Constructor.
     *
     * @param list A list of JsonValues.
     */
    public JsonList(@Nullable List<JsonValue> list) {
        this.list = list == null ? new ArrayList<JsonValue>() : new ArrayList<>(list);
    }

    /**
     * Tests whether this {@code List} contains the specified JSON value.
     *
     * @param jsonValue the object to search for.
     * @return {@code true} if the list contains the value, otherwise {@code false}.
     */
    public boolean contains(JsonValue jsonValue) {
        return list.contains(jsonValue);
    }

    /**
     * Returns the element at the specified location in this {@code List}.
     *
     * @param location the index of the element to return.
     * @return the element at the specified location.
     * @throws IndexOutOfBoundsException if {@code location < 0 || location >= size()}
     */
    public JsonValue get(int location) {
        return list.get(location);
    }

    /**
     * Searches this {@code List} for the specified object and returns the index of the
     * first occurrence.
     *
     * @param jsonValue the object to search for.
     * @return the index of the first occurrence of the object or -1 if the
     * object was not found.
     */
    public int indexOf(JsonValue jsonValue) {
        return list.indexOf(jsonValue);
    }

    /**
     * Returns whether this {@code List} contains no elements.
     *
     * @return {@code true} if this {@code List} has no elements, {@code false}
     * otherwise.
     * @see #size
     */
    public boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * Returns an iterator on the elements of this {@code List}. The elements are
     * iterated in the same order as they occur in the {@code List}.
     *
     * @return an iterator on the elements of this {@code List}.
     * @see Iterator
     */
    public Iterator<JsonValue> iterator() {
        return list.iterator();
    }

    /**
     * Searches this {@code List} for the specified object and returns the index of the
     * first occurrence.
     *
     * @param jsonValue the object to search for.
     * @return the index of the first occurrence of the object or -1 if the
     * object was not found.
     */
    public int lastIndexOf(JsonValue jsonValue) {
        return list.indexOf(jsonValue);
    }

    /**
     * Returns the number of elements in this {@code List}.
     *
     * @return the number of elements in this {@code List}.
     */
    public int size() {
        return list.size();
    }

    /**
     * Gets the JsonList as a List.
     *
     * @return The JsonList as a list.
     */
    public List<JsonValue> getList() {
        return new ArrayList<>(list);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if ((object instanceof JsonList)) {
            return list.equals(((JsonList) object).list);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return list.hashCode();
    }


    /**
     * Returns the JsonList as a JSON encoded String.
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
            Logger.error("JsonList - Failed to create JSON String.", e);
            return "";
        }
    }

    /**
     * Helper method that is used to write the list as a JSON String.
     *
     * @param stringer The JSONStringer object.
     * @throws org.json.JSONException If the value is unable to be written as JSON.
     */
    void write(JSONStringer stringer) throws JSONException {
        stringer.array();
        for (JsonValue actionValue : this) {
            actionValue.write(stringer);
        }
        stringer.endArray();
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonValue.wrap(this);
    }
}
