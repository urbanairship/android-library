/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.wallet;

import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.text.TextUtils;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

/**
 * Defines a field that can be sent up when fetching a {@link Pass}.
 */
public class Field implements JsonSerializable {

    private static final String VALUE_KEY = "value";
    private static final String LABEL_KEY = "label";

    private final String name;
    private final String label;
    private final Object value;

    /**
     * Default constructor.
     *
     * @param builder The field builder instance.
     */
    Field(Builder builder) {
        this.name = builder.name;
        this.label = builder.label;
        this.value = builder.value;
    }

    /**
     * Creates a new Builder instance.
     *
     * @return The new Builder instance.
     */
    static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Gets the name of the field.
     *
     * @return The field's name.
     */
    String getName() {
        return name;
    }

    @Override
    public String toString() {
        return toJsonValue().toString();
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                .putOpt(LABEL_KEY, label)
                .putOpt(VALUE_KEY, value)
                .build()
                .toJsonValue();
    }

    /**
     * Builds the {@link Field} object.
     */
    public static class Builder {

        private String name;
        private String label;
        private Object value;

        /**
         * Sets the field's name.
         *
         * @param name The field's name.
         * @return Builder object.
         */
        public Builder setName(@NonNull @Size(min = 1) String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the field's label.
         *
         * @param label The field's label.
         * @return Builder object.
         */
        public Builder setLabel(String label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the field's value.
         *
         * @param value The field's value.
         * @return Builder object.
         */
        public Builder setValue(String value) {
            this.value = value;
            return this;
        }

        /**
         * Sets the field's value.
         *
         * @param value The field's value.
         * @return Builder object.
         */
        public Builder setValue(int value) {
            this.value = value;
            return this;
        }

        /**
         * Builds the field.
         *
         * @return A field instance.
         * @throws IllegalStateException if the name or both the value and label are missing.
         */
        public Field build() {
            if (TextUtils.isEmpty(name) || (value == null && TextUtils.isEmpty(label))) {
                throw new IllegalStateException("The field must have a name and either a value or label.");
            }

            return new Field(this);
        }
    }
}