/* Copyright Airship and Contributors */

package com.urbanairship.experiment

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.Logger
import com.urbanairship.PreferenceDataStore
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.requireField
import com.urbanairship.reactive.Observable
import com.urbanairship.reactive.Subscriber
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataPayload
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Airship Experiment Manager.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExperimentManager internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val remoteData: RemoteData
) : AirshipComponent(context, dataStore) {

    public companion object {
        internal const val PAYLOAD_TYPE = "experiments"
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getComponentGroup(): Int = AirshipComponentGroups.EXPERIMENT

    /**
     * Returns an optional Experiment with the given [id].
     *
     * @param id The ID of the Experiment.
     */
    internal suspend fun getExperimentWithId(id: String): Experiment? {
        return getExperimentWith {
            it.requireField<String>(Experiment.KEY_ID) == id
        }
    }

    private suspend fun getExperimentWith(predicate: (JsonMap) -> Boolean): Experiment? {
        try {
            return remoteData
                .singlePayloadForType(PAYLOAD_TYPE)
                .data
                .requireField<JsonList>(PAYLOAD_TYPE)
                .map { it.optMap() }
                .find(predicate)
                ?.let(Experiment::fromJson)
        } catch (ex: JsonException) {
            Logger.e(ex, { "Failed to parse experiments from remoteData payload" })
            return null
        }
    }
}

internal suspend fun RemoteData.singlePayloadForType(type: String): RemoteDataPayload {
    return suspendCancellableCoroutine { job ->
        val subscription = this.payloadsForType(type)
            .flatMap { Observable.just(it) }
            .subscribe(object : Subscriber<RemoteDataPayload>() {
                override fun onNext(value: RemoteDataPayload) = job.resume(value)
                override fun onError(e: Exception) = job.resumeWithException(e)
            })

        job.invokeOnCancellation {
            subscription.cancel()
        }
    }
}
