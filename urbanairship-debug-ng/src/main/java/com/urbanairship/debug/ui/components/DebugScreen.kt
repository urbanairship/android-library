package com.urbanairship.debug.ui.components

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

                        is TopBarNavigation.None -> null
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = actionButton
    ) { padding ->
        Surface(modifier = modifier.padding(padding)) {
            content()
        }
    }
}


internal sealed class TopBarNavigation {
    data class Back(val onBack: () -> Unit): TopBarNavigation()
    data object None: TopBarNavigation()
}
