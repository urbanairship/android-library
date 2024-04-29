package com.urbanairship.automation.test;

import android.app.Activity;

import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.Collections;
import java.util.List;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

@Implements(InAppActivityMonitor.class)
public class ShadowInAppActivityMonitor {
    public boolean hasResumedActivities = false;

    @Implementation
    @NonNull
    @MainThread
    public List<Activity> getResumedActivities() {
        if (hasResumedActivities) {
            return Collections.singletonList(new Activity());
        } else {
            return Collections.emptyList();
        }
    }
}
