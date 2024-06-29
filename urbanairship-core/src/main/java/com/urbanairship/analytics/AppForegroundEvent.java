/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import android.content.pm.PackageInfo;
import android.os.Build;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

class AppForegroundEvent extends Event {


    /**
     * Default constructor for AppForegroundEvent.
     *
     * @param timeMS The time the app was foregrounded in milliseconds.
     */
    AppForegroundEvent(long timeMS) {
        super(timeMS);
    }

    @NonNull
    @Override
    public final EventType getType() {
        return EventType.APP_FOREGROUND;
    }

    /**
     * @hide
     */
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public final JsonMap getEventData(@NonNull ConversionData conversionData) {
        PackageInfo packageInfo = UAirship.getPackageInfo();
        return JsonMap.newBuilder()
                      .put(CONNECTION_TYPE_KEY, getConnectionType())
                      .put(CONNECTION_SUBTYPE_KEY, getConnectionSubType())
                      .put(CARRIER_KEY, getCarrier())
                      .put(TIME_ZONE_KEY, getTimezone())
                      .put(DAYLIGHT_SAVINGS_KEY, isDaylightSavingsTime())
                      .put(OS_VERSION_KEY, Build.VERSION.RELEASE)
                      .put(LIB_VERSION_KEY, UAirship.getVersion())
                      .putOpt(PACKAGE_VERSION_KEY, packageInfo != null ? packageInfo.versionName : null)
                      .put(PUSH_ID_KEY, conversionData.getConversionSendId())
                      .put(METADATA_KEY, conversionData.getConversionMetadata())
                      .put(LAST_METADATA_KEY, conversionData.getLastReceivedMetadata())
                      .build();
    }

}
