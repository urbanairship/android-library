/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Event to track Google Play Store referrals
 */
class InstallAttributionEvent extends Event {

    private static final String PLAY_STORE_REFERRER = "google_play_referrer";

    private final String referrer;

    /**
     * Default constructor.
     *
     * @param referrer The Play Store install referrer.
     */
    public InstallAttributionEvent(@NonNull String referrer) {
        this.referrer = referrer;
    }

    @NonNull
    @Override
    public EventType getType() {
        return EventType.INSTALL_ATTRIBUTION;
    }

    /**
     * @hide
     */
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JsonMap getEventData() {
        return JsonMap.newBuilder()
                      .put(PLAY_STORE_REFERRER, referrer)
                      .build();
    }

}
