/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

/**
 * An ActionValue is a representation of any value that can be described using JSON. It can contain one
 * of the following: a JsonMap, a JsonList, a Number, a Boolean, String, or it can contain null.
 */
public class ActionValue implements JsonSerializable, Parcelable {
    private final JsonValue jsonValue;

    /**
     * Creates an ActionValue from a JsonValue.
     *
     * @param jsonValue A jsonValue.
     */
    public ActionValue(JsonValue jsonValue) {
        this.jsonValue = jsonValue == null ? JsonValue.NULL : jsonValue;
    }

    /**
     * Wraps a String as an ActionValue.
     *
     * @param value The action's value as a string.
     * @return The ActionValue object.
     */
    @NonNull
    public static ActionValue wrap(String value) {
        return new ActionValue(JsonValue.wrap(value));
    }

    /**
     * Wraps an int as an ActionValue.
     *
     * @param value The action's value as an int.
     * @return The ActionValue object.
     */
    @NonNull
    public static ActionValue wrap(int value) {
        return new ActionValue(JsonValue.wrap(value));
    }

    /**
     * Wraps a long as an ActionValue.
     *
     * @param value The action's value as a long.
     * @return The ActionValue object.
     */
    @NonNull
    public static ActionValue wrap(long value) {
        return new ActionValue(JsonValue.wrap(value));
    }

    /**
     * Wraps a char as an ActionValue.
     *
     * @param value The action's value as a char.
     * @return The ActionValue object.
     */
    @NonNull
    public static ActionValue wrap(char value) {
        return new ActionValue(JsonValue.wrap(value));
    }

    /**
     * Wraps a boolean as an ActionValue.
     *
     * @param value The action's value as a boolean.
     * @return The ActionValue object.
     */
    @NonNull
    public static ActionValue wrap(boolean value) {
        return new ActionValue(JsonValue.wrap(value));
    }

    /**
     * Wraps a JsonSerializable object as an ActionValue.
     *
     * @param value The action's value as a JsonSerializable object.
     * @return The ActionValue object.
     */
    @NonNull
    public static ActionValue wrap(JsonSerializable value) {
        return new ActionValue(JsonValue.wrap(value));
    }

    /**
     * Wraps a {@link com.urbanairship.json.JsonValue} compatible object as an ActionValue.
     *
     * @param object The action's value.
     * @return The ActionValue object.
     * @throws com.urbanairship.actions.ActionValueException If the object is unable to be wrapped into an
     * action value.
     */
    @NonNull
    public static ActionValue wrap(Object object) throws ActionValueException {
        try {
            return new ActionValue(JsonValue.wrap(object));
        } catch (JsonException e) {
            throw new ActionValueException("Invalid ActionValue object: " + object, e);
        }
    }

    /**
     * Creates an empty ActionValue.
     */
    public ActionValue() {
        this.jsonValue = JsonValue.NULL;
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
        return jsonValue.getString(defaultValue);
    }

    /**
     * Gets the contained values as an int.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as an int, or the defaultValue if the value is not a number.
     */
    public int getInt(int defaultValue) {
        return jsonValue.getInt(defaultValue);
    }

    /**
     * Gets the contained values as an double.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as a double, or the defaultValue if the value is not a number.
     */
    public double getDouble(double defaultValue) {
        return jsonValue.getDouble(defaultValue);
    }

    /**
     * Gets the contained values as an long.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as a long, or the defaultValue if the value is not a number.
     */
    public long getLong(long defaultValue) {
        return jsonValue.getLong(defaultValue);
    }

    /**
     * Gets the contained values as a boolean.
     *
     * @param defaultValue The default value if the contained value is not a boolean.
     * @return The value as a boolean, or the defaultValue if the value is not a boolean.
     */
    public boolean getBoolean(boolean defaultValue) {
        return jsonValue.getBoolean(defaultValue);
    }

    /**
     * Gets the contained values as a JsonList.
     *
     * @return The value as a JsonList, or null if the value is not a JsonList.
     */
    public JsonList getList() {
        return jsonValue.getList();
    }

    /**
     * Gets the contained values as a JsonMap.
     *
     * @return The value as a JsonMap, or null if the value is not a JsonMap.
     */
    public JsonMap getMap() {
        return jsonValue.getMap();
    }

    /**
     * If the contained value is null.
     *
     * @return <code>true</code> if the contained value is null, otherwise <code>false</code>.
     */
    public boolean isNull() {
        return jsonValue.isNull();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof ActionValue) {
            ActionValue o = (ActionValue) object;
            return jsonValue.equals(o.jsonValue);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return jsonValue.hashCode();
    }

    /**
     * Returns the ActionValue as a JSON encoded String.
     *
     * @return The value as a JSON encoded String.
     */
    @Override
    public String toString() {
        return jsonValue.toString();
    }

    @Override
    public JsonValue toJsonValue() {
        return jsonValue;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(jsonValue, flags);
    }

    /**
     * ActionValue parcel creator.
     */
    public static final Parcelable.Creator<ActionValue> CREATOR = new Parcelable.Creator<ActionValue>() {

        @Override
        public ActionValue createFromParcel(Parcel in) {
            return new ActionValue((JsonValue) in.readParcelable(JsonValue.class.getClassLoader()));
        }

        @Override
        public ActionValue[] newArray(int size) {
            return new ActionValue[size];
        }
    };
}
