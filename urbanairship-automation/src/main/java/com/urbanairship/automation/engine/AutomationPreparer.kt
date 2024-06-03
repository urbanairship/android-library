/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import com.urbanairship.UALog
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.audiencecheck.AdditionalAudienceCheckerResolver
import com.urbanairship.automation.deferred.DeferredAutomationData
import com.urbanairship.automation.deferred.DeferredScheduleResult
import com.urbanairship.automation.isInAppMessageType
import com.urbanairship.automation.limits.FrequencyChecker
import com.urbanairship.automation.limits.FrequencyLimitManager
import com.urbanairship.automation.remotedata.AutomationRemoteDataAccess
import com.urbanairship.automation.utils.RetryingQueue
import com.urbanairship.base.Supplier
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.deferred.DeferredResult
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.experiment.MessageInfo
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.PreparedInAppMessageData
import com.urbanairship.json.JsonValue
import com.urbanairship.remoteconfig.RetryingQueueConfig
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
    private val deviceInfoProviderFactory: (String?) -> DeviceInfoProvider = { DeviceInfoProvider.newCachingProvider(contactId = it) },
    private val experiments: ExperimentManager,
    private val remoteDataAccess: AutomationRemoteDataAccess,
    private val additionalAudienceResolver: AdditionalAudienceCheckerResolver,
    queueConfigSupplier: Supplier<RetryingQueueConfig?>? = null,
    private val queues: Queues = Queues(queueConfigSupplier),
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
        schedule: AutomationSchedule,
        deferredContext: DeferredTriggerContext?,
        triggerSessionId: String
    ): SchedulePrepareResult {
        UALog.v { "Preparing ${schedule.identifier}" }

        val prepareCache = PrepareCache()
        return queues.queue(schedule.queue).run("Schedule ${schedule.identifier}") {

            val deviceInfoProvider = deviceInfoProviderFactory(remoteDataAccess.contactIdFor(schedule))
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
                    infoProvider = deviceInfoProvider
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

            prepareData(
                prepareCache = prepareCache,
                data = schedule.data,
                schedule = schedule,
                onDeferredRequest = {
                    deferredRequest(it, triggerContext = deferredContext, deviceInfoProvider)
                },
                onPrepareInfo = {
                    prepareInfo(schedule, experimentResult, deviceInfoProvider, triggerSessionId)
                },
                onPrepareSchedule = { info, data ->
                    prepareSchedule(info, data, frequencyChecker)
                }
            )
        }
    }

    private suspend fun prepareInfo(
        schedule: AutomationSchedule,
        experimentResult: ExperimentResult?,
        deviceInfoProvider: DeviceInfoProvider,
        triggerSessionId: String
    ): Result<PreparedScheduleInfo> {
        val additionalAudienceCheckResult = additionalAudienceResolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            overrides = schedule.additionalAudienceCheckOverrides
        ).getOrElse {
            return Result.failure(it)
        }

        return Result.success(
            PreparedScheduleInfo(
                scheduleId = schedule.identifier,
                productId = schedule.productId,
                campaigns = schedule.campaigns,
                contactId = deviceInfoProvider.getStableContactInfo().contactId,
                experimentResult = experimentResult,
                reportingContext = schedule.reportingContext,
                triggerSessionId = triggerSessionId,
                additionalAudienceCheckResult = additionalAudienceCheckResult
            )
        )
    }

    private fun prepareSchedule(
        info: PreparedScheduleInfo,
        data: PreparedScheduleData,
        frequencyChecker: FrequencyChecker?
    ): PreparedSchedule {
        return PreparedSchedule(
            info = info,
            data = data,
            frequencyChecker = frequencyChecker
        )
    }

    private suspend fun deferredRequest(
        deferred: DeferredAutomationData,
        triggerContext: DeferredTriggerContext?,
        deviceInfoProvider: DeviceInfoProvider,
    ): DeferredRequest {
        return DeferredRequest(
            uri = deferred.url,
            channelId = deviceInfoProvider.getChannelId(),
            triggerContext = triggerContext,
            locale = deviceInfoProvider.locale,
            notificationOptIn = deviceInfoProvider.isNotificationsOptedIn,
            appVersionName = deviceInfoProvider.appVersionName
        )
    }

    private suspend fun prepareData(
        prepareCache: PrepareCache,
        data: AutomationSchedule.ScheduleData,
        schedule: AutomationSchedule,
        onDeferredRequest: suspend (DeferredAutomationData) -> DeferredRequest,
        onPrepareInfo: suspend () -> Result<PreparedScheduleInfo>,
        onPrepareSchedule: (PreparedScheduleInfo, PreparedScheduleData) -> PreparedSchedule,
    ): RetryingQueue.Result<SchedulePrepareResult> {

        when(data) {
            is AutomationSchedule.ScheduleData.Actions -> {
                val info = onPrepareInfo().getOrElse {
                    UALog.e(it) { "Failed to prepare schedule data" }
                    return RetryingQueue.Result.Retry()
                }

                return actionPreparer.prepare(data.actions, info).fold(
                    onFailure = {
                        UALog.e(it) { "Failed to prepare actions" }
                        RetryingQueue.Result.Retry()
                    },
                    onSuccess = {
                        RetryingQueue.Result.Success(
                            SchedulePrepareResult.Prepared(
                                onPrepareSchedule(info, PreparedScheduleData.Action(it))
                            )
                        )
                    }
                )
            }

            is AutomationSchedule.ScheduleData.InAppMessageData -> {
                if (!data.message.displayContent.validate()) {
                    UALog.d { "⚠️ Message did not pass validation: ${data.message.name} - skipping(${schedule.identifier})." }
                    return RetryingQueue.Result.Success(SchedulePrepareResult.Skip)
                }

                val info = onPrepareInfo().getOrElse {
                    UALog.e(it) { "Failed to prepare schedule data" }
                    return RetryingQueue.Result.Retry()
                }

                return messagePreparer.prepare(data.message, info).fold(
                    onFailure = {
                        UALog.e(it) { "Failed to prepare message" }
                        RetryingQueue.Result.Retry()
                    },
                    onSuccess = {
                        RetryingQueue.Result.Success(
                            SchedulePrepareResult.Prepared(
                                onPrepareSchedule(info, PreparedScheduleData.InAppMessage(it))
                            )
                        )
                    }
                )
            }

            is AutomationSchedule.ScheduleData.Deferred -> {
                return prepareDeferred(
                    prepareCache = prepareCache,
                    deferred = data.deferred,
                    deferredRequest = onDeferredRequest(data.deferred),
                    schedule = schedule,
                    onResult = {
                        prepareData(
                            prepareCache = prepareCache,
                            data = it,
                            schedule = schedule,
                            onDeferredRequest = onDeferredRequest,
                            onPrepareInfo = onPrepareInfo,
                            onPrepareSchedule = onPrepareSchedule,
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
        prepareCache: PrepareCache,
        deferred: DeferredAutomationData,
        deferredRequest: DeferredRequest,
        schedule: AutomationSchedule,
        onResult: suspend (AutomationSchedule.ScheduleData) -> RetryingQueue.Result<SchedulePrepareResult>
    ): RetryingQueue.Result<SchedulePrepareResult> {
        UALog.v { "Resolving deferred ${schedule.identifier}" }

        val result = prepareCache.deferredResult ?: deferredResolver.resolve(deferredRequest, DeferredScheduleResult::fromJson)
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

internal class Queues(
    private val configSupplier: Supplier<RetryingQueueConfig?>?
) {
    private val defaultQueue: RetryingQueue by lazy { RetryingQueue(config = configSupplier?.get()) }
    private var queues = mutableMapOf<String, RetryingQueue>()
    private val lock = ReentrantLock()

    fun queue(name: String?): RetryingQueue {
        return if (name == null) {
            defaultQueue
        } else {
            lock.withLock {
                queues.getOrPut(name) { RetryingQueue(config = configSupplier?.get()) }
            }
        }
    }
}

private class PrepareCache(
    var deferredResult: DeferredResult<DeferredScheduleResult>? = null
)
