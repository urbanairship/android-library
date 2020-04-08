package com.urbanairship.iam;

import android.content.Context;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

/**
 * Display adapter that is only ready when the application has at least one resumed activity that
 * is not excluded using {@link InAppActivityMonitor#EXCLUDE_FROM_AUTO_SHOW}.
 */
public abstract class ForegroundDisplayAdapter implements InAppMessageAdapter {

    @CallSuper
    @Override
    public boolean isReady(@NonNull Context context) {
        return !InAppActivityMonitor.shared(context).getResumedActivities().isEmpty();
    }

}
