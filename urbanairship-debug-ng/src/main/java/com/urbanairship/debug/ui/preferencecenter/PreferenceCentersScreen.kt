package com.urbanairship.debug.ui.preferencecenter

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.DebugSettingItem
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import kotlinx.coroutines.flow.Flow

@Composable
internal fun PreferenceCentersScreen(
    viewModel: PreferenceCenterViewModel = viewModel<DefaultPreferenceCenterViewModel>(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    DebugScreen(
        title = stringResource(id = PreferenceCenterScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        PreferenceCentersScreenContent(
            viewModel = viewModel
        )
    }
}


@Composable
internal fun PreferenceCentersScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: PreferenceCenterViewModel
) {
    val items by viewModel.preferenceCenters.collectAsState(initial = listOf())
    val context = LocalContext.current

    LazyColumn(modifier = modifier) {
        items(
            count = items.size,
            key = { index -> items[index].id },
            itemContent = { index ->
                val item = items[index]


                DebugSettingItem(item.title) {
                    viewModel.openPreferenceCenter(context, item.id)
                }

                HorizontalDivider()
            }
        )
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppInfoScreenPreview() {
    AirshipDebugTheme {
        PreferenceCentersScreen(
            viewModel = PreferenceCenterViewModel.forPreview()
        )
    }
}
