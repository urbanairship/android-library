package com.urbanairship.debug.ui.deviceinfo

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.UAirship
import com.urbanairship.debug.AirshipDebug
import com.urbanairship.debug.ui.components.DebugCategoryHeader
import com.urbanairship.debug.ui.components.DebugSettingItem
import com.urbanairship.debug.ui.components.DebugSwitchItem
import com.urbanairship.debug.ui.components.TopAppBar
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import java.util.UUID

@Composable
internal fun DeviceInfoScreen(
    modifier: Modifier = Modifier.fillMaxSize(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {

    Scaffold(
        topBar = {
            AirshipDebug.TopAppBar(
                title = stringResource(id = DeviceInfoScreens.rootScreen.titleRes),
                onNavigateUp = onNavigateUp
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        DeviceInfoScreenContent(
            modifier = modifier.padding(padding),
            onNavigateUp = onNavigateUp,
            onNavigate = onNavigate,
        )
    }
}


@Composable
internal fun DeviceInfoScreenContent(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val lazyListState = rememberLazyListState()

    // TODO: these all need to be moved into a view model and wired up to the SDK properly,
    //  so that they will update when the SDK state changes.
    val namedUser = "fred"
    val displayInterval = "0 Seconds"
    val channelId = remember { UUID.randomUUID().toString() }
    val userId = "LAJSMimsoML_asoIMOVjA"
    val pushOptInStatus = true
    val notificationOptInStatus = true
    val pushProvider = "FCM"
    val pushToken = "eOBjFpelTtSS3sz0ZaP-Bp:APA91bHUDgmCfeiDA5dWWbzrmWd9fjedwD-QxSy69cRSKqu2GvGWerlCgLvsTuUwargqtxz_n1rYqQWX3GOHjLtZomHG64hTYyOD5RRf8W9MnQ4l6b07xHQGd3IRkCB91bigGFXQ8GcFpDkX"
    val sdkVersion = UAirship.getVersion()
    val locale = "en_US"
    val deviceManufacturer = "Google"
    val deviceModel = "Pixel 8"

    LazyColumn(
        state = lazyListState,
        modifier = modifier
    ) {
        item {
            DebugCategoryHeader("SDK Features")
        }

        item {
            DebugSwitchItem(
                title = "Push",
                checked = true,
                onCheckedChange = {
                    // TODO
                }
            )
        }

        item {
            DebugSwitchItem(
                title = "MessageCenter",
                checked = true,
                onCheckedChange = {
                    // TODO
                }
            )
        }

        item {
            DebugSwitchItem(
                title = "In-App Automation",
                checked = false,
                onCheckedChange = {
                    // TODO
                }
            )
        }

        item {
            DebugSwitchItem(
                title = "Analytics",
                checked = false,
                onCheckedChange = {
                    // TODO
                }
            )
        }

        item {
            DebugSwitchItem(
                title = "Contacts",
                checked = false,
                onCheckedChange = {
                    // TODO
                }
            )
        }

        item {
            DebugSwitchItem(
                title = "Tags & Attributes",
                checked = false,
                onCheckedChange = {
                    // TODO
                }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 16.dp))
        }

        item {
            DebugCategoryHeader("User Settings")
        }

        item {
            DebugSwitchItem(
                title = "Notifications Enabled",
                checked = true,
                onCheckedChange = {
                    // TODO
                }
            )
        }

        item {
            DebugSettingItem(
                title = "Named User",
                currentValue = namedUser,
                onClick = { /*TODO*/ }
            )
        }

        item {
            DebugSettingItem(
                title = "Attributes",
                description = "Manage Channel & Contact attributes",
                onClick = { /*TODO*/ }
            )
        }

        item {
            val tags = listOf("foo", "bar", "baz")

            DebugSettingItem(
                title = "Tags",
                currentValue = tags.joinToString(),
                onClick = { onNavigate(DeviceInfoScreens.EditTags.route) }
            )
        }

        item {
            DebugSettingItem(
                title = "Tag Groups",
                onClick = { /*TODO*/ }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 16.dp))
        }

        item {
            DebugCategoryHeader("Analytics Settings")
        }

        item {
            DebugSwitchItem(
                title = "Track Advertising ID",
                checked = true,
                onCheckedChange = {
                    // TODO
                }
            )
        }


        item {
            DebugSettingItem(
                title = "Associated Identifiers",
                onClick = { /*TODO*/ }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 16.dp))
        }

        item {
            DebugCategoryHeader("In-App Automation Settings")
        }

        item {
            DebugSettingItem(
                title = "Display Interval",
                currentValue = displayInterval,
                onClick = { /*TODO*/ }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 16.dp))
        }

        item {
            DebugCategoryHeader("Device Info")

        }

        item {
            DebugSettingItem(
                title = "Channel ID",
                currentValue = channelId,
                onClick = { /*TODO*/ }
            )
        }

        item {
            DebugSettingItem(
                title = "User ID",
                currentValue = userId,
                onClick = { /*TODO*/ }
            )
        }

        item {
            DebugSettingItem(
                title = "Push Opt-in Status",
                currentValue = if (pushOptInStatus) "Opted In" else "Opted Out",
            )
        }

        item {
            DebugSettingItem(
                title = "Notification Opt-in Status",
                currentValue = if (notificationOptInStatus) "Opted In" else "Opted Out",
            )
        }

        item {
            DebugSettingItem(
                title = "Push Provider",
                currentValue = pushProvider,
            )
        }

        item {
            DebugSettingItem(
                title = "Push Token",
                currentValue = pushToken,
            )
        }

        item {
            DebugSettingItem(
                title = "Airship SDK Version",
                currentValue = sdkVersion,
            )
        }

        item {
            DebugSettingItem(
                title = "Current Locale",
                currentValue = locale,
            )
        }

        item {
            DebugSettingItem(
                title = "Manufacturer",
                currentValue = deviceManufacturer,
            )
        }

        item {
            DebugSettingItem(
                title = "Model",
                currentValue = deviceModel,
            )
        }
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
