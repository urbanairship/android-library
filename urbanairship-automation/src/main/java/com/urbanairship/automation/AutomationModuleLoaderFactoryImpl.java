/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;

import com.urbanairship.AirshipComponent;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.TagGroupRegistrar;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.iam.InAppActivityMonitor;
import com.urbanairship.iam.InAppMessageManager;
import com.urbanairship.iam.LegacyInAppMessageManager;
import com.urbanairship.modules.ModuleLoader;
import com.urbanairship.modules.SimpleModuleLoader;
import com.urbanairship.modules.automation.AutomationModuleLoaderFactory;
import com.urbanairship.push.PushManager;
import com.urbanairship.remotedata.RemoteData;

import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Automation module loader factory implementation. Also loads IAA.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AutomationModuleLoaderFactoryImpl implements AutomationModuleLoaderFactory {

    @Override
    public ModuleLoader build(@NonNull Context context,
                              @NonNull PreferenceDataStore dataStore,
                              @NonNull AirshipRuntimeConfig runtimeConfig,
                              @NonNull AirshipChannel airshipChannel,
                              @NonNull PushManager pushManager,
                              @NonNull Analytics analytics,
                              @NonNull RemoteData remoteData,
                              @NonNull TagGroupRegistrar tagGroupRegistrar) {

        InAppMessageManager inAppMessageManager = new InAppMessageManager(context, dataStore, runtimeConfig, analytics, remoteData, InAppActivityMonitor.shared(context), airshipChannel, tagGroupRegistrar);
        LegacyInAppMessageManager legacyInAppMessageManager = new LegacyInAppMessageManager(context, dataStore, inAppMessageManager, analytics, pushManager);
        ActionAutomation automation = new ActionAutomation(context, dataStore, runtimeConfig.getConfigOptions(), analytics, GlobalActivityMonitor.shared(context));

        Set<AirshipComponent> components = new HashSet<>();
        components.add(inAppMessageManager);
        components.add(legacyInAppMessageManager);
        components.add(automation);

        return SimpleModuleLoader.multipleComponents(components);
    }

}
