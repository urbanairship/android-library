/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * Helper class that keeps track of the schedule's adapter, assets, and execution callback.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
final class AdapterWrapper {
    @NonNull
    public final String scheduleId;
    @NonNull
    public final InAppMessage message;
    @NonNull
    public final InAppMessageAdapter adapter;

    public boolean displayed = false;
    private boolean prepareCalled = false;

    AdapterWrapper(@NonNull String scheduleId, @NonNull InAppMessage message, @NonNull InAppMessageAdapter adapter) {
        this.scheduleId = scheduleId;
        this.message = message;
        this.adapter = adapter;
    }

    @InAppMessageAdapter.PrepareResult
    int prepare() {
        try {
            Logger.debug("InAppMessageManager - Preparing schedule: %s", scheduleId);

            @InAppMessageAdapter.PrepareResult
            int result = adapter.onPrepare(UAirship.getApplicationContext());
            prepareCalled = true;
            return result;
        } catch (Exception e) {
            Logger.error(e, "InAppMessageManager - Failed to prepare in-app message.");
            return InAppMessageAdapter.RETRY;
        }
    }

    boolean display(@NonNull Activity activity) {
        Logger.debug("InAppMessageManager - Displaying schedule: %s", scheduleId);
        try {
            DisplayHandler displayHandler = new DisplayHandler(scheduleId);
            if (adapter.onDisplay(activity, displayed, displayHandler)) {
                displayed = true;
                return true;
            }

            return false;
        } catch (Exception e) {
            Logger.error(e, "InAppMessageManager - Failed to display in-app message.");
            return false;
        }
    }

    void finish() {
        Logger.debug("InAppMessageManager - Schedule finished: %s", scheduleId);

        try {
            if (prepareCalled) {
                adapter.onFinish();
            }
        } catch (Exception e) {
            Logger.error(e, "InAppMessageManager - Exception during onFinish().");
        }
    }

    boolean isReady(@NonNull Activity activity) {
        try {
            return adapter.isReady(activity);
        } catch (Exception e) {
            Logger.error(e, "InAppMessageManager - Exception during isReady(Activity).");
            return false;
        }
    }
}
