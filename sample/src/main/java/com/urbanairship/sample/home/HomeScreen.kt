package com.urbanairship.sample.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.urbanairship.sample.MainActivity
import com.urbanairship.sample.R
import com.urbanairship.sample.ui.theme.AirshipColors
import AirshipTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    backStack: MainActivity.TopLevelBackStack<NavKey>,
    viewModel: HomeViewModel = viewModel<HomeViewModel>(),
    modifier: Modifier = Modifier) {

    val snackbarHostState = remember { SnackbarHostState() }
    val channelId by viewModel.channelId.collectAsState()
    val unreadCount by viewModel.unreadMessageCount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(unreadCount) {
        if (unreadCount > 0) {
            val result = snackbarHostState.showSnackbar(
                message = context.resources.getQuantityString
                    (R.plurals.mc_indicator_text, unreadCount, unreadCount),
                actionLabel = "View",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                backStack.switchTopLevel(MainActivity.Message)
            }
        }
    }

    AirshipTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = "Home")
                    }
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            modifier = modifier
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(0.5f))
                    Text(text = stringResource(R.string.channel_id),
                        style = MaterialTheme.typography.headlineSmall)
                    Text(color = AirshipColors.colorAccent, text = channelId ?: "", maxLines = 1,
                        modifier = Modifier.clickable {
                           viewModel.copyToClipboard(context)
                        })
                    Spacer(modifier = Modifier.weight(1.5f))
                }
            }
        }
    }
}
