package com.urbanairship.debug.ui.automations

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
    val experiments: Flow<List<Automation>>

    companion object {
        internal fun forPreview(): AutomationViewModel {
            return object : AutomationViewModel {
                override val automations: Flow<List<Automation>> = MutableStateFlow(
                    listOf(
                        Automation("one", "Automation 1", jsonMapOf("foo" to "bar")),
                        Automation("one", "Automation 2", jsonMapOf("foo" to "bar"))
                    )
                )

                override val experiments: Flow<List<Automation>> = MutableStateFlow(
                    listOf(
                        Automation("exp-1", "Experiment 1", jsonMapOf("foo" to "bar")),
                        Automation("exp-2", "Experiment 2", jsonMapOf("foo" to "bar"))
                    )
                )
            }
        }
    }
}

internal class DefaultAutomationViewModel(
    remoteData: RemoteData = DebugManager.shared().remoteData,
): AutomationViewModel, ViewModel() {

    override val automations: Flow<List<Automation>> =
        remoteData.payloadFlow(PAYLOAD_TYPE).map { payloads ->
                payloads.map { payload ->
                    val payloadForms = payload.data.opt(PAYLOAD_TYPE).optList()

                    payloadForms.mapNotNull {
                        try {
                            val name =
                                it.optMap().opt("message").optMap().optionalField<String>("name")
                            val id = it.optMap().opt("id").requireString()
                            val type = it.optMap().optionalField<String>("type") ?: "unknown"
                            Automation(id, name ?: id, it)
                        } catch (e: Exception) {
                            UALog.w("Failed to parse automations: ${e.message}")
                            null
                        }
                    }
                }.flatten().sortedBy { it.id }
            }

    override val experiments: Flow<List<Automation>> =
        remoteData.payloadFlow(EXPERIMENTS_PAYLOAD_TYPE).map { payloads ->
                payloads.map { payload ->
                    val payloadForms = payload.data.opt(EXPERIMENTS_PAYLOAD_TYPE).optList()

                    payloadForms.mapNotNull {
                        try {
                            val id = it.optMap().opt("experiment_id").requireString()
                            Automation(id, id, it)
                        } catch (e: Exception) {
                            UALog.w("Failed to parse experiments: ${e.message}")
                            null
                        }
                    }
                }.flatten().sortedBy { it.id }
            }


    private companion object {
        private const val PAYLOAD_TYPE = "in_app_messages"
        private const val EXPERIMENTS_PAYLOAD_TYPE = "experiments"
    }

}

internal data class Automation(
    val id: String,
    val name: String,
    val body: JsonSerializable
)

internal enum class DisplaySource(val route: String) {
    AUTOMATIONS("automations"), EXPERIMENTS("experiments");

    companion object {
        fun fromRoute(route: String): DisplaySource {
            return entries.first { it.route == route }
        }
    }
}
