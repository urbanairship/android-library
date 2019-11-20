/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Size;

/**
 * Interface used for modifying attributes.
 */
abstract public class AttributeEditor {

    private static final long MAX_ATTRIBUTE_FIELD_LENGTH = 1024;

    private final List<AttributeMutation> mutations = new ArrayList<>();

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

        mutations.add(AttributeMutation.newSetAttributeMutation(key, string));
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

        mutations.add(AttributeMutation.newRemoveAttributeMutation(key));
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
        if (mutations.size() == 0) {
            return;
        }

        onApply(mutations);
    }

    abstract void onApply(@NonNull List<AttributeMutation> collapsedMutations);
}
