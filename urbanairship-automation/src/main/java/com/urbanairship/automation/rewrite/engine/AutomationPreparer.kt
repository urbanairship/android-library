package com.urbanairship.automation.rewrite.engine

import android.content.Context
import android.os.Looper
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.automation.rewrite.AutomationAudience
import com.urbanairship.automation.rewrite.AutomationSchedule
import com.urbanairship.automation.rewrite.deferred.DeferredAutomationData
import com.urbanairship.automation.rewrite.deferred.DeferredScheduleResult
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.PreparedInAppMessageData
import com.urbanairship.automation.rewrite.isInAppMessageType
import com.urbanairship.automation.rewrite.limits.FrequencyCheckerInterface
import com.urbanairship.automation.rewrite.limits.FrequencyLimitManager
import com.urbanairship.automation.rewrite.remotedata.AutomationRemoteDataAccess
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.deferred.DeferredResult
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.experiment.MessageInfo
import com.urbanairship.json.JsonValue
import com.urbanairship.util.RetryingExecutor
import com.urbanairship.util.RetryingExecutor.Operation
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.runBlocking

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AutomationPreparerInterface {
    public suspend fun prepare(context: Context, schedule: AutomationSchedule, deferredContext: DeferredTriggerContext?): SchedulePrepareResult
    public suspend fun cancelled(schedule: AutomationSchedule)
}

internal interface AutomationPreparerDelegate<DataIn, DataOut> {
    suspend fun prepare(data: DataIn, preparedScheduleInfo: PreparedScheduleInfo) : DataOut
    suspend fun cancelled(scheduleID: String)
}

internal class AutomationPreparer internal constructor(
    private val actionPreparer: AutomationPreparerDelegate<JsonValue, JsonValue>,
    private val messagePreparer: AutomationPreparerDelegate<InAppMessage, PreparedInAppMessageData>,
    private val deferredResolver: DeferredResolver,
    private val frequencyLimitManager: FrequencyLimitManager,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val audienceChecker: AudienceSelector,
    private val experiments: ExperimentManager,
    private val remoteDataAccess: AutomationRemoteDataAccess,
    private val queues: Queues = Queues()
): AutomationPreparerInterface {

    internal companion object {
        private const val DEFERRED_RESULT_KEY = "AirshipAutomation#deferredResult"
        private const val DEFAULT_MESSAGE_TYPE = "transactional"
    }

    override suspend fun cancelled(schedule: AutomationSchedule) {
        if (schedule.isInAppMessageType()) {
            messagePreparer.cancelled(schedule.identifier)
        } else {
            actionPreparer.cancelled(schedule.identifier)
        }
    }

    override suspend fun prepare(
        context: Context,
        schedule: AutomationSchedule,
        deferredContext: DeferredTriggerContext?
    ): SchedulePrepareResult {
        UALog.v { "Preparing ${schedule.identifier}" }

        return suspendCoroutine {
            val queues = queues.queue(schedule.queue)

            queues.execute(Operation {
                try {
                    if (runBlocking { remoteDataAccess.requiredUpdate(schedule) }) {
                        UALog.v { "Schedule out of date ${schedule.identifier}" }
                        runBlocking { remoteDataAccess.waitForFullRefresh(schedule) }
                        it.resume(SchedulePrepareResult.Invalidate)
                        return@Operation RetryingExecutor.finishedResult()
                    }

                    if (!runBlocking { remoteDataAccess.bestEffortRefresh(schedule) }) {
                        UALog.v { "Schedule out of date ${schedule.identifier}" }
                        it.resume(SchedulePrepareResult.Invalidate)
                        return@Operation RetryingExecutor.finishedResult()
                    }

                    val frequencyChecker: FrequencyCheckerInterface
                    try {
                        frequencyChecker = runBlocking {
                            frequencyLimitManager.getFrequencyChecker(
                                constraintIDs = schedule.frequencyConstraintIDs
                            )
                        }
                        if (runBlocking { frequencyChecker.isOverLimit() }) {
                            UALog.v { "Frequency limits exceeded ${schedule.identifier}" }
                            it.resume(SchedulePrepareResult.Skip)
                            return@Operation RetryingExecutor.finishedResult()
                        }
                    } catch (ex: Exception) {
                        UALog.e(ex) { "Failed to fetch frequency checker for schedule ${schedule.identifier}" }
                        runBlocking { remoteDataAccess.notifyOutdated(schedule) }
                        it.resume(SchedulePrepareResult.Invalidate)
                        return@Operation RetryingExecutor.finishedResult()
                    }

                    schedule.audience?.let { _ ->
                        val match = runBlocking {
                            audienceChecker.evaluate(
                                context = context,
                                newEvaluationDate = schedule.created.toLong(),
                                infoProvider = deviceInfoProvider.snapshot(context),
                                contactId = remoteDataAccess.contactIDFor(schedule)
                            )
                        }

                        if (!match) {
                            it.resume(schedule.missedAudiencePrepareResult())
                            return@Operation RetryingExecutor.finishedResult() //TODO: ignoreReturnOrder: true
                        }
                    }

                    val experimentResult = if (schedule.evaluateExperiments()) {
                        runBlocking { experiments.evaluateExperiments(
                            messageInfo = MessageInfo(
                                messageType = schedule.messageType ?: DEFAULT_MESSAGE_TYPE,
                                campaigns = schedule.campaigns
                            ),
                            contactId = deviceInfoProvider.getStableContactId()
                        ) }
                    } else {
                        null
                    }

                    val scheduleInfo = PreparedScheduleInfo(
                        scheduleID = schedule.identifier,
                        productID = schedule.productID,
                        campaigns = schedule.campaigns,
                        contactID = runBlocking { deviceInfoProvider.getStableContactId() },
                        experimentResult = experimentResult,
                        reportingContext = schedule.reportingContext
                    )

                    return@Operation runBlocking { prepareData(
                        context = context,
                        data = schedule.data,
                        triggerContext = deferredContext,
                        deviceInfoProvider = deviceInfoProvider,
                        scheduleInfo = scheduleInfo,
                        frequencyChecker = frequencyChecker,
                        schedule = schedule,
                        continuation = it
                    ) }
                } catch (ex: Exception) {
                    UALog.e(ex) { "Failed to prepare ${schedule.identifier}" }
                    return@Operation RetryingExecutor.retryResult()
                }
            })

        }
    }

    private suspend fun prepareData(
        context: Context,
        data: AutomationSchedule.ScheduleData,
        triggerContext: DeferredTriggerContext?,
        deviceInfoProvider: DeviceInfoProvider,
        scheduleInfo: PreparedScheduleInfo,
        frequencyChecker: FrequencyCheckerInterface,
        schedule: AutomationSchedule,
        continuation: Continuation<SchedulePrepareResult>
    ): RetryingExecutor.Result {
        return when(data) {
            is AutomationSchedule.ScheduleData.Actions -> {
                continuation.resume(SchedulePrepareResult.Prepared(
                    PreparedSchedule(
                        info = scheduleInfo,
                        data = PreparedScheduleData.Action(actionPreparer.prepare(data.actions, scheduleInfo)),
                        frequencyChecker = frequencyChecker
                    )
                ))
                RetryingExecutor.finishedResult()
            }
            is AutomationSchedule.ScheduleData.InAppMessageData -> {
                if (!data.message.displayContent.validate()) {
                    UALog.d { "⚠️ Message did not pass validation: ${data.message.name} - skipping(${schedule.identifier})." }
                    continuation.resume(SchedulePrepareResult.Skip)
                    RetryingExecutor.finishedResult()
                }

                continuation.resume(SchedulePrepareResult.Prepared(
                    PreparedSchedule(
                        info = scheduleInfo,
                        data = PreparedScheduleData.InAppMessage(messagePreparer.prepare(data.message, scheduleInfo)),
                        frequencyChecker = frequencyChecker
                    )
                ))
                RetryingExecutor.finishedResult()
            }
            is AutomationSchedule.ScheduleData.Deferred -> {
                prepareDeferred(
                    context = context,
                    deferred = data.deferred,
                    triggerContext = triggerContext,
                    deviceInfoProvider = deviceInfoProvider,
                    schedule = schedule,
                    frequencyChecker = frequencyChecker,
                    continuation = continuation,
                    onResult = {
                        prepareData(
                            context = context,
                            data = it,
                            triggerContext = triggerContext,
                            deviceInfoProvider = deviceInfoProvider,
                            scheduleInfo = scheduleInfo,
                            frequencyChecker = frequencyChecker,
                            schedule = schedule,
                            continuation = continuation
                        )
                    }
                )

            }

        }
    }

    private suspend fun prepareDeferred(
        context: Context,
        deferred: DeferredAutomationData,
        triggerContext: DeferredTriggerContext?,
        deviceInfoProvider: DeviceInfoProvider,
        schedule: AutomationSchedule,
        frequencyChecker: FrequencyCheckerInterface,
        continuation: Continuation<SchedulePrepareResult>,
        onResult: suspend (AutomationSchedule.ScheduleData) -> RetryingExecutor.Result
    ): RetryingExecutor.Result {
        UALog.v { "Resolving deferred ${schedule.identifier}" }

        val channelID = deviceInfoProvider.channelId
        if (channelID == null) {
            UALog.v { "Unable to resolve deferred until channel is created ${schedule.identifier}" }
            return RetryingExecutor.retryResult()
        }

        val request = DeferredRequest(
            uri = deferred.url,
            channelID = channelID,
            triggerContext = triggerContext,
            locale = deviceInfoProvider.getUserLocale(context),
            notificationOptIn = deviceInfoProvider.isNotificationsOptedIn
        )

        //TODO: resolve from cache
        /*
        if let cached: AutomationSchedule.ScheduleData = await retryState.value(key: Self.deferredResultKey) {
            AirshipLogger.trace("Deferred resolved from cache \(schedule.identifier)")

            return try await onResult(cached)
        }
         */

        val result = deferredResolver.resolve(request, DeferredScheduleResult::fromJson)
        UALog.v { "Deferred result ${schedule.identifier} $result" }

        when(result) {
            is DeferredResult.NotFound -> {
                remoteDataAccess.notifyOutdated(schedule)
                continuation.resume(SchedulePrepareResult.Invalidate)
                return RetryingExecutor.finishedResult()
            }
            is DeferredResult.OutOfDate -> {
                remoteDataAccess.notifyOutdated(schedule)
                continuation.resume(SchedulePrepareResult.Invalidate)
                return RetryingExecutor.finishedResult()
            }
            is DeferredResult.RetriableError -> {
                val interval = result.retryAfter ?: return RetryingExecutor.retryResult()
                return RetryingExecutor.retryResult(interval)
            }
            is DeferredResult.TimedOut -> {
                if (deferred.retryOnTimeOut != false) {
                    return RetryingExecutor.retryResult()
                }

                continuation.resume(SchedulePrepareResult.Penalize)
                return RetryingExecutor.finishedResult() // ignoreReturnOrder: true
            }
            is DeferredResult.Success -> {
                if (!result.result.isAudienceMatch) {
                    continuation.resume(schedule.missedAudiencePrepareResult())
                    return RetryingExecutor.finishedResult() // ignoreReturnOrder: true
                }

                when(deferred.type) {
                    DeferredAutomationData.DeferredType.ACTIONS -> {
                        val actions = result.result.actions
                        if (actions == null) {
                            UALog.v { "Failed to get result for deferred ${schedule.identifier}" }
                            return RetryingExecutor.retryResult()
                        }
                        return onResult(AutomationSchedule.ScheduleData.Actions(actions))
                    }
                    DeferredAutomationData.DeferredType.IN_APP_MESSAGE -> {
                        val message = result.result.message
                        if (message == null) {
                            UALog.v { "Failed to get result for deferred ${schedule.identifier}" }
                            return RetryingExecutor.retryResult()
                        }

                        message.source = InAppMessage.InAppMessageSource.REMOTE_DATA
                        return onResult(AutomationSchedule.ScheduleData.InAppMessageData(message))
                    }
                }
            }
        }
    }
}

private fun AutomationSchedule.missedAudiencePrepareResult(): SchedulePrepareResult {
    return when(audience?.missBehavior ?: AutomationAudience.MissBehavior.PENALIZE) {
        AutomationAudience.MissBehavior.CANCEL -> SchedulePrepareResult.Cancel
        AutomationAudience.MissBehavior.SKIP -> SchedulePrepareResult.Skip
        AutomationAudience.MissBehavior.PENALIZE -> SchedulePrepareResult.Penalize
    }
}

private fun AutomationSchedule.evaluateExperiments(): Boolean {
    return isInAppMessageType() && bypassHoldoutGroups != true
}

internal class Queues(
    private var queues: MutableMap<String, RetryingExecutor> = mutableMapOf(),
    private val defaultQueue: RetryingExecutor = RetryingExecutor.newSerialExecutor(Looper.getMainLooper())
) {

    fun queue(name: String?): RetryingExecutor {
        val executorName = name ?: return defaultQueue

        fun makeAndSaveExecutor(): RetryingExecutor {
            val result = RetryingExecutor.newSerialExecutor(Looper.getMainLooper())
            queues[executorName] = result
            return result
        }

        return queues[executorName] ?: makeAndSaveExecutor()
    }
}
