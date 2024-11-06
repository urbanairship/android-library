/* Copyright Airship and Contributors */

package com.urbanairship.permission

/**
 * Fallback for when a permission is silently denied.
 */
public sealed class PermissionPromptFallback {

    /**
     * Navigate to system settings. On return the status will be checked again.
     */
    public data object SystemSettings: PermissionPromptFallback()

    /**
     * Run a custom callback. After running the callback, the status will be checked again.
     */
    public class Callback(internal val callback: suspend () -> Unit): PermissionPromptFallback()

    /**
     * No fallback
     */
    public data object None: PermissionPromptFallback()
}
