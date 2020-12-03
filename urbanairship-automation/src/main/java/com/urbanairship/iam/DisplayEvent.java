/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * In-app message display event.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DisplayEvent extends InAppMessageEvent {

    private static final String TYPE = "in_app_display";
    private static final String LOCALE = "locale";
    private final InAppMessage message;

    private DisplayEvent(@NonNull String scheduleId, @NonNull InAppMessage message, @Nullable JsonValue campaigns) {
        super(scheduleId, message.getSource(), campaigns);
        this.message = message;
    }

    /**
     * Creates a new display event.
     * @param scheduleId The schedule ID.
     * @param message The message.
     * @param campaigns The campaigns info.
     * @return The display event.
     */
    public static DisplayEvent newEvent(@NonNull String scheduleId, @NonNull InAppMessage message, @Nullable JsonValue campaigns) {
        return new DisplayEvent(scheduleId, message, campaigns);
    }

    @NonNull
    @Override
    public final String getType() {
        return TYPE;
    }

    @NonNull
    @Override
    protected JsonMap.Builder extendEventDataBuilder(@NonNull JsonMap.Builder builder) {
        return builder.putOpt(LOCALE, message.getRenderedLocale());
    }

}
