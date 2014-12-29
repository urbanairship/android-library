package com.urbanairship.analytics;

import com.urbanairship.Logger;
import com.urbanairship.analytics.ActivityMonitor.Source;

/**
 * This class keeps track of an activity's foreground state
 */
class ActivityState {
    /**
     * An enum representing the three possible states for an activity
     */
    private enum State {
        /**
         * The activity has started.
         */
        STARTED,
        /**
         * The activity has stopped.
         */
        STOPPED,
        /**
         * The activity's initial state.
         */
        NONE
    }

    final private String activityName;
    private State autoInstrumentedState = State.NONE;
    private State manualInstrumentedState = State.NONE;
    private int minSdkVersion;
    private int currentSdkVersion;
    private boolean analyticsEnabled;
    private long lastModifiedTimeMS = 0;

    /**
     * The ActivityState constructor
     *
     * @param activityName The name of the activity to be display to the user if we detect invalid usage
     * @param minSdkVersion The minimum SDK version user specified
     * @param currentSdkVersion The current SDK version the app is currently running on
     */
    public ActivityState(String activityName, int minSdkVersion, int currentSdkVersion, boolean analyticsEnabled) {
        this.activityName = activityName;
        this.minSdkVersion = minSdkVersion;
        this.currentSdkVersion = currentSdkVersion;
        this.analyticsEnabled = analyticsEnabled;
        this.lastModifiedTimeMS = System.currentTimeMillis();
    }

    /**
     * The boolean that indicates if the activity's state is in the foreground
     *
     * @return <code>true</code> if the activity is currently in the foreground, otherwise <code>false</code>.
     */
    boolean isForeground() {
        if (currentSdkVersion >= 14) {
            return autoInstrumentedState == State.STARTED;
        }

        return manualInstrumentedState == State.STARTED;
    }

    /**
     * The time of the last start or stop in milliseconds.
     *
     * @return The time of the last start or stop in milliseconds.
     */
    long getLastModifiedTime() {
        return lastModifiedTimeMS;
    }

    /**
     * Tracks when the activity started
     *
     * @param source Specifies how the activity was started manually or automatically
     * @param startTimeMS The timestamp in milliseconds when the activity started.
     */
    void setStarted(Source source, long startTimeMS) {
        if (source == Source.MANUAL_INSTRUMENTATION) {
            if (manualInstrumentedState == State.STARTED && analyticsEnabled) {
                Logger.warn("Activity " + activityName + " already added without being removed first. Call Analytics.activityStopped(this) in every activity's onStop() method.");
            }
            manualInstrumentedState = State.STARTED;
        } else {
            autoInstrumentedState = State.STARTED;
        }

        this.lastModifiedTimeMS = startTimeMS;
    }

    /**
     * Tracks when the activity stopped
     *
     * @param source Specifies how the activity was stopped manually or automatically
     * @param stopTimeMS The timestamp in milliseconds when the activity stopped.
     */
    void setStopped(Source source, long stopTimeMS) {
        if (source == Source.MANUAL_INSTRUMENTATION) {
            if (manualInstrumentedState != State.STARTED && analyticsEnabled) {
                Logger.warn("Activity " + activityName + " removed without being manually added first. Call Analytics.activityStarted(this) in every activity's onStart() method.");
            } else if (currentSdkVersion >= 14 && autoInstrumentedState == State.NONE && analyticsEnabled) {
                Logger.warn("Activity " + activityName + " removed in Analytics not during the activity's onStop() method.");
            }
            manualInstrumentedState = State.STOPPED;
        } else {
            if (minSdkVersion < 14 && manualInstrumentedState == State.NONE && analyticsEnabled) {
                Logger.warn("Activity " + activityName + " was not manually added during onStart(). Call Analytics.activityStarted(this) in every activity's onStart() method.");
            }
            autoInstrumentedState = State.STOPPED;
        }

        this.lastModifiedTimeMS = stopTimeMS;
    }


}
