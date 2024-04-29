/* Copyright Airship and Contributors */
package com.urbanairship.automation

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.contacts.Contact
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.iam.LegacyInAppMessageManager
import com.urbanairship.locale.LocaleManager
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.modules.Module
import com.urbanairship.modules.automation.AutomationModuleFactory
import com.urbanairship.push.PushManager
import com.urbanairship.remotedata.RemoteData
import java.util.Arrays

/**
 * Automation module loader factory implementation. Also loads IAA.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AutomationModuleFactoryImpl : AutomationModuleFactory {

    override fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        pushManager: PushManager,
        analytics: Analytics,
        remoteData: RemoteData,
        experimentManager: ExperimentManager,
        infoProvider: DeviceInfoProvider,
        meteredUsage: AirshipMeteredUsage,
        contact: Contact,
        deferredResolver: DeferredResolver,
        localeManager: LocaleManager,
        eventFeed: AirshipEventFeed
    ): Module {
        val inAppAutomation = InAppAutomation(
            context,
            dataStore,
            runtimeConfig,
            privacyManager,
            analytics,
            remoteData,
            airshipChannel,
            experimentManager,
            infoProvider,
            meteredUsage,
            contact,
            deferredResolver,
            localeManager
        )
        val legacyInAppMessageManager =
            LegacyInAppMessageManager(context, dataStore, inAppAutomation, analytics, pushManager)
        val components: Collection<AirshipComponent> =
            listOf(inAppAutomation, legacyInAppMessageManager)

        return Module.multipleComponents(components, R.xml.ua_automation_actions)
    }

    override val airshipVersion: String
        get() = BuildConfig.AIRSHIP_VERSION

    override val packageVersion: String
        get() = BuildConfig.SDK_VERSION
}
