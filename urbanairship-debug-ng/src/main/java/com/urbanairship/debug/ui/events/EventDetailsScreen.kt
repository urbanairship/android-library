/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.debug.ServiceLocator
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.JsonItem
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import kotlinx.coroutines.flow.map

@Composable
internal fun EventDetailsScreen(
    type: String,
    id: String,
    onNavigateUp: () -> Unit = {}
) {
    val viewModel: EventViewModel =
        viewModel(factory = EventViewModelFactory(ServiceLocator.shared(LocalContext.current).getEventRepository()))
    DebugScreen(
        title = type,
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        EventDetailsScreenContent(
            viewModel = viewModel,
            id = id
        )
    }
}

@Composable
internal fun EventDetailsScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: EventViewModel,
    id: String
) {
    val item by viewModel.events
        .map { list ->
            list.first {
                it.id.toString() == id
            }
        }
        .collectAsState(initial = null)
    item?.let {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState())
        ) {
            JsonItem(jsonSerializable = it.toJson())
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
private fun EventsScreenPreview() {
    AirshipDebugTheme {
        EventDetailsScreen(
            id = "1234abcdef",
            type = "Event"
        )
    }
}
