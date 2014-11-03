package com.urbanairship.analytics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.os.Bundle;

/**
 * This helper class registers lifecycle callbacks.
 */
@TargetApi(14)
abstract class LifeCycleCallbacks {
    private ActivityLifecycleCallbacks activityLifecycleCallbacks;
    private boolean isRegistered = false;

    public LifeCycleCallbacks() {
        activityLifecycleCallbacks = new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {}

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
     * @param application The application.
     */
    public void register(Application application) {
        if (!isRegistered) {
            application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
            isRegistered = true;
        }
    }

    /**
     * This unregisters the activity callbacks for an application.
     *
     * @param application The application.
     */
    public void unregister(Application application) {
        if (isRegistered) {
            application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks);
            isRegistered = false;
        }
    }

    /**
     * Called when an activity is stopped.
     *
     * @param activity The activity that stopped.
     */
    public abstract void onActivityStopped(Activity activity);

    /**
     * Called when an activity is started.
     *
     * @param activity The activity that started.
     */
    public abstract void onActivityStarted(Activity activity);
}
