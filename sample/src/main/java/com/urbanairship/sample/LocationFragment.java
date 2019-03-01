/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.urbanairship.PendingResult;
import com.urbanairship.ResultCallback;
import com.urbanairship.UAirship;
import com.urbanairship.location.LocationRequestOptions;

/**
 * Fragment that lets the user get its current location using
 * the Urban Airship location APIs.
 */
public class LocationFragment extends Fragment {

    private PendingResult<Location> pendingResult;
    private RadioGroup priorityGroup;
    private View progress;
    static final int PERMISSIONS_REQUEST_LOCATION = 100;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_location, container, false);

        priorityGroup = (RadioGroup) view.findViewById(R.id.location_priority);
        progress = view.findViewById(R.id.request_progress);
        progress.setVisibility(View.INVISIBLE);

        Button button = (Button) view.findViewById(R.id.request_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestLocation();
            }
        });

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        // Cancel the request
        if (pendingResult != null) {
            pendingResult.cancel();
            progress.setVisibility(View.INVISIBLE);
        }
    }

    private void requestLocation() {
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION }, PERMISSIONS_REQUEST_LOCATION);
            return;
        }

        if (pendingResult != null) {
            pendingResult.cancel();
        }

        progress.setVisibility(View.VISIBLE);

        LocationRequestOptions options = LocationRequestOptions.newBuilder()
                                                               .setPriority(getPriority())
                                                               .build();

        pendingResult = UAirship.shared()
                                .getLocationManager()
                                .requestSingleLocation(options)
                                .addResultCallback(Looper.getMainLooper(), new ResultCallback<Location>() {
                                    @Override
                                    public void onResult(@Nullable Location result) {
                                        progress.setVisibility(View.INVISIBLE);

                                        if (result != null) {
                                            Toast.makeText(getContext(), formatLocation(result), Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(getContext(), "Failed to get location", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, now request location.
                    requestLocation();
                } else {
                    // permission denied, let them know location permissions is required.
                    Toast.makeText(getContext(), "Enable location permissions and try again.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String formatLocation(Location location) {
        return String.format("provider: %s lat: %s, lon: %s, accuracy: %s",
                location.getProvider(),
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy());
    }

    /**
     * Gets the LocationRequestOptions priority from the radio group.
     *
     * @return The location request options priority.
     */
    @LocationRequestOptions.Priority
    private int getPriority() {
        switch (priorityGroup.getCheckedRadioButtonId()) {
            case R.id.priority_high_accuracy:
                return LocationRequestOptions.PRIORITY_HIGH_ACCURACY;
            case R.id.priority_balanced:
                return LocationRequestOptions.PRIORITY_BALANCED_POWER_ACCURACY;
            case R.id.priority_low_power:
                return LocationRequestOptions.PRIORITY_LOW_POWER;
            case R.id.priority_no_power:
                return LocationRequestOptions.PRIORITY_NO_POWER;
        }

        return LocationRequestOptions.PRIORITY_BALANCED_POWER_ACCURACY;
    }

}
