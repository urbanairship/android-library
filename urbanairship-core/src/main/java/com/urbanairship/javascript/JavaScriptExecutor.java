/* Copyright Airship and Contributors */

package com.urbanairship.javascript;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Executes JavaScript.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface JavaScriptExecutor {

    /**
     * Called to execute JavaScript.
     *
     * @param javaScript The JavaScript.
     */
    void executeJavaScript(@NonNull String javaScript);

}
