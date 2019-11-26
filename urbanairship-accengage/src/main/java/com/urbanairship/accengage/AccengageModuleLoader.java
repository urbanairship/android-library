/* Copyright Airship and Contributors */

package com.urbanairship.accengage;

import android.content.Context;

import com.urbanairship.AirshipComponent;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.modules.ModuleLoader;
import com.urbanairship.push.PushManager;

import java.util.Collections;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * Accengage module loader.
 */
class AccengageModuleLoader implements ModuleLoader {

    private Accengage accengage;

    /**
     * Default constructor.
     * @param context The context.
     * @param dataStore The datastore.
     * @param airshipChannel The airship channel.
     * @param pushManager The push manager.
     * @param analytics The analytics instance.
     */
    AccengageModuleLoader(@NonNull Context context, @NonNull PreferenceDataStore dataStore,
                          @NonNull AirshipChannel airshipChannel, @NonNull PushManager pushManager,
                          @NonNull Analytics analytics) {
        this.accengage = new Accengage(context, dataStore, airshipChannel, pushManager, analytics);
    }

    @Override
    public Set<? extends AirshipComponent> getComponents() {
        return Collections.singleton(accengage);
    }

}
