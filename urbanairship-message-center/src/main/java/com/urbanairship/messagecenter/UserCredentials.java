/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import androidx.annotation.RestrictTo;

/**
 * Model object for User credentials.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class UserCredentials {

    private final String username;
    private final String password;

    public UserCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
