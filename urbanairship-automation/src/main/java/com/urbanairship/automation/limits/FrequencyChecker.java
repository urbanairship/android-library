/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits;

import androidx.annotation.RestrictTo;

/**
 * Provides checks for frequency constraints.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface FrequencyChecker {

    /**
     * Checks if the frequency constraints are over the limit.
     *
     * @return {@code true} if over the limit, otherwise {@code false}.
     */
    boolean isOverLimit();

    /**
     * Checks if the frequency constraints are over limit before incrementing the count towards the constraints.
     *
     * @return {@code true} if the constraints are not over the limit and the count was incremented, otherwise {@code false}.
     */
    boolean checkAndIncrement();

}
