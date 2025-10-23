/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.contact

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Airship
import com.urbanairship.contacts.ContactChannel
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull

@Composable
internal fun AddChannelScreen(
    viewModel: CreateChannelViewModel = viewModel(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {

    DebugScreen(
        title = stringResource(id = ContactScreens.AddChannel.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel, onNavigate)
    }
}

@Composable
private fun ScreenContent(
    viewModel: CreateChannelViewModel,
    onNavigate: (String) -> Unit
) {
    LazyColumn {
        item {
            Section(title = "Channel Type") {
                RowItem(
                    modifier = Modifier.clickable { onNavigate(ContactChannelScreens.OpenChannel.route) },
                    title = "Open Channel",
                    accessory = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "display") }
                )

                RowItem(
                    modifier = Modifier.clickable { onNavigate(ContactChannelScreens.SMSChannel.route) },
                    title = "SMS Channel",
                    accessory = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "display") }
                )

                RowItem(
                    modifier = Modifier.clickable { onNavigate(ContactChannelScreens.EmailChannel.route) },
                    title = "Email Channel",
                    accessory = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "display") }
                )
            }
        }

        item {
            Spacer(Modifier.padding(top = 16.dp))

            val channels = viewModel.subscribedChannels.collectAsState(initial = emptyList()).value
            Section(title = "Subscribed Channels") {
                if (channels.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("No channels found")
                    }
                } else {
                    channels.forEach { channel ->
                        RowItem(
                            title = channel.maskedAddress,
                            details = channel.channelType.name)
                    }
                }
            }
        }
    }
}

internal class CreateChannelViewModel: ViewModel() {
    var subscribedChannels: Flow<List<ContactChannel>> = flowOf(emptyList())

    init {
        Airship.onReady {
            subscribedChannels = contact.channelContacts.mapNotNull { it.getOrNull() }
        }
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        AddChannelScreen()
    }
}
