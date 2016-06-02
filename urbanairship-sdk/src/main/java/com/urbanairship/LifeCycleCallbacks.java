/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.os.Bundle;

/**
 * This helper class registers lifecycle callbacks.
 */
@TargetApi(14)
public abstract class LifeCycleCallbacks {
    private final Application application;
    private final ActivityLifecycleCallbacks activityLifecycleCallbacks;
    private boolean isRegistered = false;

    /**
     * Default constructor.
     */
    public LifeCycleCallbacks(Application application) {
        this.application = application;
        activityLifecycleCallbacks = new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPaused(Activity activity) {
                LifeCycleCallbacks.this.onActivityPaused(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                LifeCycleCallbacks.this.onActivityResumed(activity);
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityStarted(Activity activity) {
                LifeCycleCallbacks.this.onActivityStarted(activity);
            }

            @Override
            public void onActivityStopped(Activity activity) {
                LifeCycleCallbacks.this.onActivityStopped(activity);
            }
        };
    }

    /**
     * This registers the activity callbacks for an application.
     *
     */
    public void register() {
        if (!isRegistered) {
            application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
            isRegistered = true;
        }
    }

    /**
     * This unregisters the activity callbacks for an application.
     */
    public void unregister() {
        if (isRegistered) {
            application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks);
            isRegistered = false;
        }
    }

    /**
     * Called when an activity is stopped.
     *
     * @param activity The stopped activity.
     */
    public void onActivityStopped(Activity activity) {}

    /**
     * Called when an activity is started.
     *
     * @param activity The started activity.
     */
    public void onActivityStarted(Activity activity) {}

    /**
     * Called when an activity is paused.
     *
     * @param activity The paused activity.
     */
    public void onActivityPaused(Activity activity) {}

    /**
     * Called when an activity is resumed.
     *
     * @param activity The resumed activity.
     */
    public void onActivityResumed(Activity activity) {}
}
