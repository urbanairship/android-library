package com.urbanairship.analytics;

import com.urbanairship.location.RegionEvent;

/**
 * An analytics event listener. Used to listen for generated screen tracking, custom, and region events
 * by adding the listener using {@link com.urbanairship.analytics.Analytics#addAnalyticsListener(AnalyticsListener)}.
 */
public interface AnalyticsListener {
    /**
     * Called when a new screen is tracked.
     *
     * @param screenName The screen name.
     */
    void onScreenTracked(String screenName);

    /**
     * Called when a new custom event is tracked.
     *
     * @param event The custom event.
     */
    void onCustomEventAdded(CustomEvent event);

    /**
     * Called when a new region event is generated.
     *
     * @param event The region event.
     */
    void onRegionEventAdded(RegionEvent event);
}
