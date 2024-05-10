/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import android.content.Context;

import com.urbanairship.AirshipComponent;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.audience.AudienceOverridesProvider;
import com.urbanairship.audience.DeviceInfoProvider;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.contacts.Contact;
import com.urbanairship.deferred.DeferredResolver;
import com.urbanairship.experiment.ExperimentManager;
import com.urbanairship.iam.LegacyInAppMessageManager;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.meteredusage.AirshipMeteredUsage;
import com.urbanairship.modules.Module;
import com.urbanairship.modules.automation.AutomationModuleFactory;
import com.urbanairship.push.PushManager;
import com.urbanairship.remotedata.RemoteData;

import java.util.Arrays;
import java.util.Collection;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Automation module loader factory implementation. Also loads IAA.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AutomationModuleFactoryImpl implements AutomationModuleFactory {

    @NonNull
    @Override
    public Module build(@NonNull Context context,
                        @NonNull PreferenceDataStore dataStore,
                        @NonNull AirshipRuntimeConfig runtimeConfig,
                        @NonNull PrivacyManager privacyManager,
                        @NonNull AirshipChannel airshipChannel,
                        @NonNull PushManager pushManager,
                        @NonNull Analytics analytics,
                        @NonNull RemoteData remoteData,
                        @NonNull ExperimentManager experimentManager,
                        @NonNull AirshipMeteredUsage meteredUsage,
                        @NonNull Contact contact,
                        @NonNull DeferredResolver deferredResolver,
                        @NonNull LocaleManager localeManager) {

        InAppAutomation inAppAutomation = new InAppAutomation(context, dataStore, runtimeConfig,
                privacyManager, analytics, remoteData, airshipChannel, experimentManager,
                meteredUsage, contact, deferredResolver, localeManager);
        LegacyInAppMessageManager legacyInAppMessageManager = new LegacyInAppMessageManager(context, dataStore, inAppAutomation, analytics, pushManager);

        Collection<AirshipComponent> components = Arrays.asList(inAppAutomation, legacyInAppMessageManager);
        return Module.multipleComponents(components, R.xml.ua_automation_actions);
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
