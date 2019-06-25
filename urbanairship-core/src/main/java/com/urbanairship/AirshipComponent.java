/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonList;

import java.util.concurrent.Executor;

/**
 * Base class for Airship components.
 */
public abstract class AirshipComponent {

    private static final String ENABLE_KEY_PREFIX = "airshipComponent.enable_";

    private final PreferenceDataStore dataStore;
    private final String enableKey;
    private final Context context;

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AirshipComponent(@NonNull Context context, @NonNull PreferenceDataStore dataStore) {
        this.context = context.getApplicationContext();
        this.dataStore = dataStore;
        this.enableKey = ENABLE_KEY_PREFIX + getClass().getName();
    }

    /**
     * Default job executor.
     */
    private final Executor jobExecutor = AirshipExecutors.newSerialExecutor();

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
            public void onPreferenceChange(@NonNull String key) {
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
    protected void tearDown() {
    }

    /**
     * Gets the executor for the given job.
     *
     * @param jobInfo The jobInfo.
     * @return An executor that will be used to call {@link #onPerformJob(UAirship, JobInfo)}.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Executor getJobExecutor(@NonNull JobInfo jobInfo) {
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
    public int onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
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
    protected void onAirshipReady(@NonNull UAirship airship) {
    }

    /**
     * Called when the component is enabled or disabled.
     *
     * @param isEnabled {@code true} if the component is enabled, otherwise {@code false}.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void onComponentEnableChange(boolean isEnabled) {
    }

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
    @NonNull
    protected PreferenceDataStore getDataStore() {
        return dataStore;
    }

    /**
     * The application context.
     *
     * @return The application context.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    protected Context getContext() {
        return context;
    }

    /**
     * Called when a component gets new remote config.
     *
     * @param value The config.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onNewConfig(@NonNull JsonList value) {

    }

}
