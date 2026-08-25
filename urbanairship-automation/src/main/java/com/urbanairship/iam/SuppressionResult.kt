/* Copyright Airship and Contributors */

package com.urbanairship.iam

import com.urbanairship.automation.AutomationAudience

/**
 * Result of an app suppression check for in-app automation.
 */
public sealed class SuppressionResult {
    /** Allow the message to display. */
    public data object Show : SuppressionResult()

    /** Suppress the message with the given miss behavior. */
    public data class Suppress(val behavior: AutomationAudience.MissBehavior) : SuppressionResult()
}
