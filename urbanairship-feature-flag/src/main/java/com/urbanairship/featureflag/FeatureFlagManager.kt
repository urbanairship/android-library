/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.json.JsonMap
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Airship Feature Flags manager.
 */
@OpenForTesting
public class FeatureFlagManager
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val audienceEvaluator: AudienceEvaluator,
    private val remoteData: FeatureFlagRemoteDataAccess,
    private val infoProviderFactory: () -> DeviceInfoProvider = {
        DeviceInfoProvider.newCachingProvider()
    },
    private val deferredResolver: FlagDeferredResolver,
    private val featureFlagAnalytics: FeatureFlagAnalytics
) : AirshipComponent(context, dataStore) {

    companion object {

        /**
         * Gets the shared `FeatureFlagManager` instance.
         *
         * @return an instance of `FeatureFlagManager`.
         */
        @JvmStatic
        fun shared(): FeatureFlagManager =
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
     * Gets and evaluates a feature flag and returns it as a PendingResult.
     * @param name The flag name
     * @return an instance of `PendingResult<FeatureFlag>`.
     */
    fun flagAsPendingResult(name: String): PendingResult<FeatureFlag> {
        val result = PendingResult<FeatureFlag>()
        pendingResultScope.launch {
            result.result = flag(name).getOrNull()
        }
        return result
    }

    /**
     * Gets and evaluates  a feature flag
     * @param name The flag name
     * @return an instance of `Result<FeatureFlag>`.
     */
    suspend fun flag(name: String): Result<FeatureFlag> {
        return flag(name = name, allowRefresh = true)
    }

    private suspend fun flag(name: String, allowRefresh: Boolean): Result<FeatureFlag> {
        if (!isComponentEnabled) {
            return Result.failure(FeatureFlagException.FailedToFetch())
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

    fun trackInteraction(flag: FeatureFlag) {
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

        if (flags.isEmpty()) {
            return Result.success(
                FeatureFlag.createMissingFlag(name)
            )
        }

        val deviceInfo = infoProviderFactory()

        for (info in flags) {
            if (!audienceEvaluator.evaluateOptional(info.audience, info.created, deviceInfo)) {
                continue
            }

            return when (val payload = info.payload) {
                is FeatureFlagPayload.StaticPayload -> {
                    resolveStatic(info, true, payload, deviceInfo)
                }
                is FeatureFlagPayload.DeferredPayload -> {
                    resolveDeferred(info, payload, deviceInfo)
                }
            }
        }

        val last = flags.last()
        return if (last.payload is FeatureFlagPayload.StaticPayload) {
            resolveStatic(last, false, last.payload, deviceInfo)
        } else {
            Result.success(
                FeatureFlag.createFlag(
                    name = last.name,
                    isEligible = false,
                    variables = null,
                    reportingInfo = FeatureFlag.ReportingInfo(
                        reportingMetadata = last.reportingContext,
                        channelId = deviceInfo.getChannelId(),
                        contactId = deviceInfo.getStableContactId(),
                    )
                )
            )
        }
    }

    private suspend fun resolveStatic(
        flagInfo: FeatureFlagInfo,
        isEligible: Boolean,
        staticPayload: FeatureFlagPayload.StaticPayload,
        deviceInfoProvider: DeviceInfoProvider
    ): Result<FeatureFlag> {
        val variables = staticPayload.variables?.evaluate(
            audienceEvaluator,
            flagInfo.created,
            deviceInfoProvider
        )

        val flag = FeatureFlag.createFlag(
            name = flagInfo.name,
            isEligible = isEligible,
            variables = variables?.data,
            reportingInfo = FeatureFlag.ReportingInfo(
                reportingMetadata = variables?.reportingMetadata ?: flagInfo.reportingContext,
                channelId = deviceInfoProvider.getChannelId(),
                contactId = deviceInfoProvider.getStableContactId()
            )
        )

        return Result.success(flag)
    }

    private suspend fun resolveDeferred(
        flagInfo: FeatureFlagInfo,
        deferredPayload: FeatureFlagPayload.DeferredPayload,
        deviceInfoProvider: DeviceInfoProvider
    ): Result<FeatureFlag> {
        val contactId = deviceInfoProvider.getStableContactId()
        val chanelId = deviceInfoProvider.getChannelId()
        val request = DeferredRequest(
            uri = deferredPayload.url,
            channelID = chanelId,
            contactID = contactId,
            locale = deviceInfoProvider.locale,
            notificationOptIn = deviceInfoProvider.isNotificationsOptedIn,
            appVersionName = deviceInfoProvider.appVersionName
        )

        return deferredResolver.resolve(request, flagInfo).fold(
            onSuccess = { deferredFlag ->
                val flag = when(deferredFlag) {
                    is DeferredFlag.Found -> {
                        val variables = deferredFlag.flagInfo.variables?.evaluate(
                            audienceEvaluator,
                            flagInfo.created,
                            deviceInfoProvider
                        )

                        FeatureFlag.createFlag(
                            name = flagInfo.name,
                            isEligible = deferredFlag.flagInfo.isEligible,
                            variables = variables?.data,
                            reportingInfo = FeatureFlag.ReportingInfo(
                                reportingMetadata = variables?.reportingMetadata ?: deferredFlag.flagInfo.reportingMetadata,
                                channelId = deviceInfoProvider.getChannelId(),
                                contactId = deviceInfoProvider.getStableContactId()
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
    audienceEvaluator: AudienceEvaluator, newEvaluationDate: Long, infoProvider: DeviceInfoProvider
): VariableResult {
    return when (this) {
        is FeatureFlagVariables.Fixed -> {
            VariableResult(this.data, null)
        }

        is FeatureFlagVariables.Variant -> {
            val match = this.variantVariables.firstOrNull {
                audienceEvaluator.evaluateOptional(
                    it.selector, newEvaluationDate, infoProvider
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
