/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import com.urbanairship.job.JobInfo;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Base class for Urban Airship components.
 */
public abstract class AirshipComponent {

    private static final String ENABLE_KEY_PREFIX = "airshipComponent.enable_";

    private final PreferenceDataStore dataStore;
    private final String enableKey;


    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AirshipComponent(PreferenceDataStore dataStore) {
        this.dataStore = dataStore;
        this.enableKey = ENABLE_KEY_PREFIX + getClass().getName();
    }

    /**
     * Default job executor.
     */
    private Executor jobExecutor = Executors.newSingleThreadExecutor();

    /**
     * Initialize the manager.
     * Called in {@link UAirship} during takeoff.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @CallSuper
    protected void init() {
        dataStore.addListener(new PreferenceDataStore.PreferenceChangeListener() {
            @Override
            public void onPreferenceChange(String key) {
                if (key.equals(enableKey)) {
                    onComponentEnableChange(isComponentEnabled());
                }
            }
        });
    }

    /**
     * Tear down the manager.
     * Called in {@link UAirship} during land.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void tearDown() {}

    /**
     * Gets the executor for the given job.
     *
     * @param jobInfo The jobInfo.
     * @return An executor that will be used to call {@link #onPerformJob(UAirship, JobInfo)}.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Executor getJobExecutor(JobInfo jobInfo) {
        return jobExecutor;
    }

    /**
     * Called when a scheduled {@link JobInfo} is ready to perform.
     *
     * @param airship The airship instance.
     * @param jobInfo The JobInfo.
     * @return The result of the jobInfo.
     * @hide
     */
    @WorkerThread
    @JobInfo.JobResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onPerformJob(@NonNull UAirship airship, JobInfo jobInfo) {
        return JobInfo.JOB_FINISHED;
    }

    /**
     * Called when airship instance is ready.
     *
     * @param airship The airship instance.
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void onAirshipReady(UAirship airship) {}

    /**
     * Called when the component is enabled or disabled.
     *
     * @param isEnabled {@code true} if the component is enabled, otherwise {@code false}.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void onComponentEnableChange(boolean isEnabled) {}

    /**
     * Enables/disables the component.
     * Disabled components not receive calls to {@link #onPerformJob(UAirship, JobInfo)}.
     *
     * @param enabled {@code true} to enable the component, {@code false} to disable.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void setComponentEnabled(boolean enabled) {
        dataStore.put(enableKey, enabled);
    }

    /**
     * Checks if the component is enabled.
     *
     * @return {@code true} if enabled, otherwise {@code false}.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public boolean isComponentEnabled() {
        return dataStore.getBoolean(enableKey, true);
    }

    /**
     * The preference data store.
     *
     * @return The preference data store.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected PreferenceDataStore getDataStore() {
        return dataStore;
    }
}
