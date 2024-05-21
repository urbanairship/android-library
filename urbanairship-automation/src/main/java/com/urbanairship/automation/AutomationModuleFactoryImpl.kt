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
import com.urbanairship.automation.action.ActionAutomationExecutor
import com.urbanairship.automation.action.ActionAutomationPreparer
import com.urbanairship.automation.engine.AutomationDelayProcessor
import com.urbanairship.automation.engine.AutomationEngine
import com.urbanairship.automation.engine.AutomationEventFeed
import com.urbanairship.automation.engine.AutomationExecutor
import com.urbanairship.automation.engine.AutomationPreparer
import com.urbanairship.automation.engine.AutomationStore
import com.urbanairship.automation.engine.SerialAccessAutomationStore
import com.urbanairship.automation.engine.triggerprocessor.AutomationTriggerProcessor
import com.urbanairship.iam.InAppMessageAutomationExecutor
import com.urbanairship.iam.InAppMessageAutomationPreparer
import com.urbanairship.iam.InAppMessaging
import com.urbanairship.iam.analytics.InAppEventRecorder
import com.urbanairship.iam.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.iam.assets.AssetCacheManager
import com.urbanairship.iam.adapter.DisplayAdapterFactory
import com.urbanairship.iam.coordinator.DisplayCoordinatorManager
import com.urbanairship.iam.legacy.LegacyInAppMessaging
import com.urbanairship.automation.limits.FrequencyLimitManager
import com.urbanairship.automation.remotedata.AutomationRemoteDataAccess
import com.urbanairship.automation.remotedata.AutomationRemoteDataSubscriber
import com.urbanairship.automation.storage.AutomationDatabase
import com.urbanairship.automation.storage.AutomationStoreMigrator
import com.urbanairship.automation.utils.NetworkMonitor
import com.urbanairship.automation.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.contacts.Contact
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.iam.legacy.LegacyAnalytics
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
        meteredUsage: AirshipMeteredUsage,
        contact: Contact,
        deferredResolver: DeferredResolver,
        eventFeed: AirshipEventFeed,
        metrics: ApplicationMetrics
    ): Module {
        val assetManager = AssetCacheManager(context)
        val eventRecorder = InAppEventRecorder(analytics)
        val scheduleConditionNotifier = ScheduleConditionsChangedNotifier()
        val remoteDataAccess = AutomationRemoteDataAccess(context, remoteData)
        val activityMonitor = GlobalActivityMonitor.shared(context)
        val displayCoordinatorManager = DisplayCoordinatorManager(dataStore, activityMonitor)
        val frequencyLimits = FrequencyLimitManager(context, runtimeConfig)
        val automationStore = SerialAccessAutomationStore(
            AutomationStore.createDatabase(context, runtimeConfig)
        )

        // Preparation
        val actionPreparer = ActionAutomationPreparer()
        val messagePreparer = InAppMessageAutomationPreparer(
            assetsManager = assetManager,
            displayCoordinatorManager = displayCoordinatorManager,
            displayAdapterFactory = DisplayAdapterFactory(
                context,
                NetworkMonitor.shared(context),
                activityMonitor
            )
        )

        // Execution
        val actionExecutor = ActionAutomationExecutor()
        val messageExecutor = InAppMessageAutomationExecutor(
            context = context,
            assetManager = assetManager,
            analyticsFactory = InAppMessageAnalyticsFactory(eventRecorder, meteredUsage),
            scheduleConditionsChangedNotifier = scheduleConditionNotifier,
            actionRunnerFactory = ActionRunRequestFactory()
        )

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
                experiments = experimentManager,
                remoteDataAccess = remoteDataAccess
            ),
            scheduleConditionsChangedNotifier = scheduleConditionNotifier,
            eventsFeed = AutomationEventFeed(
                applicationMetrics = metrics,
                activityMonitor = activityMonitor,
                eventFeed = eventFeed
            ),
            triggerProcessor = AutomationTriggerProcessor(automationStore),
            delayProcessor = AutomationDelayProcessor(analytics, activityMonitor),
            automationStoreMigrator = AutomationStoreMigrator(
                legacyDatabase = AutomationDatabase.createDatabase(context, runtimeConfig),
                automationStore
            )
        )

        val automation = InAppAutomation(
            engine = engine,
            inAppMessaging = InAppMessaging(messageExecutor, messagePreparer),
            legacyInAppMessaging = LegacyInAppMessaging(
                context = context,
                pushManager = pushManager,
                preferenceDataStore = dataStore,
                automationEngine = engine,
                legacyAnalytics = LegacyAnalytics(eventRecorder)
            ),
            remoteDataSubscriber = AutomationRemoteDataSubscriber(
                dataStore = dataStore,
                remoteDataAccess = remoteDataAccess,
                engine = engine,
                frequencyLimitManager = frequencyLimits
            ),
            dataStore = dataStore,
            privacyManager = privacyManager,
            config = runtimeConfig
        )

        val component = InAppAutomationComponent(context, dataStore, automation)
        return Module.singleComponent(component, R.xml.ua_automation_actions)
    }

    override val airshipVersion: String
        get() = BuildConfig.AIRSHIP_VERSION

    override val packageVersion: String
        get() = BuildConfig.SDK_VERSION
}
