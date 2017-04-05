/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.preference;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;

import com.urbanairship.UAirship;
import com.urbanairship.util.HelperActivity;

import java.lang.ref.WeakReference;

/**
 * CheckboxPreference to enable/disable location updates.
 */
public class LocationUpdatesEnabledPreference extends UACheckBoxPreference {

    private static final String CONTENT_DESCRIPTION = "LOCATION_UPDATES_ENABLED";

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public LocationUpdatesEnabledPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LocationUpdatesEnabledPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LocationUpdatesEnabledPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setChecked(boolean value) {
        if (isChecked != value && value && shouldRequestPermissions()) {
            RequestPermissionsTask task = new RequestPermissionsTask(getContext(), this);
            String[] permissions = new String[] {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, permissions);
        } else {
            super.setChecked(value);
        }
    }

    /**
     * Determines if we should request permissions
     *
     * @return {@code true} if permissions should be requested, otherwise {@code false}.
     */
    private boolean shouldRequestPermissions() {
        if (Build.VERSION.SDK_INT < 23) {
            return false;
        }

        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED;
    }

    @Override
    protected boolean getInitialAirshipValue(UAirship airship) {
        return airship.getLocationManager().isLocationUpdatesEnabled();
    }

    @Override
    protected void onApplyAirshipPreference(UAirship airship, boolean enabled) {
        airship.getLocationManager().setLocationUpdatesEnabled(enabled);
    }

    @Override
    protected String getContentDescription() {
        return CONTENT_DESCRIPTION;
    }

    private static class RequestPermissionsTask extends AsyncTask<String[], Void, Boolean> {

        private final Context context;
        private WeakReference<LocationUpdatesEnabledPreference> weakReference;

        RequestPermissionsTask(Context context, LocationUpdatesEnabledPreference preference) {
            this.context = context;
            this.weakReference = new WeakReference<LocationUpdatesEnabledPreference>(preference);
        }

        @Override
        protected Boolean doInBackground(String[]... strings) {
            int[] result = HelperActivity.requestPermissions(context, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION);
            for (int element : result) {
                if (element == PackageManager.PERMISSION_GRANTED) {
                    return true;
                }
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            LocationUpdatesEnabledPreference preference = weakReference.get();
            if (preference != null) {
                preference.setChecked(result);
            }
        }
    }
}
