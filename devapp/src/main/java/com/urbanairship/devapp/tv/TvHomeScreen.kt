package com.urbanairship.devapp.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.urbanairship.automation.compose.AirshipEmbeddedCarousel
import com.urbanairship.automation.compose.AirshipEmbeddedCarouselDefaults
import com.urbanairship.automation.compose.rememberAirshipEmbeddedCarouselState
import com.urbanairship.devapp.AppRouterViewModel.TopLevelDestination
import com.urbanairship.devapp.Destination
import com.urbanairship.devapp.R
import com.urbanairship.devapp.home.DefaultHomeViewModel
import com.urbanairship.devapp.home.HomeViewModel
import com.urbanairship.devapp.home.QuickAccess

@Composable
internal fun TvHomeScreen(
    onNavigate: (Destination) -> Unit,
    viewModel: HomeViewModel = viewModel<DefaultHomeViewModel>()
) {
    val channelId by viewModel.channelId.collectAsState()
    val namedUserId by viewModel.namedUserId.collectAsState()
    val isOptedIn by viewModel.isOptedInForPushes.collectAsState()
    val unread by viewModel.unreadMessageCount.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val carouselState = rememberAirshipEmbeddedCarouselState(embeddedId = "carousel")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(text = "Home", style = MaterialTheme.typography.headlineMedium)
        }

        if (carouselState.pageCount > 0) item {
            AirshipEmbeddedCarousel(
                state = carouselState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .focusGroup(),
                previousArrow = AirshipEmbeddedCarouselDefaults.previousArrow(),
                nextArrow = AirshipEmbeddedCarouselDefaults.nextArrow(),
                indicator = AirshipEmbeddedCarouselDefaults.dotsIndicator,
                placeholder = null
            )
        }

        item {
            HomeItem(
                title = stringResource(R.string.enable_notifications),
                subtitle = if (isOptedIn) "Opted in" else "Opted out",
                onClick = viewModel::togglePushStatus,
                trailingContent = { Switch(checked = isOptedIn, onCheckedChange = null) }
            )
        }

        item {
            HomeItem(
                title = stringResource(R.string.channel_id),
                subtitle = channelId ?: "Not Created",
                onClick = { viewModel.copyToClipboard(context) }
            )
        }

        item {
            HomeItem(
                title = stringResource(R.string.named_user),
                subtitle = namedUserId ?: "Not Set",
                onClick = { onNavigate(QuickAccess.NamedUserScreen) }
            )
        }

        item {
            HomeItem(
                title = TopLevelDestination.Message().title,
                subtitle = pluralStringResource(R.plurals.mc_indicator_text, unread.size, unread.size),
                onClick = { onNavigate(TopLevelDestination.Message()) }
            )
        }

        item {
            HomeItem(
                title = stringResource(R.string.thomas_layouts),
                subtitle = "Select to preview layouts",
                onClick = { onNavigate(QuickAccess.ThomasLayoutsHome) }
            )
        }
    }
}

@Composable
private fun HomeItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = trailingContent
    )
}
