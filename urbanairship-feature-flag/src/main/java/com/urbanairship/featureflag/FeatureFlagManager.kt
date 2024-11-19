/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.json.JsonMap
import com.urbanairship.remotedata.RemoteData
import java.lang.IllegalStateException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Airship Feature Flags manager.
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
    private val privacyManager: PrivacyManager
) : AirshipComponent(context, dataStore) {

    public companion object {

        /**
         * Gets the shared `FeatureFlagManager` instance.
         *
         * @return an instance of `FeatureFlagManager`.
         */
        @JvmStatic
        public fun shared(): FeatureFlagManager =
            UAirship.shared().requireComponent(FeatureFlagManager::class.java)
    }

    private val pendingResultScope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getComponentGroup(): Int = AirshipComponentGroups.FEATURE_FLAGS

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public override fun init() {
        super.init()
    }

    /**
     * Gets and evaluates a feature flag and returns it as a PendingResult. The [PrivacyManager.Feature.FEATURE_FLAGS]
     * must be enabled or this method will return null.
     *
     * @param name The flag name
     * @return an instance of `PendingResult<FeatureFlag>`.
     */
    public fun flagAsPendingResult(name: String): PendingResult<FeatureFlag> {
        val result = PendingResult<FeatureFlag>()
        pendingResultScope.launch {
            result.result = flag(name).getOrNull()
        }
        return result
    }

    /**
     * Gets and evaluates a feature flag. The [PrivacyManager.Feature.FEATURE_FLAGS]
     * must be enabled or this method will return an error.
     * @param name The flag name
     * @return an instance of `Result<FeatureFlag>`.
     */
    public suspend fun flag(name: String): Result<FeatureFlag> {
        return flag(name = name, allowRefresh = true)
    }

    private suspend fun flag(name: String, allowRefresh: Boolean): Result<FeatureFlag> {
        if (!privacyManager.isEnabled(PrivacyManager.Feature.FEATURE_FLAGS)) {
            return Result.failure(IllegalStateException("Feature flags are disabled"))
        }

        val remoteDataInfo = remoteData.fetchFlagRemoteInfo(name)
        val result = evaluate(name, remoteDataInfo)
        if (result.isSuccess) {
            return result
        }

        return when (result.exceptionOrNull()) {
            is FeatureFlagEvaluationException.OutOfDate -> {
                remoteData.notifyOutOfDate(remoteDataInfo.remoteDataInfo)

                if (allowRefresh) {
                    remoteData.waitForRemoteDataRefresh()
                    flag(name = name, allowRefresh = false)
                } else {
                    Result.failure(FeatureFlagException.FailedToFetch())
                }
            }

            is FeatureFlagEvaluationException.StaleNotAllowed -> {
                if (allowRefresh) {
                    remoteData.waitForRemoteDataRefresh()
                    flag(name = name, allowRefresh = false)
                } else {
                    Result.failure(FeatureFlagException.FailedToFetch())
                }
            }

            else -> Result.failure(FeatureFlagException.FailedToFetch())
        }
    }

    private fun remoteDataStatus(status: RemoteData.Status, remoteData: RemoteDataFeatureFlagInfo): Result<Unit> {
        return when (status) {
            RemoteData.Status.STALE -> {
                if (remoteData.flagInfoList.isEmpty()) {
                    return Result.failure(FeatureFlagEvaluationException.OutOfDate())
                }

                val disallowStale = remoteData
                    .flagInfoList
                    .firstOrNull { it.evaluationOptions?.disallowStaleValues == true } != null

                if (disallowStale) {
                    Result.failure(FeatureFlagEvaluationException.StaleNotAllowed())
                } else {
                    Result.success(Unit)
                }
            }
            RemoteData.Status.OUT_OF_DATE -> Result.failure(FeatureFlagEvaluationException.OutOfDate())
            RemoteData.Status.UP_TO_DATE -> Result.success(Unit)
            else -> Result.success(Unit)
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
        remoteDataStatus(remoteData.status, remoteDataInfo).let {
            val error = it.exceptionOrNull()
            if (error != null) {
                return Result.failure(error)
            }
        }

        val flags = remoteDataInfo.flagInfoList

        val deviceInfoProvider = infoProviderFactory()

        for ((index, info) in flags.withIndex()) {
            val isLocallyEligible = audienceEvaluator.evaluateOptional(info.audience, info.created, deviceInfoProvider)
            val isLast = index == flags.lastIndex

            if (!isLocallyEligible && !isLast) {
                continue
            }

            val result = when (val payload = info.payload) {
                is FeatureFlagPayload.StaticPayload -> {
                    resolveStatic(
                        flagInfo = info,
                        isLocallyEligible = isLocallyEligible,
                        staticPayload = payload,
                        deviceInfoProvider = deviceInfoProvider
                    )
                }
                is FeatureFlagPayload.DeferredPayload -> {
                    resolveDeferred(
                        flagInfo = info,
                        isLocallyEligible = isLocallyEligible,
                        deferredPayload = payload,
                        deviceInfoProvider = deviceInfoProvider
                    )
                }
            }

            /// If we have an error, flag is eligible, or the last flag return
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

        if (!audienceEvaluator.evaluateOptional(control.audience, info.created, deviceInfoProvider)) {
            return flag
        }

        val updated = when (val type = control.controlType) {
            ControlOptions.Type.Flag -> original.copyWith(isEligible = false)
            is ControlOptions.Type.Variables -> original.copyWith(variables = type.variables)
        }

        updated.reportingInfo?.let { info ->
            info.addSuperseded(info.reportingMetadata)
            info.reportingMetadata = control.reportingMetadata
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
                audienceEvaluator.evaluateOptional(
                    it.selector, newEvaluationDate, deviceInfoProvider
                )
            }

            VariableResult(match?.data, match?.reportingMetadata)
        }
    }
}

private suspend fun AudienceEvaluator.evaluateOptional(
    audienceSelector: AudienceSelector?,
    newEvaluationDate: Long,
    infoProvider: DeviceInfoProvider
): Boolean {
    return audienceSelector?.let { this.evaluate(audienceSelector, newEvaluationDate, infoProvider) } ?: true
}

private data class VariableResult(val data: JsonMap?, val reportingMetadata: JsonMap?)
