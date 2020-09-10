package com.urbanairship.automation.auth;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Exceptions when accessing AuthManager.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AuthException extends Exception {

    public AuthException(@NonNull String message, @NonNull Throwable throwable) {
        super(message, throwable);
    }

    public AuthException(@NonNull String message) {
        super(message);
    }
}
