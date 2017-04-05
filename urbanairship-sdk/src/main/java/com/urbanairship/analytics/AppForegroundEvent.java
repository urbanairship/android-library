/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.content.pm.PackageInfo;
import android.os.Build;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

class AppForegroundEvent extends Event {

    static final String TYPE = "app_foreground";

    static final String NOTIFICATION_TYPES_KEY = "notification_types";


    /**
     * Default constructor for AppForegroundEvent.
     *
     * @param timeMS The time the app was foregrounded in milliseconds.
     */
    AppForegroundEvent(long timeMS) {
        super(timeMS);
    }

    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    protected final JsonMap getEventData() {
        PackageInfo packageInfo = UAirship.getPackageInfo();
        return JsonMap.newBuilder()
                .put(CONNECTION_TYPE_KEY, getConnectionType())
                .put(CONNECTION_SUBTYPE_KEY, getConnectionSubType())
                .put(CARRIER_KEY, getCarrier())
                .put(TIME_ZONE_KEY, getTimezone())
                .put(DAYLIGHT_SAVINGS_KEY, isDaylightSavingsTime())
                .put(NOTIFICATION_TYPES_KEY, JsonValue.wrapOpt(getNotificationTypes()).getList())
                .put(OS_VERSION_KEY, Build.VERSION.RELEASE)
                .put(LIB_VERSION_KEY, UAirship.getVersion())
                .putOpt(PACKAGE_VERSION_KEY, packageInfo != null ? packageInfo.versionName : null)
                .put(PUSH_ID_KEY, UAirship.shared().getAnalytics().getConversionSendId())
                .put(METADATA_KEY, UAirship.shared().getAnalytics().getConversionMetadata())
                .put(LAST_METADATA_KEY, UAirship.shared().getPushManager().getLastReceivedMetadata())
                .build();
    }

}
