/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class AppStoreUtils {
    private static final String PLAY_STORE_URL = "https://play.google.com/store";
    private static final String PLAY_STORE_APP_URL = "https://play.google.com/store/apps/details?id=";
    private static final String PLAY_STORE_PACKAGE = "com.android.vending";

    private static final String AMAZON_URL = "amzn://apps/android?p=";

    private AppStoreUtils() {}

    @NonNull
    public static Intent getAppStoreIntent(@NonNull Context context,
                                           UAirship.Platform platform,
                                           @NonNull AirshipConfigOptions configOptions) {

        if (configOptions.appStoreUri != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW, configOptions.appStoreUri);

            if (configOptions.appStoreUri.toString().startsWith(PLAY_STORE_URL) &&  isPlayStoreAvailable(context)) {
                intent.setPackage(PLAY_STORE_PACKAGE);
            }
            return intent;
        }

        String packageName = context.getPackageName();
        if (platform == UAirship.Platform.AMAZON) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse(AMAZON_URL + packageName));
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_APP_URL + packageName));
            if (isPlayStoreAvailable(context)) {
                intent.setPackage(PLAY_STORE_PACKAGE);
            }
            return intent;
        }
    }

    private static boolean isPlayStoreAvailable(@NonNull Context context) {
        return PlayServicesUtils.isGooglePlayStoreAvailable(context.getApplicationContext());
    }
}
