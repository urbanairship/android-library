package com.urbanairship.debug.featureflags

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.json.JsonMap
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

class FeatureFlagsViewModel(
    remoteData: RemoteData
) : ViewModel() {
    private val featureFlagsLiveData = remoteData.payloadFlow(PAYLOAD_TYPE)
        .map { payloads ->
            payloads.mapNotNull { payload ->
                val payloadFlags = payload.data.opt(PAYLOAD_TYPE).list?.list
                UALog.v { "Found ${payloadFlags?.size ?: 0} feature flags in RemoteData" }

                payloadFlags?.mapNotNull { it.optMap() }
            }.flatten()
        }.asLiveData(Dispatchers.Main)

    public val featureFlags: LiveData<List<JsonMap>> = featureFlagsLiveData

    internal class ViewModelFactory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return FeatureFlagsViewModel(UAirship.shared().remoteData) as T
        }
    }

    companion object {
        private const val PAYLOAD_TYPE = "feature_flags"
    }
}
