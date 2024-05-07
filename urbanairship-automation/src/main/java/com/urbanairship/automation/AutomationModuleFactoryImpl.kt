/* Copyright Airship and Contributors */
package com.urbanairship.automation

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.ApplicationMetrics
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.automation.rewrite.AutomationEngine
import com.urbanairship.automation.rewrite.AutomationEventFeed
import com.urbanairship.automation.rewrite.AutomationExecutor
import com.urbanairship.automation.rewrite.AutomationStore
import com.urbanairship.automation.rewrite.InAppAutomation
import com.urbanairship.automation.rewrite.InAppAutomationComponent
import com.urbanairship.automation.rewrite.SerialAccessAutomationStore
import com.urbanairship.automation.rewrite.actionautomation.ActionAutomationExecutor
import com.urbanairship.automation.rewrite.actionautomation.ActionAutomationPreparer
import com.urbanairship.automation.rewrite.engine.AutomationDelayProcessor
import com.urbanairship.automation.rewrite.engine.AutomationPreparer
import com.urbanairship.automation.rewrite.engine.triggerprocessor.AutomationTriggerProcessor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessageAutomationExecutor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessageAutomationPreparer
import com.urbanairship.automation.rewrite.inappmessage.InAppMessaging
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppEventRecorder
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.automation.rewrite.inappmessage.assets.AssetCacheManager
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapterFactory
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DisplayCoordinatorManager
import com.urbanairship.automation.rewrite.inappmessage.legacy.LegacyInAppMessaging
import com.urbanairship.automation.rewrite.limits.FrequencyLimitManager
import com.urbanairship.automation.rewrite.remotedata.AutomationRemoteDataAccess
import com.urbanairship.automation.rewrite.remotedata.AutomationRemoteDataSubscriber
import com.urbanairship.automation.rewrite.utils.NetworkMonitor
import com.urbanairship.automation.rewrite.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.contacts.Contact
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.locale.LocaleManager
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.modules.Module
import com.urbanairship.modules.automation.AutomationModuleFactory
import com.urbanairship.push.PushManager
import com.urbanairship.remotedata.RemoteData

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
        eventFeed: AirshipEventFeed,
        metrics: ApplicationMetrics,
        useNewAutomation: Boolean
    ): Module {


        fun registerNewAutomation(): Module {
            val assetManager = AssetCacheManager(context)
            val eventRecorder = InAppEventRecorder(analytics)
            val scheduleConditionNotifier = ScheduleConditionsChangedNotifier()
            val remoteDataAccess = AutomationRemoteDataAccess(context, remoteData)
            val activityMonitor = GlobalActivityMonitor.shared(context)
            val displayCoordinatorManager = DisplayCoordinatorManager(dataStore, activityMonitor)
            val frequencyLimits = FrequencyLimitManager(context, runtimeConfig)
            val automationStore = SerialAccessAutomationStore(AutomationStore.createDatabase(context, runtimeConfig))

            // Preparation
            val actionPreparer = ActionAutomationPreparer()
            val messagePreparer = InAppMessageAutomationPreparer(
                assetsManager = assetManager,
                displayCoordinatorManager = displayCoordinatorManager,
                displayAdapterFactory = DisplayAdapterFactory(context, NetworkMonitor.shared(context), activityMonitor))

            // Execution
            val actionExecutor = ActionAutomationExecutor()
            val messageExecutor = InAppMessageAutomationExecutor(
                context = context,
                assetManager = assetManager,
                analyticsFactory = InAppMessageAnalyticsFactory(eventRecorder, meteredUsage),
                scheduleConditionsChangedNotifier = scheduleConditionNotifier,
                actionRunnerFactory = ActionRunRequestFactory())

            val engine = AutomationEngine(
                context = context,
                store = automationStore,
                executor = AutomationExecutor(
                    actionExecutor = actionExecutor,
                    messageExecutor = messageExecutor,
                    remoteDataAccess = remoteDataAccess
                ),
                preparer = AutomationPreparer(
                    actionPreparer = actionPreparer,
                    messagePreparer = messagePreparer,
                    deferredResolver = deferredResolver,
                    frequencyLimitManager = frequencyLimits,
                    deviceInfoProvider = infoProvider,
                    experiments = experimentManager,
                    remoteDataAccess = remoteDataAccess
                ),
                scheduleConditionsChangedNotifier = scheduleConditionNotifier,
                eventsFeed = AutomationEventFeed(
                    applicationMetrics = metrics,
                    activityMonitor = activityMonitor,
                    eventFeed = eventFeed
                ).also { it.attach() },
                triggerProcessor = AutomationTriggerProcessor(automationStore),
                delayProcessor = AutomationDelayProcessor(analytics, activityMonitor)
            )

            val automation = InAppAutomation(
                engine = engine,
                inAppMessaging = InAppMessaging(messageExecutor, messagePreparer),
                legacyInAppMessaging = LegacyInAppMessaging(pushManager),
                remoteDataSubscriber = AutomationRemoteDataSubscriber(
                    dataStore = dataStore,
                    remoteDataAccess = remoteDataAccess,
                    engine = engine,
                    frequencyLimitManager = frequencyLimits),
                dataStore = dataStore,
                privacyManager = privacyManager,
                config = runtimeConfig
            )

            val component = InAppAutomationComponent(context, dataStore, automation)
            return Module.singleComponent(component, R.xml.ua_automation_actions)
        }

        return  registerNewAutomation()
    }

    override val airshipVersion: String
        get() = BuildConfig.AIRSHIP_VERSION

    override val packageVersion: String
        get() = BuildConfig.SDK_VERSION
}
