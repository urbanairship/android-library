/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam.view;

import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

/**
 * Common banner view interface.
 */
public interface Banner {

    /**
     * Listener interface for action button clicks.
     */
    interface OnActionClickListener {

        /**
         * Called when an action button is clicked.
         * @param actionButton The action button.
         */
        void onActionClick(NotificationActionButton actionButton);
    }

    /**
     * Listener interface for dismiss button clicks.
     */
    interface OnDismissClickListener {

        /**
         * Called when the dismiss button is clicked.
         */
        void onDismissClick();
    }

    /**
     * Sets the listener for dismiss button clicks
     * @param listener The dismiss listener.
     */
    void setOnDismissClickListener(OnDismissClickListener listener);

    /**
     * Sets the listener for action button clicks
     * @param listener The action button listener.
     */
    void setOnActionClickListener(OnActionClickListener listener);


    /**
     * Sets the banner's text.
     * @param text Banner's text.
     */
    void setText(CharSequence text);

    /**
     * Sets the action buttons from a {@link NotificationActionButtonGroup}.
     * @param group The notification action button group.
     */
    void setNotificationActionButtonGroup(NotificationActionButtonGroup group);

    /**
     * Sets the primary banner color.
     * @param color The primary color.
     */
    void setPrimaryColor(int color);

    /**
     * Sets the secondary banner color.
     * @param color The secondary color.
     */
    void setSecondaryColor(int color);
}
