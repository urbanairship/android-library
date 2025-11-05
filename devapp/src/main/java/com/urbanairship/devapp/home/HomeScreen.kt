package com.urbanairship.devapp.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.devapp.AppRouterViewModel
import com.urbanairship.devapp.Destination
import com.urbanairship.messagecenter.Message
import com.urbanairship.devapp.R
import com.urbanairship.json.jsonMapOf
import com.urbanairship.liveupdate.LiveUpdate
import com.urbanairship.liveupdate.LiveUpdateManager
import AirshipTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    viewModel: HomeViewModel = viewModel<DefaultHomeViewModel>()
) {

    val snackbarHostState = remember { SnackbarHostState() }
    val channelId by viewModel.channelId.collectAsState()
    val namedUserId = viewModel.namedUserId.collectAsState().value
    val unreadCount by viewModel.unreadMessageCount.collectAsState(initial = emptyList())
    val context = LocalContext.current

    LaunchedEffect(unreadCount) {
        if (unreadCount.isNotEmpty()) {
            val result = snackbarHostState.showSnackbar(
                message = context.resources.getQuantityString
                    (R.plurals.mc_indicator_text, unreadCount.size, unreadCount.size),
                actionLabel = "View",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onNavigate(AppRouterViewModel.TopLevelDestination.Message())
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
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(0.5f))

                    NotificationStatusButton(
                        isEnabled = viewModel.isOptedInForPushes.collectAsState().value,
                        onTap = viewModel::togglePushStatus
                    )

                    Spacer(Modifier.padding(bottom = 12.dp))

                    QuickSettingItem(
                        title = stringResource(R.string.channel_id),
                        subtitle = channelId ?: "Not Created",
                        onClick = { viewModel.copyToClipboard(context) }
                    )
                    HorizontalDivider()

                    QuickSettingItem(
                        title = stringResource(R.string.named_user),
                        subtitle = namedUserId ?: "Not Set",
                        onClick = { onNavigate(QuickAccess.NamedUserScreen) }
                    )
                    HorizontalDivider()

                    QuickSettingItem(
                        title = stringResource(R.string.start_live_activity),
                        subtitle = "Start a sport live update",
                        onClick = {
                            LiveUpdateManager.shared().start(
                                name = "sample-sports",
                                type = "sports",
                                content = jsonMapOf(
                                    "team_one_score" to 0,
                                    "team_two_score" to 0,
                                    "status_update" to "Game started!"
                                )
                            )
                        }
                    )
                    HorizontalDivider()

                    QuickSettingItem(
                        title = stringResource(R.string.thomas_layouts),
                        subtitle = "Tap to preview layouts",
                        onClick = { onNavigate(QuickAccess.ThomasLayoutsHome) }
                    )
                    HorizontalDivider()

                    Spacer(modifier = Modifier.weight(0.5f))
                }
            }
        }
    }
}

@Composable
private fun NotificationStatusButton(isEnabled: Boolean, onTap: () -> Unit) {
    val icon = if (isEnabled) R.drawable.ic_notifications_off else R.drawable.ic_notifications
    val text = if (isEnabled) R.string.disable_notifications else R.string.enable_notifications

    Button(
        modifier = Modifier
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        ),
        onClick = onTap
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(icon),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp)
            )

            Text(stringResource(text))
        }
    }
}

@Composable
private fun QuickSettingItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    val homeViewModel = object : HomeViewModel {
        override var channelId: StateFlow<String?> = MutableStateFlow("preview-channel-id-12345")
        override val namedUserId: StateFlow<String?> = MutableStateFlow("user id")
        override val isOptedInForPushes: StateFlow<Boolean> = MutableStateFlow(false)
        override var unreadMessageCount: Flow<List<Message>> = flowOf(emptyList())
        override fun copyToClipboard(context: android.content.Context) { }
        override fun togglePushStatus() { }
    }

    HomeScreen(
        onNavigate = {},
        viewModel = homeViewModel
    )
}
