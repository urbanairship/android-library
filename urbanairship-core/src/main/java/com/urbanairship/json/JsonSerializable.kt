/* Copyright Airship and Contributors */
package com.urbanairship.json

/**
 * Interface for classes whose instances can be written as a JsonValue.
 */
public fun interface JsonSerializable {

    /**
     * Returns the objects represented as a JsonValue.
     *
     * @return The object as a JsonValue.
     */
    public fun toJsonValue(): JsonValue
}
