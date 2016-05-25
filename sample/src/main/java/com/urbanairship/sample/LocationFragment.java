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

package com.urbanairship.sample;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.urbanairship.Cancelable;
import com.urbanairship.UAirship;
import com.urbanairship.location.LocationCallback;
import com.urbanairship.location.LocationRequestOptions;


/**
 * Fragment that lets the user get its current location using
 * the Urban Airship location APIs.
 */
public class LocationFragment extends Fragment {

    private Cancelable pendingRequest;
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
        if (pendingRequest != null) {
            pendingRequest.cancel();
            progress.setVisibility(View.INVISIBLE);
        }
    }

    private void requestLocation() {
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION }, PERMISSIONS_REQUEST_LOCATION);
            return;
        }

        if (pendingRequest != null) {
            pendingRequest.cancel();
        }

        progress.setVisibility(View.VISIBLE);

        LocationRequestOptions options = new LocationRequestOptions.Builder()
                .setPriority(getPriority())
                .create();

        LocationCallback callback = new LocationCallback() {
            @Override
            public void onResult(Location location) {
                progress.setVisibility(View.INVISIBLE);

                if (location != null) {
                    Toast.makeText(getContext(), formatLocation(location), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Failed to get location", Toast.LENGTH_SHORT).show();
                }
            }
        };

        pendingRequest = UAirship.shared().getLocationManager().requestSingleLocation(callback, options);
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
