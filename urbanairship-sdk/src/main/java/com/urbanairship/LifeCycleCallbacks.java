/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
