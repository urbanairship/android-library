/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.util;

/**
 * A class containing basic math operations.
 */
public abstract class UAMathUtil {

    /**
     * Constrains an int to a min and max.
     *
     * @param value Value to clamp.
     * @param min The floor of the value.
     * @param max The ceiling of the value.
     * @return The value constrained between the min and max.
     */
    public static int constrain(int value, int min, int max) {
        if (value > max) {
            return max;
        }

        if (value < min) {
            return min;
        }

        return value;
    }
}
