package com.urbanairship.debug.ui.deviceinfo

import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Airship
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.DebugScreen
import com.urbanairship.debug.ui.channel.ChannelInfoScreens
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.DebugSettingItem
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.contact.ContactScreens
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import java.util.Locale

@Composable
internal fun DeviceInfoScreen(
    viewModel: DeviceInfoViewModel = viewModel<DefaultDeviceInfoViewModel>(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {

    DebugScreen(
        title = stringResource(id = DeviceInfoScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        DeviceInfoScreenContent(
            viewModel = viewModel,
            onNavigateUp = onNavigateUp,
            onNavigate = onNavigate,
        )
    }
}


@Composable
internal fun DeviceInfoScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: DeviceInfoViewModel,
    onNavigateUp: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        modifier = modifier
    ) {
        item {
            DeviceInfoSection(viewModel)
        }

        item {
            Spacer(Modifier.height(24.dp))
            PushContent(viewModel, onNavigate)
        }

        item {
            Spacer(Modifier.height(24.dp))
            UserSettingsContent(viewModel, onNavigate)
        }
    }
}

@Composable
private fun PushContent(
    viewModel: DeviceInfoViewModel,
    onNavigate: (String) -> Unit
) {
    val isPushEnabled = viewModel.isPushEnabled.collectAsState(false).value
    val isOptedIn = viewModel.pushOptInt.collectAsState(false).value
    val pushToken = viewModel.pushToken.collectAsState(null).value
    val pushProvider = viewModel.pushProvider.collectAsState(null).value
    val context = LocalContext.current

    Section(title = "Push") {
        Column {
            RowItem(title = "Notification Enabled", accessory = {
                Switch(isPushEnabled, onCheckedChange = {
                    viewModel.togglePushEnabled()
                })
            })

            RowItem(title = "Opt-In Status", details = if (isOptedIn) "Opted-In" else "Opted-Out")

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .clickable { viewModel.copyPushToken(context) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Device Token", fontWeight = FontWeight.Medium)
                pushToken?.let {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = it,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light)
                }

            }

            HorizontalDivider()

            RowItem(title = "Push Provider", details = pushProvider?.name?.uppercase())

            RowItem(
                modifier = Modifier.clickable { onNavigate(DebugScreen.Pushes.route) },
                title = "Received Pushes",
                accessory = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "display") }
            )
        }
    }
}

@Composable
private fun UserSettingsContent(
    viewModel: DeviceInfoViewModel,
    onNavigate: (String) -> Unit
) {
    val userId = viewModel.namedUser.collectAsState(null)
    val channelTags = viewModel.channelTags.collectAsState(emptySet())
    val formattedChannelTags by derivedStateOf {
        channelTags.value.ifEmpty { null }?.sorted()?.joinToString(", ")
    }

    Section(title = "User Settings") {
        Column {
            DebugSettingItem(
                title = "Named User",
                currentValue = userId.value,
                icon = R.drawable.ic_chevron,
                onClick = { onNavigate(ContactScreens.NamedUser.route) }
            )

            DebugSettingItem(
                title = "Attributes",
                description = "Manage Channel & Contact attributes",
                icon = R.drawable.ic_chevron,
                onClick = { onNavigate(DeviceInfoScreens.EditAttributes.route) }
            )

            DebugSettingItem(
                title = "Tags",
                description = formattedChannelTags,
                icon = R.drawable.ic_chevron,
                onClick = { onNavigate(ChannelInfoScreens.Tags.route) }
            )

            DebugSettingItem(
                title = "Tag Groups",
                description = "Manage Channel & Contact tag groups",
                icon = R.drawable.ic_chevron,
                onClick = { onNavigate(DeviceInfoScreens.EditTagGroups.route) }
            )
        }
    }
}

@Composable
private fun DeviceInfoSection(
    viewModel: DeviceInfoViewModel
) {
    val channelId = viewModel.channelId.collectAsState(null).value
    val userId = viewModel.contactId.collectAsState(null).value
    val context = LocalContext.current
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
        LocaleList.getDefault().get(0)
    } else{
        Locale.getDefault()
    }

    Section(title = "Info") {
        DebugSettingItem(
            title = "Channel ID",
            description = channelId,
            onClick = { viewModel.copyChannelId(context) }
        )

        HorizontalDivider()

        DebugSettingItem(
            title = "User ID",
            description = userId,
            onClick = { viewModel.copyUserId(context) }
        )

        HorizontalDivider()

        RowItem(
            title = "Airship SDK Version",
            details = Airship.version)

        RowItem(
            title = "Current Locale",
            details = locale.toString()
        )

        RowItem(
            title = "Manufacturer",
            details = Build.MANUFACTURER
        )

        RowItem(
            title = "Model",
            details = Build.MODEL
        )
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeviceInfoScreenPreview() {
    AirshipDebugTheme {
        DeviceInfoScreen()
    }
}
