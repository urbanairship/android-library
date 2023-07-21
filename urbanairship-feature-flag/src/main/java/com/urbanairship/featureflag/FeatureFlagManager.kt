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
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.json.JsonMap
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.Clock
import com.urbanairship.util.Network
import kotlin.jvm.Throws
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Airship Feature flag.
 */
public class FeatureFlag(

    /**
     * Indicates whether the device is eligible or not for the flag.
     */
    val isEligible: Boolean,

    /**
     * Indicates whether the flag exists in the current flag listing or not
     */
    val exists: Boolean,

    /**
     * Optional variables associated with the flag
     */
    val variables: JsonMap?
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FeatureFlag

        if (isEligible != other.isEligible) return false
        if (exists != other.exists) return false
        if (variables != other.variables) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isEligible.hashCode()
        result = 31 * result + exists.hashCode()
        result = 31 * result + (variables?.hashCode() ?: 0)
        return result
    }
}

/**
 * Airship Feature Flags manager.
 */
@OpenForTesting
public class FeatureFlagManager
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val remoteData: RemoteData,
    private val infoProvider: DeviceInfoProvider,
    private val network: Network = Network.shared(),
    private val clock: Clock = Clock.DEFAULT_CLOCK
) : AirshipComponent(context, dataStore) {

    companion object {
        private const val PAYLOAD_TYPE = "feature_flags"

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
        return when (refreshIfNeeded()) {
            RemoteData.Status.UP_TO_DATE -> Result.success(evaluate(fetchFlagInfos(name)))
            RemoteData.Status.STALE -> {
                val items = fetchFlagInfos(name)
                if (items.isEmpty() || !isStaleAllowed(items)) {
                    Result.failure(FeatureFlagException("Unable to fetch data"))
                } else {
                    Result.success(evaluate(items))
                }
            }
            RemoteData.Status.OUT_OF_DATE ->
                Result.failure(FeatureFlagException("Unable to fetch data"))
        }
    }

    private suspend fun refreshIfNeeded(): RemoteData.Status {
        val status = remoteData.status(RemoteDataSource.APP)
        val hasNetwork = network.isConnected(context)

        if (status == RemoteData.Status.UP_TO_DATE || !hasNetwork) {
            return status
        }

        remoteData.refresh(RemoteDataSource.APP)
        return remoteData.status(RemoteDataSource.APP)
    }

    private fun isStaleAllowed(flags: List<FeatureFlagInfo>): Boolean {
        val explicitDisallow = flags.firstOrNull { it.evaluationOptions?.disallowStaleValues ?: false }
        return explicitDisallow == null
    }

    private suspend fun fetchFlagInfos(name: String): List<FeatureFlagInfo> {
        return remoteData
            .payloads(PAYLOAD_TYPE)
            .asSequence()
            .mapNotNull { it.data.opt(PAYLOAD_TYPE).list?.list }
            .flatten()
            .map { it.optMap() }
            .mapNotNull(FeatureFlagInfo::fromJson)
            .filter { it.name == name }
            .filter { it.timeCriteria?.meets(clock.currentTimeMillis()) ?: true }
            .toList()
    }

    private suspend fun evaluate(flags: List<FeatureFlagInfo>): FeatureFlag {
        if (flags.isEmpty()) {
            return FeatureFlag(isEligible = false, exists = false, variables = null)
        }

        val deviceInfoSnapshot = infoProvider.snapshot(context)

        var variables: VariablesVariant? = null

        for (info in flags) {
            val audienceCheck = info.audience?.evaluate(context, info.created, deviceInfoSnapshot, null)
                ?: true
            if (!audienceCheck) {
                continue
            }

            variables = info.payload.evaluateVariables(context, info.created, deviceInfoSnapshot)
            if (variables != null) {
                break
            }
        }

        return FeatureFlag(
            isEligible = variables != null,
            exists = true,
            variables = variables?.data
        )
    }
}
