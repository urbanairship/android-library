package com.urbanairship.debug.preferencecenter

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

class PrefCentersViewModel(
    private val remoteData: RemoteData,
) : ViewModel() {

    data class PrefCenter(val id: String, val title: String?)
    private val prefCenterLiveData = remoteData.payloadFlow(PAYLOAD_TYPE)
        .map { payloads ->
            payloads.mapNotNull { payload ->
                val payloadForms = payload.data.opt(PAYLOAD_TYPE).optList()
                UALog.v("Found ${payloadForms.size()} preference forms in RemoteData")

                // Parse the payloads and return the list as a map of ID to PreferenceForms.
                payloadForms.mapNotNull {
                    try {
                        val form = it.optMap().opt("form").optMap()
                        val id = form.opt("id").requireString()
                        val title = form.get("display")?.map?.get("name")?.string ?: id
                        PrefCenter(id, title)
                    } catch (e: Exception) {
                        UALog.w("Failed to parse preference center config: ${e.message}")
                        null
                    }
                }
            }.flatten()
            .sortedBy { it.id }
        }.asLiveData(Dispatchers.Main)

    val prefCenters: LiveData<List<PrefCenter>> = prefCenterLiveData

    internal class ViewModelFactory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PrefCentersViewModel(UAirship.shared().remoteData) as T
        }
    }

    companion object {
        private const val PAYLOAD_TYPE = "preference_forms"
    }
}
