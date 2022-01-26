/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Clock;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.Size;

/**
 * Interface used for modifying attributes.
 */
abstract public class AttributeEditor {

    private static final long MAX_ATTRIBUTE_FIELD_LENGTH = 1024;

    private final List<PartialAttributeMutation> partialMutations = new ArrayList<>();
    private final Clock clock;

    /**
     * Attribute editor constructor.
     *
     * @param clock The clock.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected AttributeEditor(@NonNull Clock clock) {
        this.clock = clock;
    }

    /**
     * Sets a string attribute.
     *
     * @param key The attribute key greater than one character and less than 1024 characters in length.
     * @param string The attribute string greater than one character and less than 1024 characters in length.
     * @return The AttributeEditor.
     */
    @NonNull
    public AttributeEditor setAttribute(@Size(min = 1, max = MAX_ATTRIBUTE_FIELD_LENGTH) @NonNull String key,
                                        @Size(min = 1, max = MAX_ATTRIBUTE_FIELD_LENGTH) @NonNull String string) {
        if (isInvalidField(key) || isInvalidField(string)) {
            return this;
        }

        partialMutations.add(new PartialAttributeMutation(key, string));
        return this;
    }

    /**
     * Sets an integer number attribute.
     *
     * @param key The attribute key greater than one character and less than 1024 characters in length.
     * @param number The number attribute.
     * @return The AttributeEditor.
     */
    @NonNull
    public AttributeEditor setAttribute(@Size(min = 1, max = MAX_ATTRIBUTE_FIELD_LENGTH) @NonNull String key, int number) {

        if (isInvalidField(key)) {
            return this;
        }

        partialMutations.add(new PartialAttributeMutation(key, number));
        return this;
    }

    /**
     * Sets a long number attribute.
     *
     * @param key The attribute key greater than one character and less than 1024 characters in length.
     * @param number The number attribute.
     * @return The AttributeEditor.
     */
    @NonNull
    public AttributeEditor setAttribute(@Size(min = 1, max = MAX_ATTRIBUTE_FIELD_LENGTH) @NonNull String key, long number) {
        if (isInvalidField(key)) {
            return this;
        }

        partialMutations.add(new PartialAttributeMutation(key, number));
        return this;
    }

    /**
     * Sets a float number attribute.
     *
     * @param key The attribute key greater than one character and less than 1024 characters in length.
     * @param number The number attribute.
     * @return The AttributeEditor.
     * @throws NumberFormatException if the number is NaN or infinite.
     */
    @NonNull

    public AttributeEditor setAttribute(@Size(min = 1, max = MAX_ATTRIBUTE_FIELD_LENGTH) @NonNull String key, float number) throws NumberFormatException {
        if (isInvalidField(key)) {
            return this;
        }

        if (Float.isNaN(number) || Float.isInfinite(number)) {
            throw new NumberFormatException("Infinity or NaN: " + number);
        }

        partialMutations.add(new PartialAttributeMutation(key, number));
        return this;
    }

    /**
     * Sets a double number attribute.
     *
     * @param key The attribute key greater than one character and less than 1024 characters in length.
     * @param number The number attribute.
     * @return The AttributeEditor.
     * @throws NumberFormatException if the number is NaN or infinite.
     */
    @NonNull
    public AttributeEditor setAttribute(@Size(min = 1, max = MAX_ATTRIBUTE_FIELD_LENGTH) @NonNull String key, double number) throws NumberFormatException {
        if (isInvalidField(key)) {
            return this;
        }

        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw new NumberFormatException("Infinity or NaN: " + number);
        }

        partialMutations.add(new PartialAttributeMutation(key, number));
        return this;
    }

    /**
     * Sets a date attribute.
     *
     * @param key The attribute key greater than one character and less than 1024 characters in length.
     * @param date The date attribute.
     * @return The AttributeEditor.
     */
    @NonNull
    public AttributeEditor setAttribute(@Size(min = 1, max = MAX_ATTRIBUTE_FIELD_LENGTH) @NonNull String key, @NonNull Date date) {
        if (isInvalidField(key)) {
            return this;
        }

        String dateString = DateUtils.createIso8601TimeStamp(date.getTime());

        partialMutations.add(new PartialAttributeMutation(key, dateString));
        return this;
    }

    /**
     * Removes an attribute.
     *
     * @param key The attribute key greater than one character and less than 1024 characters in length.
     * @return The AttributeEditor.
     */
    @NonNull
    public AttributeEditor removeAttribute(@Size(min = 1, max = MAX_ATTRIBUTE_FIELD_LENGTH) @NonNull String key) {
        if (isInvalidField(key)) {
            return this;
        }

        partialMutations.add(new PartialAttributeMutation(key, null));
        return this;
    }

    private boolean isInvalidField(@NonNull String key) {
        if (UAStringUtil.isEmpty(key)) {
            Logger.error("Attribute fields cannot be empty.");
            return true;
        }

        if (key.length() > MAX_ATTRIBUTE_FIELD_LENGTH) {
            Logger.error("Attribute field inputs cannot be greater than %s characters in length", MAX_ATTRIBUTE_FIELD_LENGTH);
            return true;
        }

        return false;
    }

    /**
     * Apply the attribute changes.
     */
    public void apply() {
        if (partialMutations.size() == 0) {
            return;
        }

        long timestamp = clock.currentTimeMillis();
        List<AttributeMutation> mutations = new ArrayList<>();
        for (PartialAttributeMutation partial : partialMutations) {
            try {
                mutations.add(partial.toMutation(timestamp));
            } catch (IllegalArgumentException e) {
                Logger.error(e, "Invalid attribute mutation.");
            }
        }

        onApply(AttributeMutation.collapseMutations(mutations));
    }

    /**
     * @param collapsedMutations
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected abstract void onApply(@NonNull List<AttributeMutation> collapsedMutations);

    private class PartialAttributeMutation {

        String key;
        Object value;

        PartialAttributeMutation(@NonNull String key, @Nullable Object value) {
            this.key = key;
            this.value = value;
        }

        @NonNull
        AttributeMutation toMutation(long timestamp) {
            if (value != null) {
                return AttributeMutation.newSetAttributeMutation(key, JsonValue.wrapOpt(value), timestamp);
            } else {
                return AttributeMutation.newRemoveAttributeMutation(key, timestamp);
            }
        }

    }

}
