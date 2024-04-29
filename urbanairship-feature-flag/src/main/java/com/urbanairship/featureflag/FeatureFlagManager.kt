/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.deferred.DeferredRequest
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.Clock
import java.util.concurrent.TimeUnit
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
    private val remoteData: RemoteData,
    private val analytics: Analytics,
    private val infoProvider: DeviceInfoProvider,
    private val deferredResolver: FlagDeferredResolver,
    private val eventFeed: AirshipEventFeed,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) : AirshipComponent(context, dataStore) {

    companion object {

        private const val PAYLOAD_TYPE = "feature_flags"

        private val MAX_TIMEOUT_MILLIS: Long = TimeUnit.SECONDS.toMillis(15)

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

        val remoteDataInfo = fetchFlagRemoteInfo(name)
        val result = evaluate(remoteDataInfo)
        if (result.isSuccess) {
            return result
        }

        return when (result.exceptionOrNull()) {
            is FeatureFlagEvaluationException.OutOfDate -> {
                remoteDataInfo.remoteDataInfo?.let {
                    remoteData.notifyOutdated(it)
                }

                if (allowRefresh) {
                    waitForRemoteDataRefresh()
                    flag(name = name, allowRefresh = false)
                } else {
                    Result.failure(FeatureFlagException.FailedToFetch())
                }
            }

            is FeatureFlagEvaluationException.StaleNotAllowed -> {
                if (allowRefresh) {
                    waitForRemoteDataRefresh()
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
        if (!flag.exists) {
            UALog.e { "Flag does not exist, unable to track interaction: $flag" }
            return
        }

        if (flag.reportingInfo == null) {
            UALog.e { "Flag missing reporting info, unable to track interaction: $flag" }
            return
        }

        try {
            val event = FeatureFlagInteractionEvent(flag)

            analytics.addEvent(event)
            eventFeed.emit(AirshipEventFeed.Event.FeatureFlagInteracted(event.data))
        } catch (exception: Exception) {
            UALog.e(exception) { "Unable to track interaction: $flag" }
        }


    }

    private suspend fun waitForRemoteDataRefresh() {
        remoteData.waitForRefresh(RemoteDataSource.APP, MAX_TIMEOUT_MILLIS)
    }

    private suspend fun fetchFlagRemoteInfo(name: String): RemoteDataFeatureFlagInfo {
        val payloads = remoteData.payloads(PAYLOAD_TYPE)
            .filter { it.remoteDataInfo?.source == RemoteDataSource.APP }

        val flags = payloads
            .asSequence()
            .mapNotNull { it.data.opt(PAYLOAD_TYPE).list?.list }.flatten().map { it.optMap() }
            .mapNotNull(FeatureFlagInfo::fromJson).filter { it.name == name }
            .filter { it.timeCriteria?.meets(clock.currentTimeMillis()) ?: true }
            .toList()

        return RemoteDataFeatureFlagInfo(
            name = name,
            flagInfoList = flags,
            remoteDataInfo = payloads.firstOrNull()?.remoteDataInfo
        )
    }

    private suspend fun evaluate(remoteDataInfo: RemoteDataFeatureFlagInfo): Result<FeatureFlag> {
        remoteDataStatus(remoteData.status(RemoteDataSource.APP), remoteDataInfo).let {
            val error = it.exceptionOrNull()
            if (error != null) {
                return Result.failure(error)
            }
        }

        val name = remoteDataInfo.name
        val flags = remoteDataInfo.flagInfoList

        if (flags.isEmpty()) {
            return Result.success(
                FeatureFlag.createMissingFlag(name = name)
            )
        }

        val deviceInfoSnapshot = infoProvider.snapshot(context)

        for (info in flags) {
            val audienceCheck =
                info.audience?.evaluate(context, info.created, deviceInfoSnapshot, null) ?: true
            if (!audienceCheck) {
                continue
            }

            return when (val payload = info.payload) {
                is StaticPayload -> {
                    val variables =
                        info.payload.evaluateVariables(context, info.created, deviceInfoSnapshot)

                    Result.success(
                        FeatureFlag.createFlag(
                            name = name,
                            isEligible = true,
                            variables = variables?.data,
                            reportingInfo = FeatureFlag.ReportingInfo(
                                reportingMetadata = variables?.reportingMetadata ?: info.reportingContext,
                                channelId = deviceInfoSnapshot.channelId,
                                contactId = deviceInfoSnapshot.getStableContactId()
                            )
                        )
                    )
                }
                is DeferredPayload -> {
                    val contactId = deviceInfoSnapshot.getStableContactId()
                    val chanelId = deviceInfoSnapshot.channelId ?: return Result.failure(FeatureFlagException.FailedToFetch())
                    val locale = deviceInfoSnapshot.getUserLocale(context)
                    val request = DeferredRequest(
                        uri = payload.url,
                        channelID = chanelId,
                        contactID = contactId,
                        locale = locale,
                        notificationOptIn = deviceInfoSnapshot.isNotificationsOptedIn
                    )

                    deferredResolver.resolve(request, info)
                }
                else -> Result.failure(FeatureFlagException.FailedToFetch())
            }
        }

        return Result.success(
            FeatureFlag.createFlag(
                name = name,
                isEligible = false,
                variables = null,
                reportingInfo = FeatureFlag.ReportingInfo(
                    reportingMetadata = flags.last().reportingContext,
                    channelId = deviceInfoSnapshot.channelId,
                    contactId = deviceInfoSnapshot.getStableContactId(),
                )
            )
        )
    }
}

private data class RemoteDataFeatureFlagInfo(
    val name: String,
    val flagInfoList: List<FeatureFlagInfo>,
    val remoteDataInfo: RemoteDataInfo?
)
