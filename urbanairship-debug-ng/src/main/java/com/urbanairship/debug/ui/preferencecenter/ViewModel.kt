package com.urbanairship.debug.ui.preferencecenter

import android.content.Context
import android.content.Intent
import com.urbanairship.UALog
import com.urbanairship.debug.DebugManager
import com.urbanairship.preferencecenter.ui.PreferenceCenterActivity
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface ViewModel {

    fun openPreferenceCenter(context: Context, id: String)

    val preferenceCenters: Flow<List<PrefCenter>>
}

internal class DefaultViewModel(
    private val remoteData: RemoteData = DebugManager.shared().remoteData
): ViewModel {

    override fun openPreferenceCenter(context: Context, id: String) {
        val intent = Intent(context, PreferenceCenterActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(PreferenceCenterActivity.EXTRA_ID, id)

        context.startActivity(intent)
    }

    override val preferenceCenters: Flow<List<PrefCenter>> = remoteData.payloadFlow(PAYLOAD_TYPE)
        .map { payloads ->
            payloads.map { payload ->
                val payloadForms = payload.data.opt(PAYLOAD_TYPE).optList()
                UALog.v("Found ${payloadForms.size()} preference forms in RemoteData")

                // Parse the payloads and return the list as a map of ID to PreferenceForms.
                payloadForms.mapNotNull {
                    try {
                        val form = it.optMap().opt("form").optMap()
                        val id = form.opt("id").requireString()
                        val title = form.get("display")?.map?.get("name")?.string?.ifEmpty { null } ?: id
                        PrefCenter(id, title)
                    } catch (e: Exception) {
                        UALog.w("Failed to parse preference center config: ${e.message}")
                        null
                    }
                }
            }.flatten().sortedBy { it.id }
        }

    private companion object {
        private const val PAYLOAD_TYPE = "preference_forms"
    }
}

internal data class PrefCenter(val id: String, val title: String)



