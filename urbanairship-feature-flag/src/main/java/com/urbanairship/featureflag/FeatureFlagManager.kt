/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.AudienceEvaluator
import com.urbanairship.audience.CompoundAudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.json.JsonMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Feature flag remote data statuses
 */
public enum class FeatureFlagRemoteDataStatus {
    UP_TO_DATE, STALE, OUT_OF_DATE;
}

/**
 * Airship Feature Flags manager.
 * @property resultCache Flag result cache that can be used by [flag] to return a previous result if the
 * flag is not found or fails to resolve.
 */
@OpenForTesting
public class FeatureFlagManager internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val audienceEvaluator: AudienceEvaluator,
    private val remoteData: FeatureFlagRemoteDataAccess,
    private val infoProviderFactory: () -> DeviceInfoProvider = {
        DeviceInfoProvider.newCachingProvider()
    },
    private val deferredResolver: FlagDeferredResolver,
    private val featureFlagAnalytics: FeatureFlagAnalytics,
    private val privacyManager: PrivacyManager,
    public val resultCache: FeatureFlagResultCache
) : AirshipComponent(context, dataStore) {

    public companion object {

        /**
         * Gets the shared `FeatureFlagManager` instance.
         *
         * @return an instance of `FeatureFlagManager`.
         */
        @JvmStatic
        public fun shared(): FeatureFlagManager =
            Airship.shared().requireComponent(FeatureFlagManager::class.java)
    }

    private val pendingResultScope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())

    /**
     * Gets and evaluates a feature flag and returns it as a PendingResult. The [PrivacyManager.Feature.FEATURE_FLAGS]
     * must be enabled or this method will return null.
     *
     * @param name The flag name
     * @param useResultCache If the result cache should be used or not when the flag fails to resolve
     * or is not found.
     * @return an instance of `PendingResult<FeatureFlag>`.
     */
    @JvmOverloads
    public fun flagAsPendingResult(name: String, useResultCache: Boolean = true): PendingResult<FeatureFlag> {
        val result = PendingResult<FeatureFlag>()
        pendingResultScope.launch {
            result.setResult(flag(name, useResultCache).getOrNull())
        }
        return result
    }

    /**
     * Gets and evaluates a feature flag. The [PrivacyManager.Feature.FEATURE_FLAGS]
     * must be enabled or this method will return an error.
     * @param name The flag name
     * @param useResultCache If the result cache should be used or not when the flag fails to resolve
     * or is not found.
     * @return an instance of `Result<FeatureFlag>`.
     */
    public suspend fun flag(name: String, useResultCache: Boolean = true): Result<FeatureFlag> {
        if (!privacyManager.isEnabled(PrivacyManager.Feature.FEATURE_FLAGS)) {
            return Result.failure(IllegalStateException("Failed to fetch feature flag: '$name'! Feature flags are disabled."))
        }

        val result = resolveFlag(name)
        if (useResultCache && (result.isFailure || result.getOrNull()?.exists == false)) {
            val fromCache = resultCache.flag(name)
            return if (fromCache != null) {
                Result.success(fromCache)
            } else {
                result
            }
        }

        return result
    }

    /**
     * Gets the status updates
     */
    public val statusUpdates: Flow<FeatureFlagRemoteDataStatus>
        get() = remoteData.statusUpdates ?: flowOf(FeatureFlagRemoteDataStatus.OUT_OF_DATE)

    /**
     * Gets the current data status
     */
    public val status: FeatureFlagRemoteDataStatus
        get() = remoteData.status

    private suspend fun resolveFlag(name: String): Result<FeatureFlag> {
        val flagInfoResult = remoteDataFeatureFlagInfo(name)
        val remoteDataInfo = flagInfoResult.getOrNull()
        if (flagInfoResult.isFailure || remoteDataInfo == null) {
            return Result.failure(mapError(name, flagInfoResult.exceptionOrNull()))
        }

        // Attempt to evaluate
        val result = evaluate(name, remoteDataInfo)
        if (result.isSuccess) {
            return result
        }

        return when (val e = result.exceptionOrNull()) {
            // If the flag is out of date, invalidate and try again
            is FeatureFlagEvaluationException.OutOfDate -> {
                // Notify out of date
                remoteData.notifyOutOfDate(remoteDataInfo.remoteDataInfo)

                // Best effort refresh again
                remoteData.bestEffortRefresh()

                // If we are not up-to-date, then skip
                if (remoteData.status != FeatureFlagRemoteDataStatus.UP_TO_DATE) {
                    return Result.failure(mapError(name, e))
                }

                val secondAttempt = evaluate(name, remoteDataInfo)
                return secondAttempt.exceptionOrNull()?.let {
                    Result.failure(mapError(name, it))
                } ?: secondAttempt
            }
            else -> {
                Result.failure(mapError(name, e))
            }
        }
    }

    private suspend fun remoteDataFeatureFlagInfo(name: String): Result<RemoteDataFeatureFlagInfo> {
        return when (remoteData.status) {
            FeatureFlagRemoteDataStatus.UP_TO_DATE -> {
                Result.success(remoteData.fetchFlagRemoteInfo(name))
            }

            FeatureFlagRemoteDataStatus.STALE, FeatureFlagRemoteDataStatus.OUT_OF_DATE -> {
                val remoteDataInfo = remoteData.fetchFlagRemoteInfo(name)
                val disallowStale = remoteDataInfo.flagInfoList.firstOrNull {
                    it.evaluationOptions?.disallowStaleValues == true
                } != null

                return if (remoteDataInfo.flagInfoList.isEmpty() || disallowStale) {
                    remoteData.bestEffortRefresh()

                    when (remoteData.status) {
                        FeatureFlagRemoteDataStatus.UP_TO_DATE ->
                            Result.success(remoteData.fetchFlagRemoteInfo(name))
                        FeatureFlagRemoteDataStatus.STALE ->
                            Result.failure(FeatureFlagEvaluationException.StaleNotAllowed())
                        FeatureFlagRemoteDataStatus.OUT_OF_DATE ->
                            Result.failure(FeatureFlagEvaluationException.OutOfDate())
                    }
                } else {
                    Result.success(remoteDataInfo)
                }
            }
        }
    }

    private fun mapError(flagName: String, e: Throwable?): FeatureFlagException {
        return when (e) {
            is FeatureFlagEvaluationException.OutOfDate -> {
                val msg = "Failed to fetch feature flag: '$flagName'! Remote data is outdated."
                FeatureFlagException.FailedToFetch(msg).apply { initCause(e) }
            }

            is FeatureFlagEvaluationException.StaleNotAllowed -> {
                val msg = "Failed to fetch feature flag: '$flagName'! Stale data is not allowed."
                FeatureFlagException.FailedToFetch(msg).apply { initCause(e) }
            }

            is FeatureFlagEvaluationException.ConnectionError -> {
                val msg =
                    "Failed to fetch feature flag: '$flagName'! Network error" + "${e.statusCode?.let { " ($it)" }}." + "${e.errorDescription?.let { " $it" }}"
                FeatureFlagException.FailedToFetch(msg).apply { initCause(e) }
            }

            else -> {
                val msg = "Failed to fetch feature flag: '$flagName'!"
                FeatureFlagException.FailedToFetch(msg).apply {
                    if (e != null) {
                        initCause(e)
                    }
                }
            }
        }
    }

    /**
     * Tracks an interaction on a [FeatureFlag]. The [PrivacyManager.Feature.FEATURE_FLAGS]
     * must be enabled or this method will no-op.
     */
    public fun trackInteraction(flag: FeatureFlag) {
        if (!privacyManager.isEnabled(PrivacyManager.Feature.FEATURE_FLAGS)) {
            UALog.w { "Feature flags are disabled, unable to track interaction" }
            return
        }
        featureFlagAnalytics.trackInteraction(flag)
    }

    private suspend fun evaluate(name: String, remoteDataInfo: RemoteDataFeatureFlagInfo): Result<FeatureFlag> {
        val flags = remoteDataInfo.flagInfoList

        val deviceInfoProvider = infoProviderFactory()

        for ((index, info) in flags.withIndex()) {
            val isLocallyEligible = audienceEvaluator.evaluate(
                compoundAudience = CompoundAudienceSelector.combine(
                    compoundAudienceSelector = info.compoundAudienceSelector?.selector,
                    deviceAudience = info.audience
                ),
                newEvaluationDate = info.created,
                infoProvider = deviceInfoProvider)

            val isLast = index == flags.lastIndex

            if (!isLocallyEligible.isMatch && !isLast) {
                continue
            }

            val result = when (val payload = info.payload) {
                is FeatureFlagPayload.StaticPayload -> {
                    resolveStatic(
                        flagInfo = info,
                        isLocallyEligible = isLocallyEligible.isMatch,
                        staticPayload = payload,
                        deviceInfoProvider = deviceInfoProvider
                    )
                }
                is FeatureFlagPayload.DeferredPayload -> {
                    resolveDeferred(
                        flagInfo = info,
                        isLocallyEligible = isLocallyEligible.isMatch,
                        deferredPayload = payload,
                        deviceInfoProvider = deviceInfoProvider
                    )
                }
            }

            // If we have an error, flag is eligible, or the last flag return
            if (result.isFailure || result.getOrNull()?.isEligible == true || isLast) {
                return evaluatedControl(result, info, deviceInfoProvider)
            }
        }

        return Result.success(FeatureFlag.createMissingFlag(name))
    }

    private suspend fun evaluatedControl(
        flag: Result<FeatureFlag>,
        info: FeatureFlagInfo,
        deviceInfoProvider: DeviceInfoProvider
    ): Result<FeatureFlag> {

        val control = info.controlOptions ?: return flag
        val original = flag.getOrNull() ?: return flag
        if (!original.isEligible) {
            return flag
        }

        if (!audienceEvaluator.evaluate(
                compoundAudience = control.compoundAudience?.selector,
                newEvaluationDate = info.created,
                infoProvider = deviceInfoProvider)
            .isMatch) {
            return flag
        }

        val updated = when (val type = control.controlType) {
            ControlOptions.Type.Flag -> original.copyWith(isEligible = false)
            is ControlOptions.Type.Variables -> original.copyWith(variables = type.data)
        }

        updated.reportingInfo?.apply {
            addSuperseded(this.reportingMetadata)
            reportingMetadata = control.reportingMetadata
        }

        return Result.success(updated)
    }

    private suspend fun resolveStatic(
        flagInfo: FeatureFlagInfo,
        isLocallyEligible: Boolean,
        staticPayload: FeatureFlagPayload.StaticPayload,
        deviceInfoProvider: DeviceInfoProvider
    ): Result<FeatureFlag> {
        val variables = staticPayload.variables?.evaluate(
            isEligible = isLocallyEligible,
            audienceEvaluator = audienceEvaluator,
            newEvaluationDate = flagInfo.created,
            deviceInfoProvider = deviceInfoProvider
        )

        val flag = FeatureFlag.createFlag(
            name = flagInfo.name,
            isEligible = isLocallyEligible,
            variables = variables?.data,
            reportingInfo = FeatureFlag.ReportingInfo(
                reportingMetadata = variables?.reportingMetadata ?: flagInfo.reportingContext,
                channelId = deviceInfoProvider.getChannelId(),
                contactId = deviceInfoProvider.getStableContactInfo().contactId,
            )
        )

        return Result.success(flag)
    }

    private suspend fun resolveDeferred(
        flagInfo: FeatureFlagInfo,
        isLocallyEligible: Boolean,
        deferredPayload: FeatureFlagPayload.DeferredPayload,
        deviceInfoProvider: DeviceInfoProvider
    ): Result<FeatureFlag> {

        if (!isLocallyEligible) {
            return Result.success(
                FeatureFlag.createFlag(
                    name = flagInfo.name,
                    isEligible = false,
                    variables = null,
                    reportingInfo = FeatureFlag.ReportingInfo(
                        reportingMetadata = flagInfo.reportingContext,
                        channelId = deviceInfoProvider.getChannelId(),
                        contactId = deviceInfoProvider.getStableContactInfo().contactId
                    )
                )
            )
        }

        val contactId = deviceInfoProvider.getStableContactInfo().contactId
        val chanelId = deviceInfoProvider.getChannelId()
        val request = DeferredRequest(
            uri = deferredPayload.url,
            channelId = chanelId,
            contactId = contactId,
            locale = deviceInfoProvider.locale,
            notificationOptIn = deviceInfoProvider.isNotificationsOptedIn,
            appVersionName = deviceInfoProvider.appVersionName
        )

        return deferredResolver.resolve(request, flagInfo).fold(
            onSuccess = { deferredFlag ->
                val flag = when(deferredFlag) {
                    is DeferredFlag.Found -> {
                        val variables = deferredFlag.flagInfo.variables?.evaluate(
                            isEligible = deferredFlag.flagInfo.isEligible,
                            audienceEvaluator = audienceEvaluator,
                            newEvaluationDate = flagInfo.created,
                            deviceInfoProvider = deviceInfoProvider
                        )

                        FeatureFlag.createFlag(
                            name = flagInfo.name,
                            isEligible = deferredFlag.flagInfo.isEligible,
                            variables = variables?.data,
                            reportingInfo = FeatureFlag.ReportingInfo(
                                reportingMetadata = variables?.reportingMetadata ?: deferredFlag.flagInfo.reportingMetadata,
                                channelId = deviceInfoProvider.getChannelId(),
                                contactId = deviceInfoProvider.getStableContactInfo().contactId
                            )
                        )
                    }

                    is DeferredFlag.NotFound -> {
                        FeatureFlag.createMissingFlag(flagInfo.name)
                    }
                }

                Result.success(flag)
            },
            onFailure = { Result.failure(it) }
        )
    }
}

private suspend fun FeatureFlagVariables.evaluate(
    isEligible: Boolean,
    audienceEvaluator: AudienceEvaluator,
    newEvaluationDate: Long,
    deviceInfoProvider: DeviceInfoProvider
): VariableResult? {
    if (!isEligible) {
        return null
    }

    return when (this) {
        is FeatureFlagVariables.Fixed -> {
            VariableResult(this.data, null)
        }

        is FeatureFlagVariables.Variant -> {
            val match = this.variantVariables.firstOrNull {
                val audience = CompoundAudienceSelector.combine(
                    compoundAudienceSelector = it.compoundAudienceSelector?.selector,
                    deviceAudience = it.selector
                )

                audienceEvaluator.evaluate(
                    audience, newEvaluationDate, deviceInfoProvider
                ).isMatch
            }

            VariableResult(match?.data, match?.reportingMetadata)
        }
    }
}

private data class VariableResult(val data: JsonMap?, val reportingMetadata: JsonMap?)
