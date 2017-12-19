/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.v4.os.ConfigurationCompat;
import android.support.v4.os.LocaleListCompat;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.location.UALocationManager;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.UAStringUtil;

import java.util.Locale;

/**
 * Audience checks.
 */
public abstract class AudienceChecks {

    /**
     * Checks the audience and new user.
     *
     * @param context The application context.
     * @param audience The audience.
     * @param isNewUser If the user is new.
     * @return {@code true} if the audience conditions are met, otherwise {@code false}.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    static boolean checkAudienceForScheduling(Context context, @Nullable Audience audience, boolean isNewUser) {
        if (audience == null) {
            return true;
        }

        // New user
        if (audience.getNewUser() != null && audience.getNewUser() != isNewUser) {
            return false;
        }

        if (!audience.getTestDevices().isEmpty()) {
            String hash = UAStringUtil.sha256(UAirship.shared().getPushManager().getChannelId());
            if (hash == null || !audience.getTestDevices().contains(hash)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks the audience.
     *
     * @param context The application context.
     * @param audience The audience.
     * @return {@code true} if the audience conditions are met, otherwise {@code false}.
     */
    public static boolean checkAudience(Context context, @Nullable Audience audience) {
        if (audience == null) {
            return true;
        }


        UAirship airship = UAirship.shared();
        UALocationManager locationManager = airship.getLocationManager();
        PushManager pushManager = airship.getPushManager();


        // Location opt-in
        if (audience.getLocationOptIn() != null && audience.getLocationOptIn() != locationManager.isOptIn()) {
            return false;
        }

        // Push opt-in
        if (audience.getNotificationsOptIn() != null && audience.getNotificationsOptIn() != pushManager.isOptIn()) {
            return false;
        }

        // Locale
        if (!isLocaleConditionMet(context, audience)) {
            return false;
        }

        // Tags
        if (audience.getTagSelector() != null && !audience.getTagSelector().apply(airship.getPushManager().getTags())) {
            return false;
        }

        // Version
        if (!isAppVersionConditionMet(context, audience)) {
            return false;
        }

        return true;
    }

    /**
     * Helper method to check the app version.
     *
     * @param context The application context.
     * @param audience The audience.
     * @return {@code true} if the app version conditions are met or are not defined, otherwise {@code false}.
     */
    private static boolean isAppVersionConditionMet(Context context, Audience audience) {
        if (audience.getVersionPredicate() == null) {
            return true;
        }

        // Get the version code
        int versionCode;
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        String platform;
        switch (UAirship.shared().getPlatformType()) {
            case UAirship.AMAZON_PLATFORM:
                platform = Audience.AMAZON_VERSION_KEY;
                break;
            case UAirship.ANDROID_PLATFORM:
            default:
                platform = Audience.ANDROID_VERSION_KEY;
                break;
        }

        // Construct the version json value
        JsonValue value = JsonMap.newBuilder()
                .put(platform, versionCode)
                .build()
                .toJsonValue();


        // Apply the predicate
        return audience.getVersionPredicate().apply(value);
    }

    /**
     * Helper method to check the locales.
     *
     * @param context The application context.
     * @param audience The audience.
     * @return {@code true} if the locale conditions are met or are not defined, otherwise {@code false}.
     */
    private static boolean isLocaleConditionMet(Context context, Audience audience) {
        if (audience.getLanguageTags().isEmpty()) {
            return true;
        }

        LocaleListCompat userLocales = ConfigurationCompat.getLocales(context.getResources().getConfiguration());

        // Find best locale
        Locale locale = userLocales.getFirstMatch(audience.getLanguageTags().toArray(new String[] {}));
        if (locale == null) {
            return false;
        }

        // getFirstMatch will return the default language if none of the specified locales are found,
        // so we still have to verify the locale exists in the audience conditions

        String tags = UAStringUtil.join(audience.getLanguageTags(), ",");
        LocaleListCompat audienceLocales = LocaleListCompat.forLanguageTags(tags);
        for (int i = 0; i < audienceLocales.size(); i++) {
            Locale audienceLocale = audienceLocales.get(i);

            if (!locale.getLanguage().equals(audienceLocale.getLanguage())) {
                continue;
            }

            if (!UAStringUtil.isEmpty(audienceLocale.getCountry()) && !audienceLocale.getCountry().equals(locale.getCountry())) {
                continue;
            }

            return true;
        }

        return false;
    }

}
