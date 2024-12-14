package com.urbanairship.debug.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.urbanairship.debug.LocalIgnoreBottomPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebugScreen(
    title: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    navigation: TopBarNavigation = TopBarNavigation.None,
    actionButton: @Composable () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = title)
                },
                navigationIcon = {
                    when(navigation) {
                        is TopBarNavigation.Back -> {
                            IconButton(onClick = navigation.onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        }

                        is TopBarNavigation.None -> Unit
                    }
                }
            )
        },
        modifier = modifier,
        floatingActionButton = actionButton
    ) { contentPadding ->
        // Check if the bottom padding should be ignored
        // (e.g., when the screen is embedded in a screen that already handles insets/content padding)
        val ignoreBottomPadding = LocalIgnoreBottomPadding.current
        val padding = if (ignoreBottomPadding) {
            contentPadding.withoutBottomPadding()
        } else {
            contentPadding
        }

        Surface(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

@Composable
private fun PaddingValues.withoutBottomPadding(
    layoutDirection: LayoutDirection = LocalLayoutDirection.current
): PaddingValues = PaddingValues(
    start = this.calculateStartPadding(layoutDirection),
    end = this.calculateEndPadding(layoutDirection),
    top = this.calculateTopPadding(),
    bottom = 0.dp
)


internal sealed class TopBarNavigation {
    data class Back(val onBack: () -> Unit): TopBarNavigation()
    data object None: TopBarNavigation()
}
