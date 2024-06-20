/* Copyright Airship and Contributors */

package com.urbanairship.iam

import android.content.Context
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.actions.Action
import com.urbanairship.actions.run
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.engine.AutomationExecutorDelegate
import com.urbanairship.automation.engine.InterruptedBehavior
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.ScheduleExecuteResult
import com.urbanairship.automation.engine.ScheduleReadyResult
import com.urbanairship.automation.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.iam.adapter.DisplayResult
import com.urbanairship.iam.analytics.InAppMessageAnalyticsFactory
import com.urbanairship.iam.analytics.events.InAppResolutionEvent
import com.urbanairship.iam.assets.AssetCacheManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class InAppMessageAutomationExecutor(
    private val context: Context,
    private val assetManager: AssetCacheManager,
    private val analyticsFactory: InAppMessageAnalyticsFactory,
    private val scheduleConditionsChangedNotifier: ScheduleConditionsChangedNotifier,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) : AutomationExecutorDelegate<PreparedInAppMessageData> {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    var displayDelegate: InAppMessageDisplayDelegate? = null
        get() { synchronized(this) { return field } }
        set(value) { synchronized(this) { field = value } }

    override fun isReady(
        data: PreparedInAppMessageData,
        preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleReadyResult {
        if (!data.displayAdapter.isReady.value) {
            UALog.i { "Schedule ${preparedScheduleInfo.scheduleId} display adapter not ready" }
            scope.launch {
                data.displayAdapter.isReady.first { it }
                scheduleConditionsChangedNotifier.notifyChanged()
            }
            return ScheduleReadyResult.NOT_READY
        }

        if (!data.displayCoordinator.isReady.value) {
            UALog.i { "Schedule ${preparedScheduleInfo.scheduleId} display coordinator not ready" }
            scope.launch {
                data.displayCoordinator.isReady.first { it }
                scheduleConditionsChangedNotifier.notifyChanged()
            }
            return ScheduleReadyResult.NOT_READY
        }

        val isReady = displayDelegate?.isMessageReadyToDisplay(data.message, preparedScheduleInfo.scheduleId)
            ?: true

        if (!isReady) {
            UALog.i { "Schedule ${preparedScheduleInfo.scheduleId} InAppMessageDisplayDelegate not ready" }
            return ScheduleReadyResult.NOT_READY
        }

        return ScheduleReadyResult.READY
    }

    override suspend fun execute(
        data: PreparedInAppMessageData,
        preparedScheduleInfo: PreparedScheduleInfo
    ): ScheduleExecuteResult = withContext(Dispatchers.Main.immediate) {

        /**
         * Suspending calls until before the message starts to display should be avoided otherwise
         * the display will happen on another run loop and any guarantees about the state of the
         * app are lost.
         */

        if (!preparedScheduleInfo.additionalAudienceCheckResult) {
            UALog.i { "Schedule ${preparedScheduleInfo.scheduleId} missed additional audience check." }
            data.analytics.recordEvent(
                event = InAppResolutionEvent.audienceExcluded(),
                layoutContext = null
            )
            return@withContext ScheduleExecuteResult.FINISHED
        }

        // Display
        displayDelegate?.messageWillDisplay(data.message, preparedScheduleInfo.scheduleId)
        data.displayCoordinator.messageWillDisplay(data.message)

        var result = ScheduleExecuteResult.FINISHED

        if (preparedScheduleInfo.experimentResult?.isMatching == true) {
            UALog.i { "Schedule ${preparedScheduleInfo.scheduleId} part of experiment." }
            data.analytics.recordEvent(
                event = InAppResolutionEvent.control(preparedScheduleInfo.experimentResult),
                layoutContext = null
            )
        } else {
            try {
                UALog.i { "Displaying message ${preparedScheduleInfo.scheduleId}" }
                result = when(data.displayAdapter.display(context, data.analytics)) {
                    DisplayResult.CANCEL -> ScheduleExecuteResult.CANCEL
                    DisplayResult.FINISHED -> ScheduleExecuteResult.FINISHED
                }
                data.message.actions?.let {
                    data.actionRunner.run(it.map, Action.SITUATION_AUTOMATION)
                }
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to display message" }
                result = ScheduleExecuteResult.RETRY
            } finally {
                // Finished
                data.displayCoordinator.messageFinishedDisplaying(data.message)
                displayDelegate?.messageFinishedDisplaying(data.message, preparedScheduleInfo.scheduleId)
            }
        }

        // Clean up assets
        if (result != ScheduleExecuteResult.RETRY) {
            assetManager.clearCache(preparedScheduleInfo.scheduleId)
        }

        return@withContext result
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

                assetManager.clearCache(preparedScheduleInfo.scheduleId)

                return InterruptedBehavior.FINISH
            }
            else -> InterruptedBehavior.FINISH
        }
    }

    fun notifyDisplayConditionsChanged() {
        scheduleConditionsChangedNotifier.notifyChanged()
    }
}
