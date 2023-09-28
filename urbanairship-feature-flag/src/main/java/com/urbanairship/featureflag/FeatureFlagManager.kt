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
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.remotedata.RemoteData
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
    @Throws(FeatureFlagException::class)
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
        return when (remoteData.status(RemoteDataSource.APP)) {
            RemoteData.Status.UP_TO_DATE -> {
                Result.success(evaluate(name, fetchFlagInfos(name)))
            }
            RemoteData.Status.STALE -> {
                val items = fetchFlagInfos(name)
                if (items.isEmpty() || !isStaleAllowed(items)) {
                    if (allowRefresh) {
                        waitForRemoteDataRefresh()
                        flag(name = name, allowRefresh = false)
                    } else {
                        Result.failure(FeatureFlagException("Unable to fetch data"))
                    }
                } else {
                    Result.success(evaluate(name, items))
                }
            }
            RemoteData.Status.OUT_OF_DATE -> {
                if (allowRefresh) {
                    waitForRemoteDataRefresh()
                    flag(name = name, allowRefresh = false)
                } else {
                    Result.failure(FeatureFlagException("Unable to fetch data"))
                }
            }
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
            analytics.addEvent(FeatureFlagInteractionEvent(flag))
        } catch (exception: Exception) {
            UALog.e(exception) { "Unable to track interaction: $flag" }
        }
    }

    private suspend fun waitForRemoteDataRefresh() {
        remoteData.waitForRefresh(RemoteDataSource.APP, MAX_TIMEOUT_MILLIS)
    }

    private fun isStaleAllowed(flags: List<FeatureFlagInfo>): Boolean {
        val explicitDisallow =
            flags.firstOrNull { it.evaluationOptions?.disallowStaleValues ?: false }
        return explicitDisallow == null
    }

    private suspend fun fetchFlagInfos(name: String): List<FeatureFlagInfo> {
        return remoteData.payloads(PAYLOAD_TYPE).asSequence()
            .mapNotNull { it.data.opt(PAYLOAD_TYPE).list?.list }.flatten().map { it.optMap() }
            .mapNotNull(FeatureFlagInfo::fromJson).filter { it.name == name }
            .filter { it.timeCriteria?.meets(clock.currentTimeMillis()) ?: true }.toList()
    }

    private suspend fun evaluate(name: String, flags: List<FeatureFlagInfo>): FeatureFlag {
        if (flags.isEmpty()) {
            return FeatureFlag.createMissingFlag(
                name = name
            )
        }

        val deviceInfoSnapshot = infoProvider.snapshot(context)

        for (info in flags) {
            val audienceCheck =
                info.audience?.evaluate(context, info.created, deviceInfoSnapshot, null) ?: true
            if (!audienceCheck) {
                continue
            }

            val variables =
                info.payload.evaluateVariables(context, info.created, deviceInfoSnapshot)

            return FeatureFlag.createFlag(
                name = name,
                isEligible = true,
                variables = variables?.data,
                reportingInfo = FeatureFlag.ReportingInfo(
                    reportingMetadata = variables?.reportingMetadata ?: info.reportingContext,
                    channelId = deviceInfoSnapshot.channelId,
                    contactId = deviceInfoSnapshot.getStableContactId()
                )
            )
        }

        return FeatureFlag.createFlag(
            name = name,
            isEligible = false,
            variables = null,
            reportingInfo = FeatureFlag.ReportingInfo(
                reportingMetadata = flags.last().reportingContext,
                channelId = deviceInfoSnapshot.channelId,
                contactId = deviceInfoSnapshot.getStableContactId(),
            )
        )
    }
}
