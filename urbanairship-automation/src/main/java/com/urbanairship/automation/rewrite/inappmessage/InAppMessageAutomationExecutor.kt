package com.urbanairship.automation.rewrite.inappmessage

import android.content.Context
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.automation.rewrite.AutomationExecutorDelegate
import com.urbanairship.automation.rewrite.AutomationSchedule
import com.urbanairship.automation.rewrite.InterruptedBehavior
import com.urbanairship.automation.rewrite.ScheduleExecuteResult
import com.urbanairship.automation.rewrite.ScheduleReadyResult
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppResolutionEvent
import com.urbanairship.automation.rewrite.inappmessage.assets.AssetCacheManagerInterface
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayResult
import com.urbanairship.automation.rewrite.utils.ScheduleConditionsChangedNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class InAppMessageAutomationExecutor(
    private val context: Context,
    private val assetManager: AssetCacheManagerInterface,
    private val analyticsFactory: InAppMessageAnalyticsFactory,
    private val scheduleConditionsChangedNotifier: ScheduleConditionsChangedNotifier,
    private val actionRunnerFactory: ActionRunRequestFactory,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) : AutomationExecutorDelegate<PreparedInAppMessageData> {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    var displayDelegate: InAppMessageDisplayDelegate? = null
        get() { synchronized(this) { return field } }
        set(value) { synchronized(this) { field = value } }

    override suspend fun isReady(
        data: PreparedInAppMessageData,
        preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleReadyResult {
        if (!data.displayAdapter.getIsReady()) {
            UALog.i { "Schedule ${preparedScheduleInfo.scheduleID} display adapter not ready" }
            scope.launch {
                data.displayAdapter.waitForReady()
                scheduleConditionsChangedNotifier.notifyChanged()
            }
            return ScheduleReadyResult.NOT_READY
        }

        if (!data.displayCoordinator.getIsReady()) {
            UALog.i { "Schedule ${preparedScheduleInfo.scheduleID} display coordinator not ready" }
            scope.launch {
                data.displayCoordinator.waitForReady()
                scheduleConditionsChangedNotifier.notifyChanged()
            }
            return ScheduleReadyResult.NOT_READY
        }

        val isReady = displayDelegate?.isMessageReadyToDisplay(data.message, preparedScheduleInfo.scheduleID)
            ?: true

        if (!isReady) {
            UALog.i { "Schedule ${preparedScheduleInfo.scheduleID} InAppMessageDisplayDelegate not ready" }
            return ScheduleReadyResult.NOT_READY
        }

        return ScheduleReadyResult.READY
    }

    override suspend fun execute(
        data: PreparedInAppMessageData,
        preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleExecuteResult {
        // Display
        displayDelegate?.messageWillDisplay(data.message, preparedScheduleInfo.scheduleID)
        data.displayCoordinator.messageWillDisplay(data.message)

        val analytics = analyticsFactory.makeAnalytics(
            message = data.message,
            preparedScheduleInfo = preparedScheduleInfo
        )

        var result = ScheduleExecuteResult.FINISHED

        if (preparedScheduleInfo.experimentResult?.isMatching == true) {
            analytics.recordEvent(
                event = InAppResolutionEvent.control(preparedScheduleInfo.experimentResult),
                layoutContext = null
            )
        } else {
            try {
                UALog.i { "Displaying message ${preparedScheduleInfo.scheduleID}" }
                result = when(data.displayAdapter.display(context, analytics)) {
                    DisplayResult.CANCEL -> ScheduleExecuteResult.CANCEL
                    DisplayResult.FINISHED -> ScheduleExecuteResult.FINISHED
                }
                data.message.actions?.let {
                    InAppActionUtils.runActions(it, actionRunnerFactory)
                }
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to display message" }
                result = ScheduleExecuteResult.RETRY
            } finally {
                // Finished
                data.displayCoordinator.messageFinishedDisplaying(data.message)
                displayDelegate?.messageFinishedDisplaying(data.message, preparedScheduleInfo.scheduleID)
            }
        }

        // Clean up assets
        if (result != ScheduleExecuteResult.RETRY) {
            assetManager.clearCache(preparedScheduleInfo.scheduleID)
        }

        return result
    }

    override suspend fun interrupted(
        schedule: AutomationSchedule,
        preparedScheduleInfo: PreparedScheduleInfo
    ): InterruptedBehavior {
        return when(schedule.data) {
            is AutomationSchedule.ScheduleData.InAppMessageData -> {
                if (schedule.data.message.isEmbedded()) {
                    return InterruptedBehavior.RETRY
                }

                analyticsFactory
                    .makeAnalytics(schedule.data.message, preparedScheduleInfo)
                    .recordEvent(InAppResolutionEvent.interrupted(), null)

                assetManager.clearCache(preparedScheduleInfo.scheduleID)

                return InterruptedBehavior.FINISH
            }
            else -> InterruptedBehavior.FINISH
        }
    }

    fun notifyDisplayConditionsChanged() {
        scheduleConditionsChangedNotifier.notifyChanged()
    }
}