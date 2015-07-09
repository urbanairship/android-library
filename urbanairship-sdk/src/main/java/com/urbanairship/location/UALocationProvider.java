/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;

import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.google.PlayServicesUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Location provider that automatically selects between the standard android location
 * and the fused location.  This class is not thread safe.
 */
class UALocationProvider {

    private List<LocationAdapter> adapters = new ArrayList<>();
    private LocationAdapter connectedAdapter;
    private boolean isConnected = false;

    /**
     * UALocationProvider constructor.
     *
     * @param context The application context.
     */
    public UALocationProvider(Context context) {
        if (PlayServicesUtils.isFusedLocationDepdendencyAvailable()) {
            adapters.add(new FusedLocationAdapter(context));
        }

        adapters.add(new StandardLocationAdapter(context));
    }

    UALocationProvider(LocationAdapter... adapters) {
        this.adapters.addAll(Arrays.asList(adapters));
    }

    /**
     * Cancels all location requests for a given pending intent.
     *
     * @param intent The intent to cancel.
     */
    public void cancelRequests(PendingIntent intent) {
        Logger.verbose("UALocationProvider - Canceling location requests.");

        /*
         * Need to cancel requests on all providers regardless of the current
         * connected provider because pending intents persist between app starts
         * and there is no way to determine what provider was used previously.
         */
        for (LocationAdapter adapter : adapters) {
            Logger.verbose("UALocationProvider - Canceling location requests for adapter: " + adapter);

            if (adapter == connectedAdapter || adapter.connect()) {
                try {
                    adapter.cancelLocationUpdates(intent);
                } catch (SecurityException ex) {
                    Logger.verbose("Unable to cancel location updates: " + ex.getMessage());
                }
            }

            if (adapter != connectedAdapter) {
                adapter.disconnect();
            }
        }
    }

    /**
     * Requests location updates.
     *
     * @param options The request options.
     * @param intent A pending intent to be sent for each location update.
     * @throws IllegalStateException if the provider is not connected.
     */
    public void requestLocationUpdates(LocationRequestOptions options, PendingIntent intent) {
        if (!isConnected) {
            throw new IllegalStateException("Provider must be connected before making requests.");
        }

        if (connectedAdapter == null) {
            Logger.debug("UALocationProvider - Ignoring request, connected adapter unavailable.");
            return;
        }

        Logger.verbose("UALocationProvider - Requesting location updates: " + options);
        try {
            connectedAdapter.requestLocationUpdates(options, intent);
        } catch (SecurityException ex) {
            Logger.error("Unable to request location updates: " + ex.getMessage());
        }
    }

    /**
     * Requests a single location update.
     *
     * @param options The request options.
     * @return A pending location result.
     * @throws IllegalStateException if the provider is not connected.
     */
    public PendingResult<Location> requestSingleLocation(LocationRequestOptions options) {
        if (!isConnected) {
            throw new IllegalStateException("Provider must be connected before making requests.");
        }

        if (connectedAdapter == null) {
            Logger.debug("UALocationProvider - Ignoring request, connected adapter unavailable.");
            return null;
        }

        Logger.verbose("UALocationProvider - Requesting single location update: " + options);

        try {
            return connectedAdapter.requestSingleLocation(options);
        } catch (SecurityException ex) {
            Logger.error("Unable to request location: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Connects to the provider. This method blocks.
     */
    public void connect() {
        if (isConnected) {
            return;
        }

        for (LocationAdapter adapter : adapters) {
            Logger.verbose("UALocationProvider - Attempting to connect to location adapter: " + adapter);
            if (adapter.connect()) {
                Logger.verbose("UALocationProvider - Connected to location adapter: " + adapter);
                connectedAdapter = adapter;
                break;
            } else {
                Logger.verbose("UALocationProvider - Failed to connect to location adapter: " + adapter);
            }
        }

        isConnected = true;
    }

    /**
     * Disconnects the provider and cancel any location requests.
     */
    public void disconnect() {
        if (!isConnected) {
            return;
        }

        Logger.verbose("UALocationProvider - Disconnecting from location provider.");

        if (connectedAdapter != null) {
            connectedAdapter.disconnect();
            connectedAdapter = null;
        }

        isConnected = false;
    }
}
