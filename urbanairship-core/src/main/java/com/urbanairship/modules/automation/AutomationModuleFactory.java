/* Copyright Airship and Contributors */

package com.urbanairship.modules.automation;

import android.content.Context;

import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.ApplicationMetrics;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.analytics.AirshipEventFeed;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.audience.AudienceEvaluator;
import com.urbanairship.audience.AudienceOverridesProvider;
import com.urbanairship.audience.DeviceInfoProvider;
import com.urbanairship.cache.AirshipCache;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.contacts.Contact;
import com.urbanairship.deferred.DeferredResolver;
import com.urbanairship.experiment.ExperimentManager;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.meteredusage.AirshipMeteredUsage;
import com.urbanairship.modules.Module;
import com.urbanairship.push.PushManager;
import com.urbanairship.remotedata.RemoteData;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Automation module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AutomationModuleFactory extends AirshipVersionInfo {

    @NonNull
    Module build(
            @NonNull Context context,
            @NonNull PreferenceDataStore dataStore,
            @NonNull AirshipRuntimeConfig runtimeConfig,
            @NonNull PrivacyManager privacyManager,
            @NonNull AirshipChannel airshipChannel,
            @NonNull PushManager pushManager,
            @NonNull Analytics analytics,
            @NonNull RemoteData remoteData,
            @NonNull ExperimentManager experimentManager,
            @NonNull AirshipMeteredUsage meteredUsage,
            @NonNull DeferredResolver deferredResolver,
            @NonNull AirshipEventFeed eventFeed,
            @NonNull ApplicationMetrics metrics,
            @NonNull AirshipCache cache,
            @NonNull AudienceEvaluator audienceEvaluator
            );

    }
