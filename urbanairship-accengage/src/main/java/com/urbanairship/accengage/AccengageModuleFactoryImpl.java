/* Copyright Airship and Contributors */

package com.urbanairship.accengage;

import android.content.Context;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.modules.accengage.AccengageModule;
import com.urbanairship.modules.accengage.AccengageModuleFactory;
import com.urbanairship.modules.accengage.AccengageNotificationHandler;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.notifications.NotificationProvider;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Accengage module loader factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AccengageModuleFactoryImpl implements AccengageModuleFactory {

    @NonNull
    @Override
    public AccengageModule build(@NonNull Context context,
                                 @NonNull AirshipConfigOptions configOptions,
                                 @NonNull PreferenceDataStore dataStore,
                                 @NonNull PrivacyManager privacyManager,
                                 @NonNull AirshipChannel airshipChannel,
                                 @NonNull PushManager pushManager) {

        final Accengage accengage = new Accengage(context, configOptions, dataStore, privacyManager, airshipChannel, pushManager);
        return new AccengageModule(accengage, new AccengageNotificationHandler() {
            @NonNull
            @Override
            public NotificationProvider getNotificationProvider() {
                return accengage.getNotificationProvider();
            }
        });
    }

    @NonNull
    @Override
    public String getAirshipVersion() {
        return BuildConfig.AIRSHIP_VERSION;
    }

    @NonNull
    @Override
    public String getPackageVersion() {
        return BuildConfig.SDK_VERSION;
    }
}
