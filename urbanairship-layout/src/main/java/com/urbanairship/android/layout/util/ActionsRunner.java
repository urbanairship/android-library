/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import com.urbanairship.json.JsonValue;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Actions Runner for handling button actions.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@FunctionalInterface
public interface ActionsRunner {
    /**
     * Runs the given actions.
     */
    void run(@NonNull Map<String, JsonValue> actions);
}
