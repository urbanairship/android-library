/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import android.net.Uri
import androidx.annotation.CallSuper
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import java.util.concurrent.Executor

/**
 * Base class for Airship components.
 */
public abstract class AirshipComponent @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
    context: Context,
    /**
     * @property dataStore The preference data store.
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected val dataStore: PreferenceDataStore
) {

    /**
     * The application context.
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected val context: Context = context.applicationContext

    /**
     * Default job executor.
     */
    protected val defaultExecutor: Executor = AirshipExecutors.newSerialExecutor()

    /**
     * Initialize the manager.
     * Called in [UAirship] during takeoff.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @CallSuper
    public open fun init() { }

    /**
     * Tear down the manager.
     * Called in [UAirship] during land.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open fun tearDown() { }

    /**
     * Gets the executor for the given job.
     *
     * @param jobInfo The jobInfo.
     * @return An executor that will be used to call [onPerformJob].
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getJobExecutor(jobInfo: JobInfo): Executor {
        return defaultExecutor
    }

    /**
     * Called when a scheduled [JobInfo] is ready to perform.
     *
     * @param airship The airship instance.
     * @param jobInfo The JobInfo.
     * @return The result of the jobInfo.
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open fun onPerformJob(airship: UAirship, jobInfo: JobInfo): JobResult {
        return JobResult.SUCCESS
    }

    /**
     * Called when airship instance is ready.
     *
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open fun onAirshipReady() { }

    /**
     * Called to handle `uairship://` deep links.
     *
     * @param uri The deep link.
     * @return true if the deep link was handled, otherwise false.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open fun onAirshipDeepLink(uri: Uri): Boolean {
        return false
    }
}
