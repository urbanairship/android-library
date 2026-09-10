/* Copyright Airship and Contributors */

package com.urbanairship.iam

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.android.layout.assets.AirshipCachedAssets
import com.urbanairship.android.layout.assets.AssetCacheManager
import com.urbanairship.android.layout.analytics.events.LayoutResolutionEvent
import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.automation.engine.AutomationPreparerDelegate
import com.urbanairship.automation.engine.DelegatePreparerResult
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.iam.actions.InAppActionRunnerFactory
import com.urbanairship.iam.adapter.CustomDisplayAdapter
import com.urbanairship.iam.adapter.CustomDisplayAdapterType
import com.urbanairship.iam.adapter.DisplayAdapterFactory
import com.urbanairship.iam.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.iam.coordinator.DisplayCoordinatorManager
import kotlin.time.Duration

internal class InAppMessageAutomationPreparer(
    private val assetsManager: AssetCacheManager,
    private val displayCoordinatorManager: DisplayCoordinatorManager,
    private val displayAdapterFactory: DisplayAdapterFactory,
    private val analyticsFactory: InAppMessageAnalyticsFactory,
    private val actionRunnerFactory: InAppActionRunnerFactory = InAppActionRunnerFactory()
) : AutomationPreparerDelegate<InAppMessage, PreparedInAppMessageData> {

    var messageContentExtender: InAppMessageContentExtender?
        get() { synchronized(displayAdapterFactory) { return displayAdapterFactory.messageContentExtender } }
        set(value) { synchronized(displayAdapterFactory) { displayAdapterFactory.messageContentExtender = value } }

    var displayInterval: Duration
        get() { synchronized(displayCoordinatorManager) { return displayCoordinatorManager.displayInterval } }
        set(value) { synchronized(displayCoordinatorManager) { displayCoordinatorManager.displayInterval = value} }

    @Volatile
    var onCheckSuppression: (suspend (InAppMessage, String) -> SuppressionResult)? = null

    override suspend fun prepare(
        data: InAppMessage,
        preparedScheduleInfo: PreparedScheduleInfo
    ): Result<DelegatePreparerResult<PreparedInAppMessageData>> {
        onCheckSuppression?.let { check ->
            val result = check(data, preparedScheduleInfo.scheduleId)
            if (result is SuppressionResult.Suppress) {
                return Result.success(suppressed(result.behavior, data, preparedScheduleInfo))
            }
        }

        val assets = prepareAssets(
            message = data,
            scheduleID = preparedScheduleInfo.scheduleId,
            skip = !preparedScheduleInfo.additionalAudienceCheckResult
                || preparedScheduleInfo.experimentResult?.isMatching == true
                || preparedScheduleInfo.variantAudienceResult?.outcome?.isDisplaySkipped == true
        ).getOrElse {
            return Result.failure(it)
        }

        val coordinator = displayCoordinatorManager.displayCoordinator(data)
        val analytics = analyticsFactory.makeAnalytics(data, preparedScheduleInfo)
        val actionRunner = actionRunnerFactory.makeRunner(analytics = analytics, inAppMessage = data)

        val adapter = displayAdapterFactory.makeAdapter(data, preparedScheduleInfo.priority, assets, actionRunner).getOrElse {
            UALog.w(it) { "Failed to resolve adapter ${preparedScheduleInfo.scheduleId}" }
            return Result.failure(it)
        }

        return Result.success(
            DelegatePreparerResult.Prepared(
                PreparedInAppMessageData(
                    message = data,
                    displayAdapter = adapter,
                    displayCoordinator = coordinator,
                    analytics = analytics,
                    actionRunner = actionRunner
                )
            )
        )
    }

    private suspend fun suppressed(
        behavior: AutomationAudience.MissBehavior,
        message: InAppMessage,
        preparedScheduleInfo: PreparedScheduleInfo
    ): DelegatePreparerResult<PreparedInAppMessageData> {
        val analytics = analyticsFactory.makeAnalytics(message, preparedScheduleInfo)
        analytics.recordEvent(LayoutResolutionEvent.appSuppressed(), null)
        return when (behavior) {
            AutomationAudience.MissBehavior.CANCEL -> DelegatePreparerResult.Cancel
            AutomationAudience.MissBehavior.SKIP -> DelegatePreparerResult.Skip
            AutomationAudience.MissBehavior.PENALIZE -> DelegatePreparerResult.Penalize
        }
    }

    override suspend fun cancelled(scheduleID: String) {
        UALog.v { "Execution cancelled $scheduleID" }
        assetsManager.clearCache(scheduleID)
    }

    fun setAdapterFactoryBlock(
        type: CustomDisplayAdapterType,
        factoryBlock: (Context, InAppMessage, AirshipCachedAssets) -> CustomDisplayAdapter?
    ) {
        displayAdapterFactory.setAdapterFactoryBlock(type, factoryBlock)
    }

    private suspend fun prepareAssets(
        message: InAppMessage,
        scheduleID: String,
        skip: Boolean): Result<AirshipCachedAssets> {

        if (skip) {
            return assetsManager.cacheAsset(scheduleID, listOf())
        }

        val imageUrls = message
            .getUrlInfos()
            .mapNotNull {
                return@mapNotNull when(it.type) {
                    UrlInfo.UrlType.IMAGE -> it.url
                    else -> null
                }
            }

        UALog.v { "Preparing assets $scheduleID: $imageUrls" }

        return assetsManager.cacheAsset(scheduleID, imageUrls)
    }
}
