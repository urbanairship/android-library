/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.privacymanager

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun PrivacyScreen(
    viewModel: PrivacyViewModel = viewModel<DefaultPrivacyViewModel>(),
    onNavigateUp: () -> Unit = {}
) {
    DebugScreen(
        title = stringResource(id = PrivacyScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel)
    }
}

@Composable
private fun ScreenContent(
    viewModel: PrivacyViewModel
) {

    val state = viewModel.features.collectAsState(emptyList())

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        state.value.forEach { feature ->
            RowItem(title = feature.name, accessory = {
                Switch(feature.isEnabled, onCheckedChange = {
                    viewModel.toggle(feature)
                })
            })
        }
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrivacyScreenPreview() {
    AirshipDebugTheme {
        PrivacyScreen(
            viewModel = PrivacyViewModel.forPreview()
        )
    }
}
