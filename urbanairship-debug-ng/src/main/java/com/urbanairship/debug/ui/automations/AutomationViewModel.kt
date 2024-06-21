package com.urbanairship.debug.ui.automations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.urbanairship.UALog
import com.urbanairship.debug.DebugManager
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal interface AutomationViewModel {

    val automations: Flow<List<Automation>>

    companion object {
        internal fun forPreview(): AutomationViewModel {
            return object : AutomationViewModel {
                override val automations: Flow<List<Automation>> = MutableStateFlow(
                    listOf(
                        Automation("one", "Automation 1", jsonMapOf("foo" to "bar")),
                        Automation("one", "Automation 2", jsonMapOf("foo" to "bar"))
                    )
                )
            }
        }
    }
}

internal class DefaultAutomationViewModel(
    remoteData: RemoteData = DebugManager.shared().remoteData,
): AutomationViewModel, ViewModel() {

    override val automations: Flow<List<Automation>> = remoteData.payloadFlow(PAYLOAD_TYPE)
        .map { payloads ->
            payloads.map { payload ->
                val payloadForms = payload.data.opt(PAYLOAD_TYPE).optList()

                // Parse the payloads and return the list as a map of ID to PreferenceForms.
                payloadForms.mapNotNull {
                    try {
                        val name = it.optMap().opt("message").optMap().optionalField<String>("name")
                        val id = it.optMap().opt("id").requireString()
                        val type = it.optMap().optionalField<String>("type") ?: "unknown"
                        Automation(id, name ?: id, it)
                    } catch (e: Exception) {
                        UALog.w("Failed to parse preference center config: ${e.message}")
                        null
                    }
                }
            }.flatten().sortedBy { it.id }
        }

    private companion object {

        private const val PAYLOAD_TYPE = "in_app_messages"
    }

}

internal data class Automation(
    val id: String,
    val name: String,
    val body: JsonSerializable
)
