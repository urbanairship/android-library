/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.UALog;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.JsonMap;
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
    /**
     * Reserved key for JSON attribute expiration.
     */
    private static final String JSON_EXPIRY_KEY = "exp";

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

    /**
     * Removes a JSON attribute for the given instance.
     *
     * @param key The attribute key.
     * @param instanceID The instance identifier.
     * @return The AttributeEditor.
     */
    @NonNull
    public AttributeEditor removeAttribute(@NonNull String key, @NonNull String instanceID) {
        // Validate key and instanceID
        if (isInvalidField(key)
                || isInvalidField(instanceID)
                || key.contains("#")
                || instanceID.contains("#")) {
            UALog.e("Invalid attribute or instance ID. Must not be empty, exceed %s characters, or contain '#'.", MAX_ATTRIBUTE_FIELD_LENGTH);
            return this;
        }
        String formattedKey = key + "#" + instanceID;
        partialMutations.add(new PartialAttributeMutation(formattedKey, null));
        return this;
    }

    /**
     * Sets a custom attribute with a JSON payload and optional expiration.
     *
     * @param key The attribute key.
     * @param instanceID The instance identifier.
     * @param jsonMap A JsonMap representing the custom payload.
     * @return The AttributeEditor.
     * @throws IllegalArgumentException if the expiration is invalid, the payload is empty, or contains a reserved key.
     */
    @NonNull
    public AttributeEditor setAttribute(@NonNull String key,
                                        @NonNull String instanceID,
                                        @NonNull JsonMap jsonMap) throws IllegalArgumentException {
        return setAttribute(key, instanceID, jsonMap, null);
    }

    /**
     * Sets a custom attribute with a JSON payload and optional expiration.
     *
     * @param key The attribute key.
     * @param instanceID The instance identifier.
     * @param jsonMap A JsonMap representing the custom payload.
     * @param expiration Optional expiration Date. Must be > now and <= 731 days from now.
     * @return The AttributeEditor.
     * @throws IllegalArgumentException if the expiration is invalid, the payload is empty, or contains a reserved key.
     */
    @NonNull
    public AttributeEditor setAttribute(@NonNull String key,
                                        @NonNull String instanceID,
                                        @NonNull JsonMap jsonMap,
                                        @Nullable Date expiration) throws IllegalArgumentException {
        long now = clock.currentTimeMillis();
        if (expiration != null) {
            long expMillis = expiration.getTime();
            long maxMillis = now + 1000L * 60 * 60 * 24 * 731;
            if (expMillis <= now || expMillis > maxMillis) {
                throw new IllegalArgumentException("The expiration is invalid (more than 731 days or not in the future)." );
            }
        }
        if (jsonMap.isEmpty()) {
            throw new IllegalArgumentException("The input `json` payload is empty.");
        }
        if (jsonMap.containsKey(JSON_EXPIRY_KEY)) {
            throw new IllegalArgumentException(
                "The JSON contains a top-level `" + JSON_EXPIRY_KEY + "` key (reserved for expiration)."
            );
        }
        // Build JSON payload with optional expiration
        JsonMap.Builder builder = JsonMap.newBuilder().putAll(jsonMap);
        if (expiration != null) {
            double expSeconds = expiration.getTime() / 1000.0;
            builder.put(JSON_EXPIRY_KEY, expSeconds);
        }
        JsonMap finalJson = builder.build();
        // Format key with instanceID
        String formattedKey = key + "#" + instanceID;
        partialMutations.add(new PartialAttributeMutation(formattedKey, finalJson));
        return this;
    }

    private boolean isInvalidField(@NonNull String key) {
        if (UAStringUtil.isEmpty(key)) {
            UALog.e("Attribute fields cannot be empty.");
            return true;
        }

        if (key.length() > MAX_ATTRIBUTE_FIELD_LENGTH) {
            UALog.e("Attribute field inputs cannot be greater than %s characters in length", MAX_ATTRIBUTE_FIELD_LENGTH);
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
                UALog.e(e, "Invalid attribute mutation.");
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
