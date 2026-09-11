/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import com.urbanairship.UALog
import com.urbanairship.audience.AudienceEvaluator
import com.urbanairship.audience.CompoundAudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.audiencecheck.AdditionalAudienceCheckerResolver
import com.urbanairship.automation.deferred.DeferredAutomationData
import com.urbanairship.automation.deferred.DeferredScheduleResult
import com.urbanairship.automation.AutomationAiSuppression
import com.urbanairship.automation.isInAppMessageType
import com.urbanairship.automation.limits.AutomationLedgerInterface
import com.urbanairship.automation.limits.FrequencyChecker
import com.urbanairship.automation.limits.FrequencyLimitManager
import com.urbanairship.automation.limits.LedgerExecutionResult
import com.urbanairship.automation.remotedata.AutomationRemoteDataAccess
import com.urbanairship.automation.utils.RetryingQueue
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

internal interface AutomationPreparerDelegate<DataIn, DataOut> {
    suspend fun prepare(data: DataIn, preparedScheduleInfo: PreparedScheduleInfo) : Result<DelegatePreparerResult<DataOut>>
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
    private val audienceEvaluator: AudienceEvaluator,
    private val ledger: AutomationLedgerInterface,
    queueConfigSupplier: (() -> RetryingQueueConfig?)? = null,
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
        triggerSessionId: String,
        triggerId: String? = null
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

            val audience = CompoundAudienceSelector.combine(
                compoundAudienceSelector = schedule.compoundAudience?.selector,
                deviceAudience = schedule.audience?.audienceSelector
            )

            if (audience != null) {
                val result = audienceEvaluator.evaluate(
                    compoundAudience = audience,
                    newEvaluationDate = schedule.created,
                    infoProvider = deviceInfoProvider
                )

                if (!result.isMatch) {
                    UALog.v { "Local audience miss for schedule ${schedule.identifier}" }
                    val behavior = schedule.effectiveAudienceMissBehavior
                    recordPenalty(schedule, triggerId, behavior)
                    return@run RetryingQueue.Result.Success(
                        result = behavior.toPrepareResult(),
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
                triggerId = triggerId,
                aiSuppression = schedule.aiSuppression,
                onDeferredRequest = {
                    deferredRequest(it, triggerContext = deferredContext, deviceInfoProvider)
                },
                onPrepareInfo = { aiSuppression ->
                    prepareInfo(
                        schedule, experimentResult, deviceInfoProvider, triggerSessionId,
                        triggerId, aiSuppression
                    )
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
        triggerSessionId: String,
        triggerId: String?,
        aiSuppression: AutomationAiSuppression?
    ): Result<PreparedScheduleInfo> {
        val additionalAudienceCheckResult = additionalAudienceResolver.resolve(
            deviceInfoProvider = deviceInfoProvider,
            overrides = schedule.additionalAudienceCheckOverrides
        ).getOrElse {
            UALog.v(it) { "Additional audience check failed ${schedule.identifier}" }
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
                additionalAudienceCheckResult = additionalAudienceCheckResult,
                priority = schedule.priority ?: 0,
                sendMetadata = schedule.sendMetadata,
                ledgerSharedId = schedule.ledgerConfig?.sharedId,
                triggerId = triggerId,
                aiSuppression = aiSuppression
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
            contactId = deviceInfoProvider.getStableContactInfo().contactId,
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
        triggerId: String?,
        onDeferredRequest: suspend (DeferredAutomationData) -> DeferredRequest,
        aiSuppression: AutomationAiSuppression?,
        onPrepareInfo: suspend (AutomationAiSuppression?) -> Result<PreparedScheduleInfo>,
        onPrepareSchedule: (PreparedScheduleInfo, PreparedScheduleData) -> PreparedSchedule,
    ): RetryingQueue.Result<SchedulePrepareResult> {

        when(data) {
            is AutomationSchedule.ScheduleData.Actions -> {
                val info = onPrepareInfo(aiSuppression).getOrElse {
                    UALog.e(it) { "Failed to prepare schedule data" }
                    return RetryingQueue.Result.Retry()
                }

                return actionPreparer.prepare(data.actions, info).fold(
                    onFailure = {
                        UALog.e(it) { "Failed to prepare actions" }
                        RetryingQueue.Result.Retry()
                    },
                    onSuccess = { result ->
                        settleDelegateOutcome(result, schedule, triggerId) { prepared ->
                            RetryingQueue.Result.Success(
                                SchedulePrepareResult.Prepared(onPrepareSchedule(info, PreparedScheduleData.Action(prepared)))
                            )
                        }
                    }
                )
            }

            is AutomationSchedule.ScheduleData.InAppMessageData -> {
                if (!data.message.displayContent.validate()) {
                    UALog.d { "⚠️ Message did not pass validation: ${data.message.name} - skipping(${schedule.identifier})." }
                    return RetryingQueue.Result.Success(SchedulePrepareResult.Skip)
                }

                val info = onPrepareInfo(aiSuppression).getOrElse {
                    UALog.e(it) { "Failed to prepare schedule data" }
                    return RetryingQueue.Result.Retry()
                }

                return messagePreparer.prepare(data.message, info).fold(
                    onFailure = {
                        UALog.e(it) { "Failed to prepare message" }
                        RetryingQueue.Result.Retry()
                    },
                    onSuccess = { result ->
                        settleDelegateOutcome(result, schedule, triggerId) { prepared ->
                            RetryingQueue.Result.Success(
                                SchedulePrepareResult.Prepared(onPrepareSchedule(info, PreparedScheduleData.InAppMessage(prepared)))
                            )
                        }
                    }
                )
            }

            is AutomationSchedule.ScheduleData.Deferred -> {
                return prepareDeferred(
                    prepareCache = prepareCache,
                    deferred = data.deferred,
                    deferredRequest = onDeferredRequest(data.deferred),
                    schedule = schedule,
                    triggerId = triggerId,
                    onResult = { data, deferredAiSuppression ->
                        prepareData(
                            prepareCache = prepareCache,
                            data = data,
                            schedule = schedule,
                            triggerId = triggerId,
                            // The deferred response wins when it carries its own config.
                            aiSuppression = deferredAiSuppression ?: aiSuppression,
                            onDeferredRequest = onDeferredRequest,
                            onPrepareInfo = onPrepareInfo,
                            onPrepareSchedule = onPrepareSchedule,
                        )
                    }
                )
            }
        }
    }

    /**
     * Records an outcome that ends an attempt with a miss behavior, when that
     * behavior consumes budget. `PENALIZE` records `AUDIENCE_MISS`; `CANCEL`
     * records `AUDIENCE_MISS` with `cancel = true`; `SKIP` records nothing.
     *
     * Used for both a failed audience check and an app suppression, which spend
     * a schedule's budget the same way.
     *
     * @param behavior The behavior actually applied, which a deferred response or an
     * app suppression may have decided. Passed in rather than read off the schedule so
     * the ledger entry and the prepare result cannot disagree.
     */
    private suspend fun recordPenalty(
        schedule: AutomationSchedule,
        triggerId: String?,
        behavior: AutomationAudience.MissBehavior
    ) {
        if (behavior == AutomationAudience.MissBehavior.SKIP) {
            return
        }

        recordPenalized(
            schedule = schedule,
            triggerId = triggerId,
            cancel = behavior == AutomationAudience.MissBehavior.CANCEL
        )
    }

    /**
     * Records the budget-consuming outcome every `PENALIZE` prepare result
     * shares, so a penalty always reaches the ledger. Without an event the
     * penalty spends nothing and the schedule can be penalized forever without
     * ever reaching its limit.
     *
     * The schema has no result of its own for a give-up deferred timeout, so it
     * lands in the same `AUDIENCE_MISS` bucket the miss behaviors use. That is
     * safe on the axis that decides delivery — the result never affects whether
     * an execution counts, only which executions an exclusion rule can
     * subtract — but a rule excluding `audience_miss` does also exclude
     * timeouts.
     */
    private suspend fun recordPenalized(
        schedule: AutomationSchedule,
        triggerId: String?,
        cancel: Boolean
    ) {
        ledger.recordExecution(
            scheduleId = schedule.identifier,
            sharedId = schedule.ledgerConfig?.sharedId,
            triggerId = triggerId,
            result = LedgerExecutionResult.AUDIENCE_MISS,
            cancel = cancel
        )
    }

    /**
     * Maps a delegate's outcome to a prepare result, recording the ones that
     * spend the schedule's budget.
     *
     * A delegate can end an attempt with its own miss behavior — the app's
     * `onCheckSuppression` does exactly that — which consumes budget the same
     * way an audience miss does. Every delegate outcome routes through here, so
     * such a path reaches the ledger without each delegate having to record for
     * itself.
     *
     * @param onPrepared Wraps the delegate's data, which only the caller knows
     * the shape of.
     */
    private suspend fun <DataOut> settleDelegateOutcome(
        outcome: DelegatePreparerResult<DataOut>,
        schedule: AutomationSchedule,
        triggerId: String?,
        onPrepared: (DataOut) -> RetryingQueue.Result<SchedulePrepareResult>
    ): RetryingQueue.Result<SchedulePrepareResult> = when (outcome) {
        is DelegatePreparerResult.Prepared -> onPrepared(outcome.data)

        DelegatePreparerResult.Cancel -> {
            recordPenalty(schedule, triggerId, AutomationAudience.MissBehavior.CANCEL)
            RetryingQueue.Result.Success(SchedulePrepareResult.Cancel, ignoreReturnOrder = true)
        }

        DelegatePreparerResult.Skip -> {
            recordPenalty(schedule, triggerId, AutomationAudience.MissBehavior.SKIP)
            RetryingQueue.Result.Success(SchedulePrepareResult.Skip, ignoreReturnOrder = true)
        }

        DelegatePreparerResult.Penalize -> {
            recordPenalty(schedule, triggerId, AutomationAudience.MissBehavior.PENALIZE)
            RetryingQueue.Result.Success(SchedulePrepareResult.Penalize, ignoreReturnOrder = true)
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
        triggerId: String?,
        onResult: suspend (
            AutomationSchedule.ScheduleData,
            AutomationAiSuppression?
        ) -> RetryingQueue.Result<SchedulePrepareResult>
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
                RetryingQueue.Result.Retry(retryAfter = result.retryAfter)
            }

            is DeferredResult.TimedOut -> {
                if (deferred.retryOnTimeOut != false) {
                    RetryingQueue.Result.Retry()
                } else {
                    // Giving up penalizes the schedule, which spends budget the
                    // same way an audience miss does. `recordAudienceMiss` is
                    // not reusable here: this penalizes regardless of the
                    // schedule's miss behavior, `SKIP` included.
                    recordPenalized(schedule, triggerId, cancel = false)
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
                                onResult(
                                    AutomationSchedule.ScheduleData.Actions(actions),
                                    result.result.aiSuppression
                                )
                            }
                        }
                        DeferredAutomationData.DeferredType.IN_APP_MESSAGE -> {
                            val message = result.result.message
                            if (message == null) {
                                UALog.v { "Failed to get result for deferred ${schedule.identifier}" }
                                RetryingQueue.Result.Retry()
                            } else {
                                onResult(
                                    AutomationSchedule.ScheduleData.InAppMessageData(message),
                                    result.result.aiSuppression
                                )
                            }
                        }
                    }
                } else {
                    // The deferred response wins when it provides its own behavior.
                    val behavior = result.result.missBehavior
                        ?: schedule.effectiveAudienceMissBehavior
                    recordPenalty(schedule, triggerId, behavior)
                    RetryingQueue.Result.Success(
                        result = behavior.toPrepareResult(),
                        ignoreReturnOrder = true
                    )
                }
            }
        }
    }
}

/**
 * The effective miss behavior after combining compound and device audiences,
 * defaulting to `penalize` when no behavior is configured.
 */
private val AutomationSchedule.effectiveAudienceMissBehavior: AutomationAudience.MissBehavior
    get() = compoundAudience?.missBehavior
        ?: audience?.missBehavior
        ?: AutomationAudience.MissBehavior.PENALIZE

private fun AutomationSchedule.evaluateExperiments(): Boolean {
    return isInAppMessageType() && bypassHoldoutGroups != true
}

internal class Queues(
    private val configSupplier: (() -> RetryingQueueConfig?)?
) {
    private val defaultQueue: RetryingQueue by lazy { RetryingQueue(config = configSupplier?.invoke()) }
    private var queues = mutableMapOf<String, RetryingQueue>()
    private val lock = ReentrantLock()

    fun queue(name: String?): RetryingQueue {
        return if (name == null) {
            defaultQueue
        } else {
            lock.withLock {
                queues.getOrPut(name) { RetryingQueue(config = configSupplier?.invoke()) }
            }
        }
    }
}

private class PrepareCache(
    var deferredResult: DeferredResult<DeferredScheduleResult>? = null
)
