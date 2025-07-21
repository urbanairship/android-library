/* Copyright Airship and Contributors */
package com.urbanairship.modules.automation

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.ApplicationMetrics
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics
import com.urbanairship.audience.AudienceEvaluator
import com.urbanairship.cache.AirshipCache
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.modules.Module
import com.urbanairship.push.PushManager
import com.urbanairship.remotedata.RemoteData

/**
 * Automation module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AutomationModuleFactory : AirshipVersionInfo {

    public fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        pushManager: PushManager,
        analytics: Analytics,
        remoteData: RemoteData,
        experimentManager: ExperimentManager,
        meteredUsage: AirshipMeteredUsage,
        deferredResolver: DeferredResolver,
        eventFeed: AirshipEventFeed,
        metrics: ApplicationMetrics,
        cache: AirshipCache,
        audienceEvaluator: AudienceEvaluator
    ): Module
}
