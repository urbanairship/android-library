package com.urbanairship.debug.preferencecenter

import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.urbanairship.AirshipLoopers
import com.urbanairship.Logger
import com.urbanairship.UAirship
import com.urbanairship.reactive.Observable
import com.urbanairship.reactive.Schedulers
import com.urbanairship.reactive.Subscriber
import com.urbanairship.remotedata.RemoteData

class PrefCentersViewModel(
    private val remoteData: RemoteData,
    backgroundLooper: Looper
) : ViewModel() {
    private val backgroundScheduler = Schedulers.looper(backgroundLooper)

    data class PrefCenter(val id: String, val title: String?)
    private val prefCenterLiveData = MediatorLiveData<List<PrefCenter>>()

    val prefCenters: LiveData<List<PrefCenter>> = prefCenterLiveData

    internal class ViewModelFactory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PrefCentersViewModel(UAirship.shared().remoteData, AirshipLoopers.getBackgroundLooper()) as T
        }
    }

    init {
        subscribe()
    }

    private fun subscribe() {
        remoteData.payloadsForType(PAYLOAD_TYPE)
            .flatMap { payload ->
                val payloadForms = payload.data.opt(PAYLOAD_TYPE).optList()
                Logger.verbose("Found ${payloadForms.size()} preference forms in RemoteData")

                // Parse the payloads and return the list as a map of ID to PreferenceForms.
                val preferenceForms = payloadForms.mapNotNull {
                    try {
                        val form = it.optMap().opt("form").optMap()
                        val id = form.opt("id").requireString()
                        val title = form.get("display")?.map?.get("name")?.string ?: id
                        PrefCenter(id, title)
                    } catch (e: Exception) {
                        Logger.warn("Failed to parse preference center config: ${e.message}")
                        null
                    }
                }

                Observable.just(preferenceForms)
            }
            .subscribeOn(backgroundScheduler)
            .observeOn(backgroundScheduler)
            .subscribe(object : Subscriber<List<PrefCenter>>() {
                override fun onNext(list: List<PrefCenter>) =
                    prefCenterLiveData.postValue(list)

                override fun onError(e: Exception) {
                    Logger.error(e, "Failed to get preference centers!")
                    prefCenterLiveData.postValue(emptyList())
                }
            })
    }

    companion object {
        private const val PAYLOAD_TYPE = "preference_forms"
    }
}
