package com.urbanairship.debug.ui.preferencecenter

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.urbanairship.UALog
import com.urbanairship.debug.DebugManager
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.compose.ui.PreferenceCenterActivity
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal interface PreferenceCenterViewModel {

    fun openPreferenceCenter(context: Context, id: String)
    val preferenceCenters: Flow<List<PrefCenter>>

    companion object {
        internal fun forPreview(): PreferenceCenterViewModel {
            return object : PreferenceCenterViewModel {
                override val preferenceCenters: Flow<List<PrefCenter>> = MutableStateFlow(
                    listOf(
                        PrefCenter("one", "Preference Center"),
                        PrefCenter("one", "Another Preference Center")
                    )
                )

                override fun openPreferenceCenter(context: Context, id: String) {
                }
            }
        }
    }
}

internal class DefaultPreferenceCenterViewModel(
    remoteData: RemoteData = DebugManager.shared().remoteData
): PreferenceCenterViewModel, ViewModel() {

    override fun openPreferenceCenter(context: Context, id: String) {
        val intent = Intent(context, PreferenceCenterActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(PreferenceCenter.EXTRA_PREFERENCE_CENTER_ID, id)

        context.startActivity(intent)
    }

    override val preferenceCenters: Flow<List<PrefCenter>> = remoteData.payloadFlow(PAYLOAD_TYPE)
        .map { payloads ->
            val preferenceCenters  = payloads.map { payload ->
                val payloadForms = payload.data.opt(PAYLOAD_TYPE).optList()
                UALog.v("Found ${payloadForms.size()} preference forms in RemoteData")

                // Parse the payloads and return the list as a map of ID to PreferenceForms.
                payloadForms.mapNotNull {
                    try {
                        val form = it.optMap().opt("form").optMap()
                        val id = form.opt("id").requireString()
                        val title =
                            form["display"]?.map?.get("name")?.string?.ifEmpty { null } ?: id
                        PrefCenter(id, title)
                    } catch (e: Exception) {
                        UALog.w("Failed to parse preference center config: ${e.message}")
                        null
                    }
                }
            }.flatten()

            // Need to deduplicate and sort by ID
            preferenceCenters
                .groupBy { it.id }
                .entries.map { it.value.first() }
                .sortedBy { it.id }
        }

    private companion object {

        private const val PAYLOAD_TYPE = "preference_forms"
    }

}

internal data class PrefCenter(val id: String, val title: String)
