/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import android.net.Uri;

import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;
import com.urbanairship.json.JsonMap;

import java.util.concurrent.Executor;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

/**
 * Base class for Airship components.
 */
public abstract class AirshipComponent {

    private final PreferenceDataStore dataStore;
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
    }

    /**
     * Default job executor.
     */
    protected final Executor defaultExecutor = AirshipExecutors.newSerialExecutor();

    /**
     * Initialize the manager.
     * Called in {@link UAirship} during takeoff.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @CallSuper
    protected void init() {
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
        return defaultExecutor;
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
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JobResult onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        return JobResult.SUCCESS;
    }

    /**
     * Called when airship instance is ready.
     *
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void onAirshipReady() {
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
     * Called to handle `uairship://` deep links.
     *
     * @param uri The deep link.
     * @return true if the deep link was handled, otherwise false.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public boolean onAirshipDeepLink(@NonNull Uri uri) {
        return false;
    }
}
