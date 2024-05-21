package com.urbanairship.automation.engine

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.deferred.DeferredAutomationData
import com.urbanairship.automation.deferred.DeferredScheduleResult
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.PreparedInAppMessageData
import com.urbanairship.automation.isInAppMessageType
import com.urbanairship.automation.limits.FrequencyChecker
import com.urbanairship.automation.limits.FrequencyLimitManager
import com.urbanairship.automation.remotedata.AutomationRemoteDataAccess
import com.urbanairship.automation.utils.RetryingQueue
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.deferred.DeferredResult
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.experiment.MessageInfo
import com.urbanairship.json.JsonValue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds

internal interface AutomationPreparerDelegate<DataIn, DataOut> {
    suspend fun prepare(data: DataIn, preparedScheduleInfo: PreparedScheduleInfo) : Result<DataOut>
    suspend fun cancelled(scheduleID: String)
}

internal class AutomationPreparer internal constructor(
    private val actionPreparer: AutomationPreparerDelegate<JsonValue, JsonValue>,
    private val messagePreparer: AutomationPreparerDelegate<InAppMessage, PreparedInAppMessageData>,
    private val deferredResolver: DeferredResolver,
    private val frequencyLimitManager: FrequencyLimitManager,
    private val deviceInfoProviderFactory: () -> DeviceInfoProvider = { DeviceInfoProvider.newCachingProvider() },
    private val experiments: ExperimentManager,
    private val remoteDataAccess: AutomationRemoteDataAccess,
    private val queues: Queues = Queues()
) {

    internal companion object {
        private const val DEFAULT_MESSAGE_TYPE = "transactional"
    }

    suspend fun cancelled(schedule: AutomationSchedule) {
        if (schedule.isInAppMessageType()) {
            messagePreparer.cancelled(schedule.identifier)
        } else {
            actionPreparer.cancelled(schedule.identifier)
        }
    }

    suspend fun prepare(
        context: Context,
        schedule: AutomationSchedule,
        deferredContext: DeferredTriggerContext?
    ): SchedulePrepareResult {
        UALog.v { "Preparing ${schedule.identifier}" }

        val prepareCache = PrepareCache()
        return queues.queue(schedule.queue).run("Schedule ${schedule.identifier}") {

            val deviceInfoProvider = deviceInfoProviderFactory()
            // Check if we are out of date
            if (remoteDataAccess.requiredUpdate(schedule)) {
                UALog.v { "Schedule out of date ${schedule.identifier}" }
                remoteDataAccess.waitForFullRefresh(schedule)
                return@run RetryingQueue.Result.Success(SchedulePrepareResult.Invalidate)
            }

            // Best effort refresh
            if (!remoteDataAccess.bestEffortRefresh(schedule) ) {
                UALog.v { "Schedule out of date ${schedule.identifier}" }
                return@run RetryingQueue.Result.Success(SchedulePrepareResult.Invalidate)
            }

            // Frequency Checker
            val frequencyChecker = frequencyLimitManager.getFrequencyChecker(
                schedule.frequencyConstraintIds
            ).getOrElse { ex ->
                UALog.e(ex) { "Failed to fetch frequency checker for schedule ${schedule.identifier}" }
                remoteDataAccess.notifyOutdated(schedule)
                return@run RetryingQueue.Result.Success(SchedulePrepareResult.Invalidate)
            }

            // Check if schedule is already over limit
            if (frequencyChecker?.isOverLimit() == true) {
                UALog.v { "Frequency limits exceeded ${schedule.identifier}" }
                return@run RetryingQueue.Result.Success(
                    result = SchedulePrepareResult.Skip,
                    ignoreReturnOrder = true
                )
            }

            // Audience checks
            schedule.audience?.let {
                val match = it.audienceSelector.evaluate(
                    newEvaluationDate = schedule.created.toLong(),
                    infoProvider = deviceInfoProvider,
                    contactId = remoteDataAccess.contactIdFor(schedule)
                )

                if (!match) {
                    return@run RetryingQueue.Result.Success(
                        result = schedule.missedAudiencePrepareResult(),
                        ignoreReturnOrder = true
                    )
                }
            }

            // Experiment result
            val experimentResult = evaluateExperiments(schedule, deviceInfoProvider).getOrElse { ex ->
                UALog.e(ex) { "Failed to evaluate hold out groups ${schedule.identifier}" }
                remoteDataAccess.notifyOutdated(schedule)
                return@run RetryingQueue.Result.Retry()
            }

            val scheduleInfo = PreparedScheduleInfo(
                scheduleId = schedule.identifier,
                productId = schedule.productId,
                campaigns = schedule.campaigns,
                contactId = deviceInfoProvider.getStableContactId(),
                experimentResult = experimentResult,
                reportingContext = schedule.reportingContext
            )

            prepareData(
                context = context,
                prepareCache = prepareCache,
                data = schedule.data,
                triggerContext = deferredContext,
                deviceInfoProvider = deviceInfoProvider,
                scheduleInfo = scheduleInfo,
                frequencyChecker = frequencyChecker,
                schedule = schedule,
            )
        }
    }

    private suspend fun prepareData(
        context: Context,
        prepareCache: PrepareCache,
        data: AutomationSchedule.ScheduleData,
        triggerContext: DeferredTriggerContext?,
        deviceInfoProvider: DeviceInfoProvider,
        scheduleInfo: PreparedScheduleInfo,
        frequencyChecker: FrequencyChecker?,
        schedule: AutomationSchedule,
    ): RetryingQueue.Result<SchedulePrepareResult> {
        return when(data) {
            is AutomationSchedule.ScheduleData.Actions -> {
                actionPreparer.prepare(data.actions, scheduleInfo).fold(
                    onFailure = {
                        UALog.e(it) { "Failed to prepare actions" }
                        RetryingQueue.Result.Retry()
                    },
                    onSuccess = {
                        RetryingQueue.Result.Success(
                            SchedulePrepareResult.Prepared(
                                PreparedSchedule(
                                    info = scheduleInfo,
                                    data = PreparedScheduleData.Action(it),
                                    frequencyChecker = frequencyChecker
                                )
                            )
                        )
                    }
                )
            }

            is AutomationSchedule.ScheduleData.InAppMessageData -> {
                if (!data.message.displayContent.validate()) {
                    UALog.d { "⚠️ Message did not pass validation: ${data.message.name} - skipping(${schedule.identifier})." }
                    RetryingQueue.Result.Success(SchedulePrepareResult.Skip)
                } else {
                    messagePreparer.prepare(data.message, scheduleInfo).fold(
                        onFailure = {
                            UALog.e(it) { "Failed to prepare message" }
                            RetryingQueue.Result.Retry()
                        },
                        onSuccess = {
                            RetryingQueue.Result.Success(
                                SchedulePrepareResult.Prepared(
                                    PreparedSchedule(
                                        info = scheduleInfo,
                                        data = PreparedScheduleData.InAppMessage(it),
                                        frequencyChecker = frequencyChecker
                                    )
                                )
                            )
                        }
                    )
                }
            }

            is AutomationSchedule.ScheduleData.Deferred -> {
                prepareDeferred(context = context,
                    prepareCache = prepareCache,
                    deferred = data.deferred,
                    triggerContext = triggerContext,
                    deviceInfoProvider = deviceInfoProvider,
                    schedule = schedule,
                    onResult = {
                        prepareData(
                            context = context,
                            prepareCache = prepareCache,
                            data = it,
                            triggerContext = triggerContext,
                            deviceInfoProvider = deviceInfoProvider,
                            scheduleInfo = scheduleInfo,
                            frequencyChecker = frequencyChecker,
                            schedule = schedule
                        )
                    }
                )
            }
        }
    }

    private suspend fun evaluateExperiments(
        schedule: AutomationSchedule,
        deviceInfoProvider: DeviceInfoProvider
    ): Result<ExperimentResult?> {
        return if (schedule.evaluateExperiments()) {
            experiments.evaluateExperiments(
                messageInfo = MessageInfo(
                    messageType = schedule.messageType ?: DEFAULT_MESSAGE_TYPE,
                    campaigns = schedule.campaigns
                ),
                deviceInfoProvider = deviceInfoProvider
            )
        } else {
            Result.success(null)
        }
    }

    private suspend fun prepareDeferred(
        context: Context,
        prepareCache: PrepareCache,
        deferred: DeferredAutomationData,
        triggerContext: DeferredTriggerContext?,
        deviceInfoProvider: DeviceInfoProvider,
        schedule: AutomationSchedule,
        onResult: suspend (AutomationSchedule.ScheduleData) -> RetryingQueue.Result<SchedulePrepareResult>
    ): RetryingQueue.Result<SchedulePrepareResult> {
        UALog.v { "Resolving deferred ${schedule.identifier}" }


        val request = DeferredRequest(
            uri = deferred.url,
            channelId = deviceInfoProvider.getChannelId(),
            triggerContext = triggerContext,
            locale = deviceInfoProvider.locale,
            notificationOptIn = deviceInfoProvider.isNotificationsOptedIn,
            appVersionName = deviceInfoProvider.appVersionName
        )


        val result = prepareCache.deferredResult ?: deferredResolver.resolve(request, DeferredScheduleResult::fromJson)
        UALog.v { "Deferred result ${schedule.identifier} $result" }

        return when(result) {
            is DeferredResult.NotFound -> {
                remoteDataAccess.notifyOutdated(schedule)
                RetryingQueue.Result.Success(SchedulePrepareResult.Invalidate)
            }

            is DeferredResult.OutOfDate -> {
                remoteDataAccess.notifyOutdated(schedule)
                RetryingQueue.Result.Success(SchedulePrepareResult.Invalidate)
            }

            is DeferredResult.RetriableError -> {
                RetryingQueue.Result.Retry(retryAfter = result.retryAfter?.seconds)
            }

            is DeferredResult.TimedOut -> {
                if (deferred.retryOnTimeOut != false) {
                    RetryingQueue.Result.Retry()
                } else {
                    RetryingQueue.Result.Success(
                        result = SchedulePrepareResult.Penalize,
                        ignoreReturnOrder = true
                    )
                }
            }

            is DeferredResult.Success -> {
                prepareCache.deferredResult = result

                if (result.result.isAudienceMatch) {
                    when(deferred.type) {
                        DeferredAutomationData.DeferredType.ACTIONS -> {
                            val actions = result.result.actions
                            if (actions == null) {
                                UALog.v { "Failed to get result for deferred ${schedule.identifier}" }
                                RetryingQueue.Result.Retry()
                            } else {
                                onResult(AutomationSchedule.ScheduleData.Actions(actions))
                            }
                        }
                        DeferredAutomationData.DeferredType.IN_APP_MESSAGE -> {
                            val message = result.result.message
                            if (message == null) {
                                UALog.v { "Failed to get result for deferred ${schedule.identifier}" }
                                RetryingQueue.Result.Retry()
                            } else {
                                onResult(AutomationSchedule.ScheduleData.InAppMessageData(message))
                            }
                        }
                    }
                } else {
                    RetryingQueue.Result.Success(
                        result = schedule.missedAudiencePrepareResult(),
                        ignoreReturnOrder = true
                    )
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

internal class Queues {
    private val defaultQueue = RetryingQueue()
    private var queues = mutableMapOf<String, RetryingQueue>()
    private val lock = ReentrantLock()

    fun queue(name: String?): RetryingQueue {
        return if (name == null) {
            defaultQueue
        } else {
            lock.withLock {
                queues.getOrPut(name) { RetryingQueue() }
            }
        }
    }
}

private class PrepareCache(
    var deferredResult: DeferredResult<DeferredScheduleResult>? = null
)
