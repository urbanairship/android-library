package com.urbanairship.debug.ui.push

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.debug.ServiceLocator
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.JsonItem
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.json.JsonValue
import com.urbanairship.push.PushMessage
import kotlinx.coroutines.flow.map

@Composable
internal fun PushDetailsScreen(
    id: String,
    onNavigateUp: () -> Unit = {}
) {
    val viewModel: PushViewModel =
        viewModel(factory = PushViewModelFactory(ServiceLocator.shared(LocalContext.current).getPushRepository()))
    DebugScreen(
        title = stringResource(id = PushScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        PushDetailsScreenContent(
            viewModel = viewModel,
            id = id
        )
    }
}

@Composable
internal fun PushDetailsScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: PushViewModel,
    id: String
) {
    val item by viewModel.pushes
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
            JsonItem(PushMessage.fromJsonValue(JsonValue.parseString(it.payload)))
        }
    } ?: Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.width(64.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
