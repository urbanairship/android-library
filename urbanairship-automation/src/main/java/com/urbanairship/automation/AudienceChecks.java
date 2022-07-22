/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.json.JsonMap;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.permission.PermissionsManager;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.VersionUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

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

        // Test devices
        if (!isTestDeviceConditionMet(audience)) {
            return false;
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
    @WorkerThread
    public static boolean checkAudience(@NonNull Context context, @Nullable Audience audience) {
        if (audience == null) {
            return true;
        }

        // Test devices
        if (!isTestDeviceConditionMet(audience)) {
            return false;
        }

        UAirship airship = UAirship.shared();
        AirshipLocationClient locationClient = airship.getLocationClient();
        PushManager pushManager = airship.getPushManager();
        AirshipChannel channel = airship.getChannel();

        // Notification opt-in
        boolean notificationsOptIn = pushManager.areNotificationsOptedIn();

        if (audience.getNotificationsOptIn() != null) {
            if (audience.getNotificationsOptIn() != notificationsOptIn) {
                return false;
            }
        }

        // Locale
        if (!isLocaleConditionMet(context, audience)) {
            return false;
        }

        // Tags
        if (audience.getTagSelector() != null) {
            if (!airship.getPrivacyManager().isEnabled(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)) {
                return false;
            }

            if (!audience.getTagSelector().apply(channel.getTags())) {
                return false;
            }
        }

        // Requires analytics
        if (audience.getRequiresAnalytics() != null && audience.getRequiresAnalytics()) {
            if (!airship.getPrivacyManager().isEnabled(PrivacyManager.FEATURE_ANALYTICS)) {
                return false;
            }
        }

        // Permissions and location
        if (audience.getLocationOptIn() != null || audience.getPermissionsPredicate() != null) {
            JsonMap permissionsMap = createPermissionsMap(airship.getPermissionsManager());

            if (audience.getPermissionsPredicate() != null && !audience.getPermissionsPredicate().apply(permissionsMap)) {
                return false;
            }

            if (audience.getLocationOptIn() != null) {
                String permissionStatus = permissionsMap.opt(Permission.LOCATION.getValue()).getString();
                if (permissionStatus == null) {
                    return false;
                }
                boolean isGranted = PermissionStatus.GRANTED.getValue().equals(permissionStatus);

                if (audience.getLocationOptIn() != isGranted) {
                    return false;
                }
            }
        }

        // Version
        return isAppVersionConditionMet(audience);
    }

    /**
     * Helper method to check the app version.
     *
     * @param audience The audience.
     * @return {@code true} if the app version conditions are met or are not defined, otherwise {@code false}.
     */
    private static boolean isAppVersionConditionMet(@NonNull Audience audience) {
        if (audience.getVersionPredicate() == null) {
            return true;
        }

        // Apply the predicate
        return audience.getVersionPredicate().apply(VersionUtils.createVersionObject());
    }

    private static Set<String> sanitizeLanguageTags(List<String> languageTags) {
        HashSet<String> sanitizedLanguageTags = new HashSet<>();

        for (String languageTag : languageTags) {
            // Remove trailing dashes and underscores
            if (!UAStringUtil.isEmpty(languageTag)) {
                if (languageTag.endsWith("_") || languageTag.endsWith("-")) {
                    Logger.debug("Sanitizing malformed language tag: " + languageTag);
                    sanitizedLanguageTags.add(languageTag.substring(0, languageTag.length() - 1));
                } else {
                    sanitizedLanguageTags.add(languageTag);
                }
            }
        }

        // Remove duplicates
        return sanitizedLanguageTags;
    }


    /**
     * Helper method to check the locales.
     *
     * @param context The application context.
     * @param audience The audience.
     * @return {@code true} if the locale conditions are met or are not defined, otherwise {@code false}.
     */
    private static boolean isLocaleConditionMet(@NonNull Context context, @NonNull Audience audience) {
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

        // Sanitize language tags in case any happen to be malformed
        Set<String> languageTags = sanitizeLanguageTags(audience.getLanguageTags());

        try {
            String joinedTags = UAStringUtil.join(languageTags, ",");
            LocaleListCompat audienceLocales = LocaleListCompat.forLanguageTags(joinedTags);
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
        } catch (Exception e) {
            Logger.error("Unable to construct locale list: ", e);
        }

        return false;
    }

    private static boolean isTestDeviceConditionMet(@NonNull Audience audience) {
        // Test devices
        if (!audience.getTestDevices().isEmpty()) {
            byte[] digest = UAStringUtil.sha256Digest(UAirship.shared().getChannel().getId());
            if (digest == null || digest.length < 16) {
                return false;
            }

            digest = Arrays.copyOf(digest, 16);

            for (String testDevice : audience.getTestDevices()) {
                byte[] decoded = UAStringUtil.base64Decode(testDevice);
                if (Arrays.equals(digest, decoded)) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }

    @WorkerThread
    @NonNull
    private static JsonMap createPermissionsMap(@NonNull PermissionsManager permissionsManager) {
        JsonMap.Builder builder = JsonMap.newBuilder();
        for (Permission permission : permissionsManager.getConfiguredPermissions()) {
            try {
                PermissionStatus status = permissionsManager.checkPermissionStatus(permission).get();
                if (status != null) {
                    builder.putOpt(permission.getValue(), status.getValue());
                }
            } catch (ExecutionException e) {
                Logger.error(e, "Failed to get permissions status: %s", permission);
            } catch (InterruptedException e) {
                Logger.error(e, "Failed to get permissions status: %s", permission);
                Thread.currentThread().interrupt();
            }
        }

        return builder.build();
    }
}
