package com.urbanairship.debug.ui.automations

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun AutomationScreen(
    viewModel: AutomationViewModel = viewModel<DefaultAutomationViewModel>(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    DebugScreen(
        title = stringResource(id = AutomationScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        AutomationScreenContent(
            viewModel = viewModel,
            onNavigate = onNavigate
        )
    }
}


@Composable
internal fun AutomationScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: AutomationViewModel,
    onNavigate: (String) -> Unit = {}
) {
    val items by viewModel.automations.collectAsState(initial = listOf())

    LazyColumn(modifier = modifier) {
        items(
            count = items.size,
            key = { index -> items[index].id },
            itemContent = { index ->
                val item = items[index]


                ListItem(
                    modifier = modifier.clickable {
                        onNavigate("${AutomationScreens.Details.route}/${item.name}/${item.id}")
                    },
                    headlineContent = {
                        Text(text = item.name, fontWeight = FontWeight.Medium)
                    }, trailingContent = {

                    }
                )

            }
        )
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        AutomationScreen(
            viewModel = AutomationViewModel.forPreview()
        )
    }
}
