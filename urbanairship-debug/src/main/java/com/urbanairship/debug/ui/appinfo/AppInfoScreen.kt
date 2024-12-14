package com.urbanairship.debug.ui.appinfo

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.ConfigurationCompat
import com.urbanairship.UAirship
import com.urbanairship.debug.ui.components.DebugCategoryHeader
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.DebugSettingItem
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun AppInfoScreen(
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    DebugScreen(
        title = stringResource(id = AppInfoScreen.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        AppInfoScreenContent(
            onNavigateUp = onNavigateUp,
            onNavigate = onNavigate,
        )
    }
}


@Composable
internal fun AppInfoScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    onNavigateUp: () -> Unit,
    onNavigate: (String) -> Unit,
) {

    val context = LocalContext.current

    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

    val sdkVersion = UAirship.getVersion()
    val appVersionName = packageInfo.versionName
    val appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

    val lazyListState = rememberLazyListState()
    LazyColumn(
        state = lazyListState,
        modifier = modifier
    ) {
        item {
            DebugCategoryHeader("Versions")
        }

        item {
            DebugSettingItem(
                title = "SDK Version",
                currentValue = sdkVersion
            )
        }

        item {
            DebugSettingItem(
                title = "App Version Name",
                currentValue = appVersionName
            )
        }

        item {
            DebugSettingItem(
                title = "App Version Code",
                currentValue = "$appVersionCode"
            )
        }

        item {
            DebugCategoryHeader("Locale")
        }

        item {
            DebugSettingItem(
                title = "Device Locale",
                currentValue = "${ConfigurationCompat.getLocales(context.resources.configuration).get(0)}"
            )
        }
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppInfoScreenPreview() {
    AirshipDebugTheme {
        AppInfoScreen()
    }
}
