/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.deviceinfo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.TopBarNavigation

@Composable
internal fun NavigationSelectionScreen(
    title: String,
    items: List<NavigationSelectionItem>,
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {

    DebugScreen(
        title = title,
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        Column {
            items.forEach { item ->
                RowItem(
                    modifier = Modifier.clickable { onNavigate(item.route) },
                    title = item.name,
                    accessory = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "display") }
                )
            }
        }
    }
}

internal data class NavigationSelectionItem(
    val name: String,
    val route: String
)
