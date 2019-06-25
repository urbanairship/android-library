/* Copyright Airship and Contributors */

package com.urbanairship.json;

import androidx.annotation.NonNull;

/**
 * Interface for classes whose instances can be written as a JsonValue.
 */
public interface JsonSerializable {

    /**
     * Returns the objects represented as a JsonValue.
     *
     * @return The object as a JsonValue.
     */
    @NonNull
    JsonValue toJsonValue();

}
