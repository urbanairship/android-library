/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun AnalyticsScreens(
    viewModel: AnalyticsViewModel = viewModel<DefaultAnalyticsViewModel>(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {

    DebugScreen(
        title = stringResource(id = AnalyticsScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(
            viewModel = viewModel,
            onNavigate = onNavigate
        )
    }
}

@Composable
private fun ScreenContent(
    viewModel: AnalyticsViewModel,
    onNavigate: (String) -> Unit
) {

    val isLimited = viewModel.trackAdvertisingId.collectAsState(true).value
    Column {
        RowItem(
            title = "Track Advertising ID",
            accessory = {
                Switch(
                    checked = isLimited,
                    enabled = false,
                    onCheckedChange = {}
                )
            }
        )

        RowItem(
            modifier = Modifier.clickable { onNavigate(AnalyticsScreens.Events.route) },
            title = "Events",
            accessory = { Icon(painter = painterResource(id = R.drawable.ic_chevron), contentDescription = "display") }
        )

        RowItem(
            modifier = Modifier.clickable { onNavigate(AnalyticsScreens.AddCustom.route) },
            title = "Add Custom Event",
            accessory = { Icon(painter = painterResource(id = R.drawable.ic_chevron), contentDescription = "display") }
        )

        RowItem(
            modifier = Modifier.clickable { onNavigate(AnalyticsScreens.AssociatedIdentifiers.route) },
            title = "Associated Identifiers",
            accessory = { Icon(painter = painterResource(id = R.drawable.ic_chevron), contentDescription = "display") }
        )
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnalyticsScreenPreview() {
    AirshipDebugTheme {
        AnalyticsScreens()
    }
}
