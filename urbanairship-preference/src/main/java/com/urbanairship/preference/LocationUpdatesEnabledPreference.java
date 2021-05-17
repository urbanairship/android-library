/* Copyright Airship and Contributors */

package com.urbanairship.preference;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.AttributeSet;

import com.urbanairship.AirshipExecutors;
import com.urbanairship.PendingResult;
import com.urbanairship.PrivacyManager;
import com.urbanairship.ResultCallback;
import com.urbanairship.UAirship;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.util.HelperActivity;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * CheckboxPreference to enable/disable location updates.
 */
public class LocationUpdatesEnabledPreference extends UACheckBoxPreference {
    private final Executor executor = AirshipExecutors.newSerialExecutor();

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public LocationUpdatesEnabledPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LocationUpdatesEnabledPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LocationUpdatesEnabledPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private final PrivacyManager.Listener privacyManagerListener = new PrivacyManager.Listener() {
        @Override
        public void onEnabledFeaturesChanged() {
            AirshipLocationClient client = UAirship.shared().getLocationClient();
            boolean isEnabled = client != null && client.isLocationUpdatesEnabled() && isPermissionGranted() && UAirship.shared().getPrivacyManager().isEnabled(PrivacyManager.FEATURE_LOCATION);
            setEnabled(isEnabled);
        }
    };

    @Override
    public void onAttached() {
        super.onAttached();
        UAirship.shared().getPrivacyManager().addListener(privacyManagerListener);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        UAirship.shared().getPrivacyManager().removeListener(privacyManagerListener);
    }

    @Override
    public void setChecked(boolean value) {
        final WeakReference<LocationUpdatesEnabledPreference> weakReference = new WeakReference<>(this);

        if (isChecked != value && value && shouldRequestPermissions()) {
            final PendingResult<Boolean> checkedResult = new PendingResult<>();

            checkedResult.addResultCallback(new ResultCallback<Boolean>() {
                @Override
                public void onResult(@Nullable Boolean result) {
                    LocationUpdatesEnabledPreference preference = weakReference.get();
                    if (preference != null) {
                        preference.setChecked(result == null ? false : result);
                    }
                }
            });

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    int[] result = HelperActivity.requestPermissions(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION);
                    boolean checked = false;
                    for (int element : result) {
                        if (element == PackageManager.PERMISSION_GRANTED) {
                            checked = true;
                            break;
                        }
                    }

                    checkedResult.setResult(checked);
                }
            });
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected boolean getInitialAirshipValue(@NonNull UAirship airship) {
        if (airship.getLocationClient() != null && isPermissionGranted() && airship.getPrivacyManager().isEnabled(PrivacyManager.FEATURE_LOCATION)) {
            return airship.getLocationClient().isLocationUpdatesEnabled();
        } else {
            return false;
        }
    }

    private boolean isPermissionGranted() {
        return (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_DENIED &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_DENIED);
    }

    @Override
    protected void onApplyAirshipPreference(@NonNull UAirship airship, boolean enabled) {
        if (airship.getLocationClient() != null) {
            airship.getLocationClient().setLocationUpdatesEnabled(enabled);
        }

        if (enabled) {
            airship.getPrivacyManager().enable(PrivacyManager.FEATURE_LOCATION);
        }
    }

    @Override
    public boolean isEnabled() {
        return UAirship.shared().getLocationClient() != null && super.isEnabled();
    }
}
