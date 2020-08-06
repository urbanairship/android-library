/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * In-app message display event.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DisplayEvent extends InAppMessageEvent {

    private static final String TYPE = "in_app_display";

    /**
     * Default constructor.
     *
     * @param scheduleId The schedule ID.
     * @param message The in-app message.
     */
    DisplayEvent(@NonNull String scheduleId, @NonNull InAppMessage message) {
        super(scheduleId, message);
    }

    @NonNull
    @Override
    public final String getType() {
        return TYPE;
    }

}
