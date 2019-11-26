/* Copyright Airship and Contributors */

package com.urbanairship.accengage;

import android.content.Context;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.modules.AccengageModuleLoaderFactory;
import com.urbanairship.modules.ModuleLoader;
import com.urbanairship.push.PushManager;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Accengage module loader factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AccengageModuleLoaderFactoryImpl implements AccengageModuleLoaderFactory {

    @Override
    public ModuleLoader build(@NonNull Context context, @NonNull PreferenceDataStore dataStore,
                              @NonNull AirshipChannel airshipChannel, @NonNull PushManager manager,
                              @NonNull Analytics analytics) {
        return new AccengageModuleLoader(context, dataStore, airshipChannel, manager, analytics);
    }

}