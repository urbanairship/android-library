package com.urbanairship.modules;

import com.urbanairship.push.notifications.NotificationProvider;

import androidx.annotation.NonNull;

/**
 * Accengage notification handler.
 *
 * @hide
 */
public interface AccengageNotificationHandler {

    /**
     * Returns the Accengage notification provider.
     *
     * @return The Accengage notification provider.
     */
    @NonNull
    NotificationProvider getNotificationProvider();

}

