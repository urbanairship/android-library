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

package com.urbanairship.analytics;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.util.Network;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A client that handles uploading analytic events
 */
class EventApiClient {

    static final String SYSTEM_LOCATION_DISABLED = "SYSTEM_LOCATION_DISABLED";
    static final String NOT_ALLOWED = "NOT_ALLOWED";
    static final String ALWAYS_ALLOWED = "ALWAYS_ALLOWED";

    private final RequestFactory requestFactory;

    EventApiClient() {
        this(new RequestFactory());
    }

    /**
     * Create the EventApiClient
     *
     * @param requestFactory The requestFactory.
     */
    @VisibleForTesting
    EventApiClient(@NonNull RequestFactory requestFactory) {
        this.requestFactory = requestFactory;
    }

    /**
     * Sends a collection of events.
     *
     * @param events Specified events
     * @return eventResponse or null if an error occurred
     */
    EventResponse sendEvents(@NonNull Collection<String> events) {
        if (events.size() == 0) {
            Logger.verbose("EventApiClient - No events to send.");
            return null;
        }

        if (!Network.isConnected()) {
            Logger.verbose("EventApiClient - No network connectivity available. Unable to send events.");
            return null;
        }

        JSONArray eventJSON = new JSONArray();

        for (String eventPayload : events) {
            try {
                eventJSON.put(new JSONObject(eventPayload));
            } catch (JSONException e) {
                Logger.error("EventApiClient - Invalid eventPayload.", e);
            }
        }

        String payload = eventJSON.toString();

        String url = UAirship.shared().getAirshipConfigOptions().analyticsServer + "warp9/";
        URL analyticsServerUrl = null;
        try {
            analyticsServerUrl = new URL(url);
        } catch (MalformedURLException e) {
            Logger.error("EventApiClient - Invalid analyticsServer: " + url, e);
        }

        String deviceFamily;
        if (UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM) {
            deviceFamily = "amazon";
        } else {
            deviceFamily = "android";
        }

        double sentAt = System.currentTimeMillis() / 1000.0;
        AirshipConfigOptions airshipConfig = UAirship.shared().getAirshipConfigOptions();

        Request request = requestFactory.createRequest("POST", analyticsServerUrl)
                                        .setRequestBody(payload, "application/json")
                                        .setCompressRequestBody(true)
                                        .setHeader("X-UA-Device-Family", deviceFamily)
                                        .setHeader("X-UA-Sent-At", String.format(Locale.US, "%.3f", sentAt))
                                        .setHeader("X-UA-Package-Name", UAirship.getPackageName())
                                        .setHeader("X-UA-Package-Version", UAirship.getPackageInfo().versionName)
                                        .setHeader("X-UA-App-Key", airshipConfig.getAppKey())
                                        .setHeader("X-UA-In-Production", Boolean.toString(airshipConfig.inProduction))
                                        .setHeader("X-UA-Device-Model", Build.MODEL)
                                        .setHeader("X-UA-Android-Version-Code", String.valueOf(Build.VERSION.SDK_INT))
                                        .setHeader("X-UA-Lib-Version", UAirship.getVersion())
                                        .setHeader("X-UA-Timezone", TimeZone.getDefault().getID())
                                        .setHeader("X-UA-Channel-Opted-In",
                                                Boolean.toString(UAirship.shared().getPushManager().isOptIn()))
                                        .setHeader("X-UA-Channel-Background-Enabled",
                                                Boolean.toString(UAirship.shared().getPushManager().isPushEnabled() &&
                                                        UAirship.shared().getPushManager().isPushAvailable()))
                                        .setHeader("X-UA-Location-Permission", getLocationPermission())
                                        .setHeader("X-UA-Location-Service-Enabled",
                                                Boolean.toString(UAirship.shared().getLocationManager().isLocationUpdatesEnabled()))
                                        .setHeader("X-UA-Bluetooth-Status", Boolean.toString(isBluetoothEnabled()))
                                        .setHeader("X-UA-User-ID", UAirship.shared().getInbox().getUser().getId());


        Locale locale = Locale.getDefault();
        if (!UAStringUtil.isEmpty(locale.getLanguage())) {
            request.setHeader("X-UA-Locale-Language", locale.getLanguage());

            if (!UAStringUtil.isEmpty(locale.getCountry())) {
                request.setHeader("X-UA-Locale-Country", locale.getCountry());
            }

            if (!UAStringUtil.isEmpty(locale.getVariant())) {
                request.setHeader("X-UA-Locale-Variant", locale.getVariant());
            }
        }

        String channelID = UAirship.shared().getPushManager().getChannelId();
        if (!UAStringUtil.isEmpty(channelID)) {
            request.setHeader("X-UA-Channel-ID", channelID);
            // Send the Channel ID instead of the Registration ID as the Push Address for
            // analytics because the GCM Registration ID for Android and the ADM Registration ID
            // for Amazon can be too large (both may be greater than 4K).
            request.setHeader("X-UA-Push-Address", channelID);
        }

        Logger.debug("EventApiClient - Sending analytic events. Request:  " + request + " Events: " + events);

        Response response = request.execute();


        Logger.debug("EventApiClient - Analytic event send response: " + response);


        return response == null ? null : new EventResponse(response);
    }

    /**
     * Gets the location permission for the app.
     *
     * @return The location permission string.
     */
    static String getLocationPermissionForApp() {
        if (ManifestUtils.isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                ManifestUtils.isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return ALWAYS_ALLOWED;
        } else {
            return NOT_ALLOWED;
        }
    }

    /**
     * Gets the location permission.
     *
     * @return The location permission string.
     */
    static String getLocationPermission() {

        // Android Marshmallow
        if (Build.VERSION.SDK_INT >= 23) {
            if (UAirship.getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    UAirship.getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                return ALWAYS_ALLOWED;
            } else {
                return NOT_ALLOWED;
            }
        }

        // KitKat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int locationMode = 0;

            try {
                locationMode = Settings.Secure.getInt(UAirship.getApplicationContext().getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                Logger.debug("EventApiClient - Settings not found.");
            }

            if (locationMode != Settings.Secure.LOCATION_MODE_OFF) {
                return getLocationPermissionForApp();
            } else {
                return SYSTEM_LOCATION_DISABLED;
            }

        }

        String locationProviders = Settings.Secure.getString(UAirship.getApplicationContext().getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        if (!UAStringUtil.isEmpty(locationProviders)) {
            return getLocationPermissionForApp();
        } else {
            return SYSTEM_LOCATION_DISABLED;
        }

    }

    /**
     * Gets the Bluetooth enable/disable status.
     *
     * @return <code>true</code> if Bluetooth is enabled, otherwise <code>false</code>.
     */
    static boolean isBluetoothEnabled() {
        if (!ManifestUtils.isPermissionGranted(Manifest.permission.BLUETOOTH)) {
            // Manifest missing Bluetooth permissions
            return false;
        } else {
            // Code from Android Developer: http://developer.android.com/guide/topics/connectivity/bluetooth.html
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            //noinspection ResourceType - Suppresses the bluetooth permission warning
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        }
    }
}
