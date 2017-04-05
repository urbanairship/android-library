/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.shadow;

import android.app.NotificationManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowNotificationManager;

@Implements(NotificationManager.class)
public class ShadowNotificationManagerExtension extends ShadowNotificationManager {
    @Implementation
    public boolean areNotificationsEnabled() {
        return true;
    }
}
