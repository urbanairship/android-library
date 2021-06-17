/* Copyright Airship and Contributors */

package com.urbanairship;

import androidx.annotation.IntDef;
import androidx.annotation.RestrictTo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Airship component groups. Used to group components for remote-config.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AirshipComponentGroups {

    @IntDef({ NONE, PUSH, ANALYTICS, MESSAGE_CENTER, IN_APP, ACTION_AUTOMATION, NAMED_USER, LOCATION, CHANNEL, CHAT, CONTACT })
    @Retention(RetentionPolicy.SOURCE)
    @interface Group {
    }

    int NONE = -1;
    int PUSH = 0;
    int ANALYTICS = 1;
    int MESSAGE_CENTER = 2;
    int IN_APP = 3;
    int ACTION_AUTOMATION = 4;
    int NAMED_USER = 5;
    int LOCATION = 6;
    int CHANNEL = 7;
    int CHAT = 8;
    int CONTACT = 9;
}
