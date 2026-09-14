/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.ai

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Airship
import com.urbanairship.AirshipDispatchers
import com.urbanairship.ai.ModelAvailability
import com.urbanairship.ai.Usage
import com.urbanairship.android.layout.ai.sceneTextInput
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import com.urbanairship.iam.ai.inAppMessageSuppression
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Placeholder key for the embedded selection usage.
 *
 * There's no `Usage<EmbeddedSelectionSubject>` (or evaluation, or model ranking) on Android yet —
 * this only reserves the row and route so the sandbox can be filled in once that feature ships,
 * without another round of nav/screen wiring.
 */
internal const val EMBEDDED_SELECTION_USAGE_KEY: String = "embedded_selection"

/** Usage keys the debug UI knows how to drive a sandbox for. */
internal val AI_DEBUG_USAGE_KEYS: List<String> = listOf(
    Usage.inAppMessageSuppression.rawValue,
    Usage.sceneTextInput.rawValue,
    EMBEDDED_SELECTION_USAGE_KEY
)

internal fun aiUsageDisplayName(usageKey: String): String = when (usageKey) {
    Usage.inAppMessageSuppression.rawValue -> "In-App Message Suppression"
    Usage.sceneTextInput.rawValue -> "Scene Text Input"
    EMBEDDED_SELECTION_USAGE_KEY -> "Embedded Selection"
    else -> usageKey
}

@Composable
internal fun AiRootScreen(
    viewModel: AiRootScreenViewModel = viewModel<DefaultAiRootScreenViewModel>(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    DebugScreen(
        title = stringResource(id = AIScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        AiRootScreenContent(viewModel = viewModel, onNavigate = onNavigate)
    }
}

@Composable
private fun AiRootScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: AiRootScreenViewModel,
    onNavigate: (String) -> Unit
) {
    LazyColumn(modifier = modifier) {
        items(count = AI_DEBUG_USAGE_KEYS.size) { index ->
            val usageKey = AI_DEBUG_USAGE_KEYS[index]
            Column(
                modifier = Modifier.clickable {
                    onNavigate("${AIScreens.Usage.route}/$usageKey")
                }
            ) {
                ListItem(
                    headlineContent = { Text(aiUsageDisplayName(usageKey)) },
                    supportingContent = {
                        Text(
                            text = if (usageKey == EMBEDDED_SELECTION_USAGE_KEY) {
                                "Not yet available on Android"
                            } else {
                                viewModel.availabilityByUsage[usageKey].debugLabel()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

private fun ModelAvailability?.debugLabel(): String = when (this) {
    null -> "No model"
    ModelAvailability.Available -> "Available"
    is ModelAvailability.Unavailable -> "Unavailable — ${reason.debugLabel()}"
}

private fun ModelAvailability.Reason.debugLabel(): String = when (this) {
    ModelAvailability.Reason.DeviceNotEligible -> "Device or OS can't run the model"
    ModelAvailability.Reason.MissingModel -> "No model available (not downloaded or still preparing)"
    ModelAvailability.Reason.NotEnabled -> "AI features are turned off"
    is ModelAvailability.Reason.Other -> description
}

internal interface AiRootScreenViewModel {
    val availabilityByUsage: Map<String, ModelAvailability>

    companion object {
        fun forPreview(): AiRootScreenViewModel = object : AiRootScreenViewModel {
            override val availabilityByUsage: Map<String, ModelAvailability> = mapOf(
                Usage.inAppMessageSuppression.rawValue to ModelAvailability.Available,
                Usage.sceneTextInput.rawValue to ModelAvailability.Unavailable(ModelAvailability.Reason.MissingModel)
            )
        }
    }
}

internal class DefaultAiRootScreenViewModel(
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) : AiRootScreenViewModel, ViewModel() {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private var availabilityState by mutableStateOf<Map<String, ModelAvailability>>(emptyMap())
    override val availabilityByUsage: Map<String, ModelAvailability>
        get() = availabilityState

    init {
        Airship.onReady {
            observe(Usage.inAppMessageSuppression)
            observe(Usage.sceneTextInput)
        }
    }

    private fun <Subject> observe(usage: Usage<Subject>) {
        val updates = Airship.internalAi.gatedModel(usage)?.availabilityUpdates ?: return
        scope.launch {
            updates.collect { availability ->
                availabilityState = availabilityState + (usage.rawValue to availability)
            }
        }
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AiRootScreenPreview() {
    AirshipDebugTheme {
        AiRootScreen(viewModel = AiRootScreenViewModel.forPreview())
    }
}
