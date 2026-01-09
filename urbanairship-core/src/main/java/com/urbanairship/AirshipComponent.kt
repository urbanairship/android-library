/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import android.net.Uri
import androidx.annotation.CallSuper
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult

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
     * Initialize the manager.
     * Called in [Airship] during takeoff.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @CallSuper
    public open fun init() {

    }

    /**
     * Tear down the manager.
     * Called in [Airship] during land.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open fun tearDown() { }

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


/**
 * Temporary class to handle jobs in the traditional way until we migrate to new APIs.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class JobAwareAirshipComponent(
    context: Context,
    dataStore: PreferenceDataStore,
    jobDispatcher: JobDispatcher = JobDispatcher.shared(context)
) : AirshipComponent(context, dataStore) {

    protected abstract val jobActions: List<String>

    /**
     * Called when a scheduled [JobInfo] is ready to perform.
     *
     * @param jobInfo The JobInfo.
     * @return The result of the jobInfo.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open suspend fun onPerformJob(jobInfo: JobInfo): JobResult {
        return JobResult.SUCCESS
    }

    init {
        jobDispatcher.addWeakJobHandler(this, jobActions) {
            onPerformJob(it)
        }
    }
}
