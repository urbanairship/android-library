/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.debug.ServiceLocator
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.TopBarNavigation

@Composable
internal fun EventScreen(
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    val viewModel: EventViewModel =
        viewModel(factory = EventViewModelFactory(ServiceLocator.shared(LocalContext.current).getEventRepository()))
    DebugScreen(
        title = stringResource(id = EventScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        EventScreenContent(
            viewModel = viewModel,
            onNavigate = onNavigate
        )
    }
}


@Composable
internal fun EventScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: EventViewModel,
    onNavigate: (String) -> Unit = {}
) {
    val items by viewModel.events.collectAsState(initial = listOf())

    LazyColumn(modifier = modifier) {
        items(
            count = items.size,
            key = { index -> items[index].id },
            itemContent = { index ->
                val item = items[index]
                ListItem(
                    modifier = modifier.clickable {
                        onNavigate("${EventScreens.Details.route}/${item.type}/${item.id}")
                    },
                    headlineContent = {
                        Text(text = item.eventId, fontWeight = FontWeight.Medium)
                    }, trailingContent = {
                        Text(text = item.type.toString(), fontWeight = FontWeight.Medium)
                    }
                )
            }
        )
    }
}
