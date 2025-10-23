/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.channel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Airship
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Composable
internal fun ChannelScreen(
    viewModel: ChannelViewModel = viewModel<DefaultChannelViewModel>(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {

    DebugScreen(
        title = stringResource(id = ChannelInfoScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel = viewModel, onNavigate = onNavigate)
    }
}

@Composable
private fun ScreenContent(viewModel: ChannelViewModel, onNavigate: (String) -> Unit = {}) {
    val channelId = viewModel.channelId.collectAsState(null).value

    val context = LocalContext.current

    Column {
        ListItem(
            modifier = Modifier.clickable { viewModel.copyChannelId(context, channelId) },
            headlineContent = {
                Text("Channel ID", fontWeight = FontWeight.Medium)
            },
            supportingContent = {
                Text(text = channelId ?: "")
            })
        HorizontalDivider()

        RowItem(
            modifier = Modifier.clickable { onNavigate(ChannelInfoScreens.Tags.route) },
            title = "Tags",
            accessory = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "display") }
        )

        RowItem(
            modifier = Modifier.clickable { onNavigate(ChannelInfoScreens.TagGroups.route) },
            title = "Tag Groups",
            accessory = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "display") }
        )

        RowItem(
            modifier = Modifier.clickable { onNavigate(ChannelInfoScreens.Attributes.route) },
            title = "Attributes",
            accessory = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "display") }
        )

        RowItem(
            modifier = Modifier.clickable { onNavigate(ChannelInfoScreens.SubscriptionLists.route) },
            title = "Subscription Lists",
            accessory = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "display") }
        )
    }
}

internal interface ChannelViewModel {
    val channelId: Flow<String?>
    fun copyChannelId(context: Context, value: String?)

    companion object {
        fun forPreview(): ChannelViewModel {
            return object : ChannelViewModel {
                override val channelId: Flow<String?> = flowOf(UUID.randomUUID().toString())

                override fun copyChannelId(context: Context, value: String?) { }
            }
        }
    }
}

internal class DefaultChannelViewModel: ChannelViewModel, ViewModel() {
    override var channelId = Airship
        .channel
        .channelIdFlow
        .map { it?.uppercase() }

    override fun copyChannelId(context: Context, value: String?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("channel id", value))

        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT)
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        ChannelScreen(
            viewModel = ChannelViewModel.forPreview()
        )
    }
}
