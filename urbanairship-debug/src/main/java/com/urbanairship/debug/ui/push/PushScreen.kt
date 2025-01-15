package com.urbanairship.debug.ui.push

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
internal fun PushScreen(
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    val viewModel: PushViewModel =
        viewModel(factory = PushViewModelFactory(ServiceLocator.shared(LocalContext.current).getPushRepository()))
    DebugScreen(
        title = stringResource(id = PushScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        PushScreenContent(
            viewModel = viewModel,
            onNavigate = onNavigate
        )
    }
}


@Composable
internal fun PushScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: PushViewModel,
    onNavigate: (String) -> Unit = {}
) {
    val items by viewModel.pushes.collectAsState(initial = listOf())

    LazyColumn(modifier = modifier) {
        items(
            count = items.size,
            key = { index -> items[index].id },
            itemContent = { index ->
                val item = items[index]
                ListItem(
                    modifier = modifier.clickable {
                        onNavigate("${PushScreens.Details.route}/${item.id}")
                    },
                    headlineContent = {
                        Text(text = PushItem(item).alert?: "Push", fontWeight = FontWeight.Medium)
                    }, trailingContent = {
                        Text(text = item.pushId, fontWeight = FontWeight.Medium)
                    }
                )
            }
        )
    }
}
