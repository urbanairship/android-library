/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import androidx.annotation.NonNull;

import com.urbanairship.json.JsonMap;

/**
 * Event to track Google Play Store referrals
 */
class InstallAttributionEvent extends Event {

    private static final String TYPE = "install_attribution";

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
    public String getType() {
        return TYPE;
    }

    @NonNull
    @Override
    protected JsonMap getEventData() {
        return JsonMap.newBuilder()
                      .put(PLAY_STORE_REFERRER, referrer)
                      .build();
    }

}
