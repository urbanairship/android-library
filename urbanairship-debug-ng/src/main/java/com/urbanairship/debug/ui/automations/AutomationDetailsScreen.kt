package com.urbanairship.debug.ui.automations

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.JsonItem
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import com.urbanairship.json.jsonMapOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Composable
internal fun AutomationDetailsScreen(
    name: String,
    id: String,
    viewModel: AutomationViewModel = viewModel<DefaultAutomationViewModel>(),
    onNavigateUp: () -> Unit = {}
) {
    DebugScreen(
        title = name,
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        AutomationDetailsScreenContent(
            viewModel = viewModel,
            id = id
        )
    }
}

@Composable
internal fun AutomationDetailsScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: AutomationViewModel,
    id: String
) {
    val item by viewModel.automations
        .map { list ->
            list.first {
                it.id == id
            }
        }
        .collectAsState(initial = null)
    item?.let {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState())
        ) {
            JsonItem(jsonSerializable = it.body)
        }
    } ?: Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.width(64.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        AutomationDetailsScreen(
            id = "Automation",
            name = "Automation",
            viewModel = AutomationViewModel.forPreview()
        )
    }
}
