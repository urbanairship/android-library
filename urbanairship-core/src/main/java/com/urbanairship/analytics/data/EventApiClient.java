/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.util.UAStringUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * A client that handles uploading analytic events
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EventApiClient {

    private static final String SYSTEM_LOCATION_DISABLED = "SYSTEM_LOCATION_DISABLED";

    private static final String NOT_ALLOWED = "NOT_ALLOWED";

    private static final String ALWAYS_ALLOWED = "ALWAYS_ALLOWED";

    @NonNull
    private final RequestFactory requestFactory;

    @NonNull
    private final LocaleManager localeManager;

    @NonNull
    private final Context context;

    /**
     * Default constructor.
     *
     * @param context The application context.
     */
    public EventApiClient(@NonNull Context context) {
        this(context, RequestFactory.DEFAULT_REQUEST_FACTORY, LocaleManager.shared(context));
    }

    /**
     * Create the EventApiClient
     *
     * @param context The application context.
     * @param requestFactory The requestFactory.
     * @param localeManager The locale manager.
     */
    @VisibleForTesting
    EventApiClient(@NonNull Context context, @NonNull RequestFactory requestFactory, @NonNull LocaleManager localeManager) {
        this.requestFactory = requestFactory;
        this.context = context;
        this.localeManager = localeManager;
    }

    /**
     * Sends a collection of events.
     *
     * @param airship The {@link UAirship} instance.
     * @param events Specified events
     * @return eventResponse or null if an error occurred
     */
    @Nullable
    EventResponse sendEvents(@NonNull UAirship airship, @NonNull Collection<String> events) {
        if (events.size() == 0) {
            Logger.verbose("EventApiClient - No analytics events to send.");
            return null;
        }

        List<JsonValue> eventJSON = new ArrayList<>();

        for (String eventPayload : events) {
            try {
                eventJSON.add(JsonValue.parseString(eventPayload));
            } catch (JsonException e) {
                Logger.error(e, "EventApiClient - Invalid eventPayload.");
            }
        }

        String payload = new JsonList(eventJSON).toString();

        String url = airship.getAirshipConfigOptions().analyticsUrl + "warp9/";
        URL analyticsServerUrl = null;
        try {
            analyticsServerUrl = new URL(url);
        } catch (MalformedURLException e) {
            Logger.error(e, "EventApiClient - Invalid analyticsServer: %s", url);
        }

        if (analyticsServerUrl == null) {
            return null;
        }

        String deviceFamily;
        if (airship.getPlatformType() == UAirship.AMAZON_PLATFORM) {
            deviceFamily = "amazon";
        } else {
            deviceFamily = "android";
        }

        double sentAt = System.currentTimeMillis() / 1000.0;

        // CE-2745: Calling BluetoothAdapter.getDefaultAdapter() results in a RuntimeException on
        // devices running Ice Cream Sandwich http://stackoverflow.com/a/15036421
        String bluetoothEnabled = "false";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            bluetoothEnabled = Boolean.toString(isBluetoothEnabled());
        }

        // SDK Extensions
        String extensionHeader = "";

        Map<String, String> sdkExtensions = airship.getAnalytics().getExtensions();
        Integer i = 0;
        for (String name : sdkExtensions.keySet()) {
            extensionHeader += String.format("%s:%s", name, sdkExtensions.get(name));

            i++;
            if (i < sdkExtensions.size()) {
                extensionHeader += ",";
            }
        }

        Request request = requestFactory.createRequest("POST", analyticsServerUrl)
                                        .setRequestBody(payload, "application/json")
                                        .setCompressRequestBody(true)
                                        .setHeader("X-UA-Device-Family", deviceFamily)
                                        .setHeader("X-UA-Sent-At", String.format(Locale.US, "%.3f", sentAt))
                                        .setHeader("X-UA-Package-Name", getPackageName())
                                        .setHeader("X-UA-Package-Version", getPackageVersion())
                                        .setHeader("X-UA-App-Key", airship.getAirshipConfigOptions().appKey)
                                        .setHeader("X-UA-In-Production", Boolean.toString(airship.getAirshipConfigOptions().inProduction))
                                        .setHeader("X-UA-Device-Model", Build.MODEL)
                                        .setHeader("X-UA-Android-Version-Code", String.valueOf(Build.VERSION.SDK_INT))
                                        .setHeader("X-UA-Lib-Version", UAirship.getVersion())
                                        .setHeader("X-UA-Timezone", TimeZone.getDefault().getID())
                                        .setHeader("X-UA-Channel-Opted-In",
                                                Boolean.toString(airship.getPushManager().isOptIn()))
                                        .setHeader("X-UA-Channel-Background-Enabled",
                                                Boolean.toString(airship.getPushManager().isPushEnabled() &&
                                                        airship.getPushManager().isPushAvailable()))
                                        .setHeader("X-UA-Location-Permission", getLocationPermission())
                                        .setHeader("X-UA-Location-Service-Enabled",
                                                Boolean.toString(airship.getLocationManager().isLocationUpdatesEnabled()))
                                        .setHeader("X-UA-Bluetooth-Status", bluetoothEnabled)
                                        .setHeader("X-UA-User-ID", airship.getInbox().getUser().getId())
                                        .setHeader("X-UA-Frameworks", extensionHeader);

        Locale locale = localeManager.getDefaultLocale();
        if (!UAStringUtil.isEmpty(locale.getLanguage())) {
            request.setHeader("X-UA-Locale-Language", locale.getLanguage());

            if (!UAStringUtil.isEmpty(locale.getCountry())) {
                request.setHeader("X-UA-Locale-Country", locale.getCountry());
            }

            if (!UAStringUtil.isEmpty(locale.getVariant())) {
                request.setHeader("X-UA-Locale-Variant", locale.getVariant());
            }
        }

        String channelID = airship.getChannel().getId();
        if (!UAStringUtil.isEmpty(channelID)) {
            request.setHeader("X-UA-Channel-ID", channelID);
            // Send the Channel ID instead of the Registration ID as the Push Address for
            // analytics because the GCM Registration ID for Android and the ADM Registration ID
            // for Amazon can be too large (both may be greater than 4K).
            request.setHeader("X-UA-Push-Address", channelID);
        }

        Logger.debug("EventApiClient - Sending analytics events. Request: %s Events: %s", request, events);

        Response response = request.execute();

        Logger.debug("EventApiClient - Analytics event response: %s", response);

        return response == null ? null : new EventResponse(response);
    }

    /**
     * Gets the location permission for the app.
     *
     * @return The location permission string.
     */
    @NonNull
    String getLocationPermissionForApp() {
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
    @NonNull
    String getLocationPermission() {
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
    @SuppressLint("MissingPermission")
    boolean isBluetoothEnabled() {
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

    @Nullable
    String getPackageName() {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Nullable
    String getPackageVersion() {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

}
