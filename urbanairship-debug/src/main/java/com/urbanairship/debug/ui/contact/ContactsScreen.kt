/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.contact

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.UAirship
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun ContactsScreen(
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {

    DebugScreen(
        title = stringResource(id = ContactScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(onNavigate = onNavigate)
    }
}

@Composable
private fun ScreenContent(onNavigate: (String) -> Unit = {}) {
    Column {
        RowItem(
            modifier = Modifier.clickable { onNavigate(ContactScreens.NamedUser.route) },
            title = "Named User",
            details = if (UAirship.isFlying) { UAirship.shared().contact.namedUserId } else { null },
            accessory = { Icon(Icons.Default.ChevronRight, contentDescription = "display") }
        )

        RowItem(
            modifier = Modifier.clickable { onNavigate(ContactScreens.TagGroups.route) },
            title = "Tag Groups",
            accessory = { Icon(Icons.Default.ChevronRight, contentDescription = "display") }
        )

        RowItem(
            modifier = Modifier.clickable { onNavigate(ContactScreens.Attributes.route) },
            title = "Attributes",
            accessory = { Icon(Icons.Default.ChevronRight, contentDescription = "display") }
        )

        RowItem(
            modifier = Modifier.clickable { onNavigate(ContactScreens.SubscriptionList.route) },
            title = "Subscription Lists",
            accessory = { Icon(Icons.Default.ChevronRight, contentDescription = "display") }
        )

        RowItem(
            modifier = Modifier.clickable { onNavigate(ContactScreens.AddChannel.route) },
            title = "Add Channel",
            accessory = { Icon(Icons.Default.ChevronRight, contentDescription = "display") }
        )
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        ContactsScreen()
    }
}
