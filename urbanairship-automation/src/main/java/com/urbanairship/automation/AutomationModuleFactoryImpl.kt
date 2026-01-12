/* Copyright Airship and Contributors */

package com.urbanairship.automation

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.ApplicationMetrics
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.actions.ActionsManifest
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.audience.AudienceEvaluator
import com.urbanairship.automation.action.ActionAutomationExecutor
import com.urbanairship.automation.action.ActionAutomationPreparer
import com.urbanairship.automation.audiencecheck.AdditionalAudienceCheckerResolver
import com.urbanairship.automation.engine.AutomationDelayProcessor
import com.urbanairship.automation.engine.AutomationEngine
import com.urbanairship.automation.engine.AutomationEventFeed
import com.urbanairship.automation.engine.AutomationExecutor
import com.urbanairship.automation.engine.AutomationPreparer
import com.urbanairship.automation.engine.AutomationStore
import com.urbanairship.automation.engine.EventsHistory
import com.urbanairship.automation.engine.SerialAccessAutomationStore
import com.urbanairship.automation.engine.triggerprocessor.AutomationTriggerProcessor
import com.urbanairship.automation.limits.FrequencyLimitManager
import com.urbanairship.automation.remotedata.AutomationRemoteDataAccess
import com.urbanairship.automation.remotedata.AutomationRemoteDataSubscriber
import com.urbanairship.automation.storage.AutomationDatabase
import com.urbanairship.automation.storage.AutomationStoreMigrator
import com.urbanairship.automation.utils.NetworkMonitor
import com.urbanairship.automation.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.cache.AirshipCache
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.iam.InAppMessageAutomationExecutor
import com.urbanairship.iam.InAppMessageAutomationPreparer
import com.urbanairship.iam.InAppMessaging
import com.urbanairship.iam.actions.CancelSchedulesAction
import com.urbanairship.iam.actions.LandingPageAction
import com.urbanairship.iam.actions.ScheduleAction
import com.urbanairship.iam.adapter.DisplayAdapterFactory
import com.urbanairship.iam.analytics.DefaultInAppDisplayImpressionRuleProvider
import com.urbanairship.android.layout.analytics.LayoutEventRecorder
import com.urbanairship.iam.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.iam.analytics.MessageDisplayHistoryStore
import com.urbanairship.iam.assets.AssetCacheManager
import com.urbanairship.iam.coordinator.DisplayCoordinatorManager
import com.urbanairship.iam.legacy.LegacyAnalytics
import com.urbanairship.iam.legacy.LegacyInAppMessaging
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


    override val airshipVersion: String = BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION

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
        deferredResolver: DeferredResolver,
        eventFeed: AirshipEventFeed,
        metrics: ApplicationMetrics,
        cache: AirshipCache,
        audienceEvaluator: AudienceEvaluator
    ): Module {
        val assetManager = AssetCacheManager(context)
        val eventRecorder = LayoutEventRecorder(analytics, meteredUsage)
        val scheduleConditionNotifier = ScheduleConditionsChangedNotifier()
        val remoteDataAccess = AutomationRemoteDataAccess(context, remoteData)
        val activityMonitor = GlobalActivityMonitor.shared(context)
        val displayCoordinatorManager = DisplayCoordinatorManager(dataStore, activityMonitor)
        val frequencyLimits = FrequencyLimitManager(context, runtimeConfig)
        val automationStore = SerialAccessAutomationStore(
            AutomationStore.createDatabase(context, runtimeConfig)
        )
        val analyticsFactory = InAppMessageAnalyticsFactory(
            eventRecorder = eventRecorder,
            displayHistoryStore = MessageDisplayHistoryStore(automationStore),
            displayImpressionRuleProvider = DefaultInAppDisplayImpressionRuleProvider()
        )
        val eventsHistory = EventsHistory()

        // Preparation
        val actionPreparer = ActionAutomationPreparer()
        val messagePreparer = InAppMessageAutomationPreparer(
            assetsManager = assetManager,
            displayCoordinatorManager = displayCoordinatorManager,
            displayAdapterFactory = DisplayAdapterFactory(
                context,
                NetworkMonitor.shared(context),
                activityMonitor
            ),
            analyticsFactory = analyticsFactory
        )

        // Execution
        val actionExecutor = ActionAutomationExecutor()
        val messageExecutor = InAppMessageAutomationExecutor(
            context = context,
            assetManager = assetManager,
            analyticsFactory = analyticsFactory,
            scheduleConditionsChangedNotifier = scheduleConditionNotifier
        )

        val engine = AutomationEngine(
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
                remoteDataAccess = remoteDataAccess,
                additionalAudienceResolver = AdditionalAudienceCheckerResolver(
                    config = runtimeConfig,
                    cache = cache
                ),
                queueConfigSupplier = { runtimeConfig.remoteConfig.iaaConfig?.retryingQueue },
                audienceEvaluator = audienceEvaluator
            ),
            scheduleConditionsChangedNotifier = scheduleConditionNotifier,
            eventsFeed = AutomationEventFeed(
                applicationMetrics = metrics,
                activityMonitor = activityMonitor,
                eventFeed = eventFeed
            ),
            triggerProcessor = AutomationTriggerProcessor(automationStore, eventsHistory),
            delayProcessor = AutomationDelayProcessor(
                analytics = analytics,
                activityMonitor = activityMonitor,
                executionWindowProcessor = ExecutionWindowProcessor(context)),
            automationStoreMigrator = AutomationStoreMigrator(
                legacyDatabase = AutomationDatabase.createDatabase(context, runtimeConfig),
                automationStore
            ),
            eventsHistory = eventsHistory
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
        return Module.singleComponent(component, AutomationActionsManifest())
    }
}

private class AutomationActionsManifest: ActionsManifest {

    override val manifest: Map<Set<String>, () -> ActionRegistry.Entry> = mapOf(
        CancelSchedulesAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(
                action = CancelSchedulesAction()
            )
        },
        ScheduleAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(
                action = ScheduleAction()
            )
        },
        LandingPageAction.DEFAULT_NAMES to {
            ActionRegistry.Entry(
                action = LandingPageAction()
            )
        }
    )
}
