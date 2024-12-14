package com.urbanairship.debug.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.IconDecoration
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun DebugHomeScreen(
    modifier: Modifier = Modifier.fillMaxSize(),
    onNavigate: (String) -> Unit = {},
) {
    DebugScreen(
        title = stringResource(id = TopLevelScreens.Root.titleRes),
        modifier = modifier,
    ) {
        DebugHomeScreenContent(
            onNavigate = onNavigate
        )
    }
}

@Composable
internal fun DebugHomeScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    onNavigate: (String) -> Unit,
) {
    val topLevelScreens = remember { TopLevelScreens.topLevelScreens }
    LazyColumn(
        modifier = modifier
    ) {
        itemsIndexed(topLevelScreens) { index, screen ->
            Surface(
                modifier = Modifier.clickable { onNavigate(screen.route) }
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(id = screen.titleRes))
                    },
                    supportingContent = {
                        screen.descRes?.let {
                            Text(stringResource(id = it)) }
                    },
                    leadingContent = {
                        screen.icon?.let { icon ->
                            IconDecoration(imageVector = icon)
                        }
                    }
                )

                if (index < topLevelScreens.size) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
            }

        }
    }
}


@Preview
@Composable
private fun DebugScreenPreview() {
    AirshipDebugTheme {
        DebugHomeScreen()
    }
}
