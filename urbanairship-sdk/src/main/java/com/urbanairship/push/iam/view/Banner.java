/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
