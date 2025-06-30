/* Copyright Airship and Contributors */
package com.urbanairship.app

import android.app.Activity
import android.os.Bundle

/**
 * A convenience class to extend when you only want to listen for a subset
 * of of activity events.
 */
public open class SimpleActivityListener public constructor() : ActivityListener {

    override fun onActivityPaused(activity: Activity) { }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) { }

    override fun onActivityStarted(activity: Activity) { }

    override fun onActivityResumed(activity: Activity) { }

    override fun onActivityStopped(activity: Activity) { }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) { }

    override fun onActivityDestroyed(activity: Activity) { }
}
