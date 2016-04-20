package com.urbanairship.analytics;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonException;

/**
 * Analytics service preferences.
 */
class AnalyticsPreferences {

    private static final String KEY_PREFIX = "com.urbanairship.analytics";
    private static final String MAX_TOTAL_DB_SIZE_KEY = KEY_PREFIX + ".MAX_TOTAL_DB_SIZE";
    private static final String MAX_BATCH_SIZE_KEY = KEY_PREFIX + ".MAX_BATCH_SIZE";
    private static final String MAX_WAIT_KEY = KEY_PREFIX + ".MAX_WAIT";
    private static final String MIN_BATCH_INTERVAL_KEY = KEY_PREFIX + ".MIN_BATCH_INTERVAL";
    private static final String LAST_SEND_KEY = KEY_PREFIX + ".LAST_SEND";
    private static final String SCHEDULED_SEND_TIME = KEY_PREFIX + ".SCHEDULED_SEND_TIME";
    private static final String ANALYTICS_ENABLED_KEY = KEY_PREFIX + ".ANALYTICS_ENABLED";
    private static final String ASSOCIATED_IDENTIFIERS_KEY = KEY_PREFIX + ".ASSOCIATED_IDENTIFIERS";
    private static final String ADVERTISING_ID_AUTO_TRACKING_KEY = KEY_PREFIX + ".ADVERTISING_ID_TRACKING";

    static final int MAX_TOTAL_DB_SIZE_BYTES = 5 * 1024 * 1024; //5 MB
    static final int MIN_TOTAL_DB_SIZE_BYTES = 10 * 1024;       //10 KB

    static final int MAX_BATCH_SIZE_BYTES = 500 * 1024; //500 KB
    static final int MIN_BATCH_SIZE_BYTES = 1024;       //1 KB

    static final int MAX_WAIT_MS = 14 * 24 * 3600 * 1000;   // 14 days
    static final int MIN_WAIT_MS = 7 * 24 * 3600 * 1000;    // 7 days

    static final int MIN_BATCH_INTERVAL_MS = 60 * 1000;             // 60 seconds
    static final int MAX_BATCH_INTERVAL_MS = 7 * 24 * 3600 * 1000;  // 7 days
    private final PreferenceDataStore preferenceDataStore;

    AnalyticsPreferences(PreferenceDataStore preferenceDataStore) {
        this.preferenceDataStore = preferenceDataStore;
    }

    /**
     * Returns the event database max size in bytes.
     *
     * @return Max size of the event database in bytes.
     */
    int getMaxTotalDbSize() {
        return preferenceDataStore.getInt(MAX_TOTAL_DB_SIZE_KEY, MAX_TOTAL_DB_SIZE_BYTES);
    }

    /**
     * Sets the event database max size in bytes.
     *
     * @param maxTotalDbSize Max size of the event database in bytes.
     */
    void setMaxTotalDbSize(int maxTotalDbSize) {
        preferenceDataStore.put(MAX_TOTAL_DB_SIZE_KEY, maxTotalDbSize);
    }

    /**
     * Returns the max batch size of events in bytes to upload to analytics.
     *
     * @return The max batch size in bytes.
     */
    int getMaxBatchSize() {
        return preferenceDataStore.getInt(MAX_BATCH_SIZE_KEY, MAX_BATCH_SIZE_BYTES);
    }

    /**
     * Sets the max batch size of events in bytes to upload to analytics.
     *
     * @param maxBatchSize The max batch size in bytes.
     */
    void setMaxBatchSize(int maxBatchSize) {
        preferenceDataStore.put(MAX_BATCH_SIZE_KEY, maxBatchSize);
    }

    /**
     * Returns the upper bound on the send interval in milliseconds.
     *
     * @return The max wait time in milliseconds.
     */
    int getMaxWait() {
        return preferenceDataStore.getInt(MAX_WAIT_KEY, MAX_WAIT_MS);
    }

    /**
     * Sets the upper bound on the send interval in milliseconds.
     *
     * @param maxWait Sets the max wait time in milliseconds.
     */
    void setMaxWait(int maxWait) {
        preferenceDataStore.put(MAX_WAIT_KEY, maxWait);
    }

    /**
     * Returns the lower bound on the send interval in milliseconds.
     *
     * @return The min batch interval time in milliseconds.
     */
    int getMinBatchInterval() {
        return preferenceDataStore.getInt(MIN_BATCH_INTERVAL_KEY, MIN_BATCH_INTERVAL_MS);
    }

    /**
     * Sets the lower bound on the send interval in milliseconds.
     *
     * @param minBatchInterval Sets the min batch interval time in milliseconds.
     */
    void setMinBatchInterval(int minBatchInterval) {
        preferenceDataStore.put(MIN_BATCH_INTERVAL_KEY, minBatchInterval);
    }

    /**
     * Returns the last event upload time in milliseconds since January 1, 1970 00:00:00.0 UTC
     *
     * @return The last send time in milliseconds.
     */
    long getLastSendTime() {
        return preferenceDataStore.getLong(LAST_SEND_KEY, 0);
    }

    /**
     * Sets the last event upload time in milliseconds.
     *
     * @param lastSendTime Last send time in milliseconds.
     */
    void setLastSendTime(long lastSendTime) {
        preferenceDataStore.put(LAST_SEND_KEY, lastSendTime);
    }

    /**
     * Gets the scheduled event upload time in milliseconds.
     *
     * @return next scheduled event upload time in milliseconds.
     */
    long getScheduledSendTime() {
        return preferenceDataStore.getLong(SCHEDULED_SEND_TIME, 0);
    }

    /**
     * Sets the scheduled send time in milliseconds for event uploads.
     *
     * @param scheduledSendTime Last send time in milliseconds.
     */
    void setScheduledSendTime(long scheduledSendTime) {
        preferenceDataStore.put(SCHEDULED_SEND_TIME, scheduledSendTime);
    }

    /**
     * Sets analytics enabled flag.
     *
     * @param enabled {@code true} to enable, {@code false} to disable.
     */
    void setAnalyticsEnabled(boolean enabled) {
        preferenceDataStore.put(ANALYTICS_ENABLED_KEY, enabled);
    }

    /**
     * Returns the analytics enable flag.
     *
     * @return {@code true} if enabled, otherwise {@code false}.
     */
    boolean isAnalyticsEnabled() {
        return preferenceDataStore.getBoolean(ANALYTICS_ENABLED_KEY, true);
    }

    /**
     * Sets the associated identifiers.
     *
     * @param identifiers The AssociatedIdentifiers object.
     */
    void setIdentifiers(@Nullable AssociatedIdentifiers identifiers) {
        if (identifiers != null) {
            preferenceDataStore.put(ASSOCIATED_IDENTIFIERS_KEY, identifiers);
        } else {
            preferenceDataStore.remove(ASSOCIATED_IDENTIFIERS_KEY);
        }
    }

    /**
     * Sets the ad ID auto tracking enabled flag.
     *
     * @param enabled {@code true} to enable, {@code false} to disable.
     */
    void setAutoTrackAdvertisingIdEnabled(boolean enabled) {
        preferenceDataStore.put(ADVERTISING_ID_AUTO_TRACKING_KEY, enabled);
    }

    /**
     * Returns the tracking ad ID auto enabled flag.
     *
     * @return {@code true} if enabled, otherwise {@code false}.
     */
    boolean isAutoTrackAdvertisingIdEnabled() {
        return preferenceDataStore.getBoolean(ADVERTISING_ID_AUTO_TRACKING_KEY, false);
    }

    /**
     * Returns the current associated identifiers.
     *
     * @return The current associated identifiers.
     */
    @NonNull
    AssociatedIdentifiers getIdentifiers() {
        try {
            return AssociatedIdentifiers.fromJson(preferenceDataStore.getString(ASSOCIATED_IDENTIFIERS_KEY, null));
        } catch (JsonException e) {
            Logger.debug("Unable to parse associated identifiers.", e);
            preferenceDataStore.remove(ASSOCIATED_IDENTIFIERS_KEY);
        }

        return new AssociatedIdentifiers();
    }
}
