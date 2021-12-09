/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.display;

import androidx.annotation.RestrictTo;

/**
 * Display exception.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DisplayException extends Exception {
    public DisplayException(String message) {
        super(message);
    }
}
