/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Airship
import com.urbanairship.ai.EvaluationContext
import com.urbanairship.ai.EvaluationResult
import com.urbanairship.ai.Usage
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.iam.ai.InAppMessageSuppressionEvaluation
import com.urbanairship.iam.ai.InAppMessageSuppressionSubject
import com.urbanairship.iam.ai.inAppMessageSuppression
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import kotlinx.coroutines.launch

/** Persisted inputs for the IAA suppression sandbox. */
internal data class IAASuppressionState(
    val condition: String = "",
    val messageName: String = "Test Message",
    val extrasJSON: String = "",
    val hintsJSON: String = "",
    val contextMode: AirshipDebugContextMode = AirshipDebugContextMode.ProviderOnly,
    val contextItems: List<AirshipDebugContextItem> = emptyList()
)

private fun IAASuppressionState.toJson(): JsonValue = jsonMapOf(
    "condition" to condition,
    "message_name" to messageName,
    "extras_json" to extrasJSON,
    "hints_json" to hintsJSON,
    "context_mode" to contextMode.toJson(),
    "context_items" to contextItems.toJsonList()
).toJsonValue()

private fun iaaSuppressionStateFromJson(json: JsonValue): IAASuppressionState? = try {
    val map = json.requireMap()
    IAASuppressionState(
        condition = map.optionalField<String>("condition") ?: "",
        messageName = map.optionalField<String>("message_name") ?: "Test Message",
        extrasJSON = map.optionalField<String>("extras_json") ?: "",
        hintsJSON = map.optionalField<String>("hints_json") ?: "",
        contextMode = contextModeFromJson(map.get("context_mode")),
        contextItems = contextItemsFromJson(map.get("context_items"))
    )
} catch (e: JsonException) {
    null
}

internal sealed class SuppressionResult {
    data class Completed(val allow: Boolean, val reason: String) : SuppressionResult()
    data class Skipped(val reason: String) : SuppressionResult()
    data class Failed(val message: String) : SuppressionResult()

    val summary: String
        get() = when (this) {
            is Completed -> "${if (allow) "Allowed" else "Blocked"}: $reason"
            is Skipped -> "Skipped: $reason"
            is Failed -> "Failed: $message"
        }
}

@Composable
internal fun IaaSuppressionScreen(
    viewModel: IaaSuppressionViewModel = viewModel<IaaSuppressionViewModel>(),
    onNavigateUp: () -> Unit = {}
) {
    LaunchedEffect(Unit) { viewModel.onAppear() }
    DisposableEffect(Unit) { onDispose { viewModel.onDisappear() } }

    // Autosave: persist whenever any input changes.
    val currentState = viewModel.currentState()
    LaunchedEffect(currentState) { viewModel.store.saveLastSession(currentState) }

    // Keep the resolved-context preview live: refetch whenever the context mode or items change,
    // not only when Evaluate is pressed.
    LaunchedEffect(viewModel.contextMode, viewModel.contextItems) { viewModel.fetchContext() }

    DebugScreen(title = "IAX AI Suppression", navigation = TopBarNavigation.Back(onNavigateUp)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                AirshipDebugAITestDataSection(
                    store = viewModel.store,
                    currentState = viewModel::currentState,
                    onLoad = viewModel::apply
                )
            }

            item {
                Section(title = "Condition") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TextField(
                            value = viewModel.condition,
                            onValueChange = { viewModel.condition = it },
                            placeholder = { Text("e.g. Only show this if the user travels frequently for work") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Injected into the SDK's fixed instruction template.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            item {
                Section(title = "Test Message Details") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TextField(
                            value = viewModel.messageName,
                            onValueChange = { viewModel.messageName = it },
                            label = { Text("Message name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        JsonFieldEditor(
                            label = "Extras",
                            placeholder = "{\"key\": \"value\"}",
                            value = viewModel.extrasJSON,
                            onValueChange = { viewModel.extrasJSON = it }
                        )
                        JsonFieldEditor(
                            label = "Subject Hints",
                            placeholder = "{\"hint_key\": \"hint_value\"}",
                            value = viewModel.hintsJSON,
                            onValueChange = { viewModel.hintsJSON = it },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            item {
                AirshipDebugAIContextSection(
                    mode = viewModel.contextMode,
                    onModeChange = { viewModel.contextMode = it },
                    items = viewModel.contextItems,
                    onItemsChange = { viewModel.contextItems = it },
                    resolvedItems = viewModel.context?.items,
                    isFetching = viewModel.isFetchingContext
                )
            }

            item {
                Section(title = "Assembled Prompt") {
                    AirshipDebugAIPromptPreview(
                        instructions = viewModel.assembledInstructions,
                        prompt = viewModel.assembledPrompt
                    )
                }
            }

            item {
                EvaluateButton(
                    label = "Evaluate",
                    enabled = !viewModel.isRunning && viewModel.messageName.isNotEmpty(),
                    onClick = viewModel::runEvaluation
                )
            }

            item {
                Section(title = "Result") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (viewModel.isRunning) {
                            RunningRow("Evaluating…")
                        } else {
                            when (val result = viewModel.result) {
                                is SuppressionResult.Completed -> Column {
                                    Text(
                                        text = if (result.allow) "Allowed" else "Blocked",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (result.allow) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = result.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                is SuppressionResult.Skipped ->
                                    Text("Skipped: ${result.reason}", color = Color(0xFFE65100))

                                is SuppressionResult.Failed ->
                                    Text(result.message, color = MaterialTheme.colorScheme.error)

                                null -> Text("Not evaluated yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                AirshipDebugAIHistorySection(
                    store = viewModel.store,
                    onRestore = viewModel::apply
                )
            }
        }
    }
}

@Composable
internal fun RunningRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun JsonFieldEditor(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.padding(top = 8.dp)
) {
    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isNotBlank() && runCatching { JsonValue.parseString(value) }.isFailure) {
            Text(
                text = "Invalid JSON",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

internal class IaaSuppressionViewModel : ViewModel() {
    var messageName: String by mutableStateOf("Test Message")
    var condition: String by mutableStateOf("")
    var extrasJSON: String by mutableStateOf("")
    var hintsJSON: String by mutableStateOf("")
    var contextMode: AirshipDebugContextMode by mutableStateOf(AirshipDebugContextMode.ProviderOnly)
    var contextItems: List<AirshipDebugContextItem> by mutableStateOf(emptyList())
    var context: EvaluationContext? by mutableStateOf(null)
        private set
    var isFetchingContext: Boolean by mutableStateOf(false)
        private set
    var isRunning: Boolean by mutableStateOf(false)
        private set
    var result: SuppressionResult? by mutableStateOf(null)
        private set

    private val usage = Usage.inAppMessageSuppression

    val store: AirshipDebugAIStore<IAASuppressionState> = AirshipDebugAIStore(
        usageKey = usage.rawValue,
        bundledFixtures = listOf(
            AirshipDebugAIPreset(
                name = "Frequent traveler",
                state = IAASuppressionState(
                    condition = "Only show if the user travels frequently for work",
                    messageName = "Trip Upgrade Offer"
                )
            ),
            AirshipDebugAIPreset(
                name = "Cat owner (custom context)",
                state = IAASuppressionState(
                    condition = "Only show if the user is likely to own a cat",
                    messageName = "Cat Toy Promo",
                    contextMode = AirshipDebugContextMode.Override,
                    contextItems = listOf(
                        AirshipDebugContextItem(content = "User interests: cats"),
                        AirshipDebugContextItem(content = "Recent purchase: cat litter")
                    )
                )
            ),
            AirshipDebugAIPreset(
                name = "Insufficient context (fails open)",
                state = IAASuppressionState(
                    condition = "Only show to users who prefer email over SMS",
                    messageName = "Channel Preference",
                    contextMode = AirshipDebugContextMode.Override,
                    contextItems = listOf(AirshipDebugContextItem(content = "Signup date: 2024-01-01"))
                )
            )
        ),
        toJson = { it.toJson() },
        fromJson = ::iaaSuppressionStateFromJson
    )

    fun currentState(): IAASuppressionState = IAASuppressionState(
        condition = condition,
        messageName = messageName,
        extrasJSON = extrasJSON,
        hintsJSON = hintsJSON,
        contextMode = contextMode,
        contextItems = contextItems
    )

    fun apply(state: IAASuppressionState) {
        condition = state.condition
        messageName = state.messageName
        extrasJSON = state.extrasJSON
        hintsJSON = state.hintsJSON
        contextMode = state.contextMode
        contextItems = state.contextItems
    }

    fun onAppear() {
        store.loadLastSession()?.let(::apply)
    }

    fun onDisappear() {
        // Airship.internalAi blocks the calling thread until the SDK is ready; run it on a
        // coroutine rather than directly in DisposableEffect's onDispose, which can't suspend.
        viewModelScope.launch {
            Airship.internalAi.setContextProvider(usage, null)
        }
    }

    suspend fun fetchContext() {
        isFetchingContext = true
        syncContextProvider()
        val provider = Airship.internalAi.fetchContext(usage, makeSubject())
        context = when (contextMode) {
            AirshipDebugContextMode.ProviderOnly -> provider
            AirshipDebugContextMode.Append -> provider.appending(contextItems.toEvaluationContext())
            AirshipDebugContextMode.Override -> contextItems.toEvaluationContext()
        }
        isFetchingContext = false
    }

    fun runEvaluation() {
        viewModelScope.launch {
            isRunning = true
            result = null

            fetchContext()

            val evaluation = InAppMessageSuppressionEvaluation(condition, makeSubject())
            val additional = if (contextMode == AirshipDebugContextMode.Append) {
                contextItems.toEvaluationContext()
            } else {
                EvaluationContext.EMPTY
            }

            val outcome = when (val evaluated = Airship.internalAi.evaluate(evaluation, additional)) {
                is EvaluationResult.Completed -> SuppressionResult.Completed(evaluated.value.allow, evaluated.value.reason)
                is EvaluationResult.Skipped -> SuppressionResult.Skipped(evaluated.reason)
                is EvaluationResult.Failed -> SuppressionResult.Failed(
                    evaluated.error.message ?: evaluated.error.toString()
                )
            }
            result = outcome
            store.recordRun(
                summary = messageName.ifEmpty { condition },
                output = outcome.summary,
                state = currentState()
            )
            isRunning = false
        }
    }

    /** The full instruction text the SDK will send — the fixed template with the current condition. */
    val assembledInstructions: String
        get() = InAppMessageSuppressionEvaluation(condition, makeSubject()).instructions()

    /** The rendered user prompt for the currently resolved context. */
    val assembledPrompt: String
        get() = InAppMessageSuppressionEvaluation(condition, makeSubject())
            .prompt(context ?: EvaluationContext.EMPTY)

    private fun syncContextProvider() {
        when (contextMode) {
            AirshipDebugContextMode.Override -> {
                val ctx = contextItems.toEvaluationContext()
                Airship.internalAi.setContextProvider(usage) { ctx }
            }
            AirshipDebugContextMode.ProviderOnly, AirshipDebugContextMode.Append ->
                Airship.internalAi.setContextProvider(usage, null)
        }
    }

    private fun makeSubject(): InAppMessageSuppressionSubject = InAppMessageSuppressionSubject(
        name = messageName,
        extras = extrasJSON.trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { JsonValue.parseString(it) }.getOrNull()
        },
        priority = 0,
        hints = parseStringMap(hintsJSON)
    )
}

private fun parseStringMap(json: String): Map<String, String> {
    val trimmed = json.trim()
    if (trimmed.isEmpty()) return emptyMap()
    return try {
        JsonValue.parseString(trimmed).map?.map?.mapValues { it.value.optString() } ?: emptyMap()
    } catch (e: JsonException) {
        emptyMap()
    }
}
