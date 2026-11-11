package com.urbanairship.devapp.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import com.urbanairship.automation.compose.AirshipBannerHost
import com.urbanairship.devapp.AppRouterViewModel
import com.urbanairship.devapp.AppRouterViewModel.TopLevelDestination
import com.urbanairship.devapp.DeeplinkEffect
import com.urbanairship.devapp.Destination
import com.urbanairship.devapp.debug.DebugScreen
import com.urbanairship.devapp.preferencecenter.PreferenceCenterScreen
import com.urbanairship.devapp.ui.theme.AirshipTvTheme
import com.urbanairship.messagecenter.compose.ui.rememberMessageCenterState
import com.urbanairship.preferencecenter.compose.ui.PreferenceCenterDefaults
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Entry point on Google TV / Android TV.
 */
class TvMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        super.onCreate(savedInstanceState)

        setContent {
            val appRouter: AppRouterViewModel = viewModel(
                modelClass = AppRouterViewModel::class.java,
                factory = AppRouterViewModel.factory()
            )

            AirshipTvTheme {
                TvShell(appRouter)
            }

            DeeplinkEffect(this, appRouter)
        }
    }
}

internal fun Context.isTelevision(): Boolean =
    getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

@Composable
private fun TvShell(appRouter: AppRouterViewModel) {
    val backstack = appRouter.activeBackStack.collectAsState().value
    val selectedTopLevel = appRouter.selectedTopLevel.collectAsState().value

    val itemFocus = remember { TopLevelDestination.entries.map { FocusRequester() } }
    val drawerFocus = itemFocus[
        TopLevelDestination.entries.indexOfFirst { it::class == selectedTopLevel::class }.coerceAtLeast(0)
    ]
    val focusManager = LocalFocusManager.current
    var drawerHasFocus by remember { mutableStateOf(false) }
    val pageFocus = remember { PageFocus() }

    LaunchedEffect(Unit) { drawerFocus.requestFocus() }

    BackHandler(enabled = !drawerHasFocus && backstack.size < 2) {
        drawerFocus.requestFocus()
    }

    ModalNavigationDrawer(
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp)
                    .selectableGroup()
                    .onFocusChanged { drawerHasFocus = it.hasFocus },
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                TopLevelDestination.entries.forEachIndexed { index, item ->
                    NavigationDrawerItem(
                        modifier = Modifier.focusRequester(itemFocus[index]),
                        selected = selectedTopLevel::class == item::class,
                        onClick = {
                            appRouter.navigate(item)
                            pageFocus.pending = item
                        },
                        leadingContent = {
                            Icon(painter = painterResource(item.icon), contentDescription = null)
                        }
                    ) {
                        Text(item.title)
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 80.dp)
                .onKeyEvent { event ->
                    if (event.key != Key.DirectionLeft || event.type != KeyEventType.KeyDown) {
                        return@onKeyEvent false
                    }
                    focusManager.moveFocus(FocusDirection.Left)
                    if (drawerHasFocus) drawerFocus.requestFocus()
                    true
                }
        ) {
            NavDisplay(
                modifier = Modifier.fillMaxSize(),
                backStack = backstack,
                onBack = appRouter::pop,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = { destination: Any -> tvEntry(destination, appRouter, pageFocus) }
            )

            AirshipBannerHost(modifier = Modifier.fillMaxSize())
        }
    }
}

/** The top-level destination that should take focus once its page can accept it. */
private class PageFocus {
    var pending by mutableStateOf<TopLevelDestination?>(null)
}

@Composable
private fun TvPage(
    destination: TopLevelDestination,
    pageFocus: PageFocus,
    content: @Composable () -> Unit
) {
    val requester = remember { FocusRequester() }
    val pending = pageFocus.pending

    LaunchedEffect(pending) {
        if (pending == null || pending::class != destination::class) return@LaunchedEffect
        repeat(40) {
            if (requester.requestFocus()) {
                pageFocus.pending = null
                return@LaunchedEffect
            }
            delay(50.milliseconds)
        }
        pageFocus.pending = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(requester)
            .focusGroup()
    ) {
        content()
    }
}

private fun tvEntry(destination: Any, appRouter: AppRouterViewModel, pageFocus: PageFocus): NavEntry<Any> {
    val onNavigate: (Destination) -> Unit = appRouter::navigate
    return when (destination) {
        is TopLevelDestination.Home -> NavEntry(destination) {
            TvPage(destination, pageFocus) { TvHomeScreen(onNavigate = onNavigate) }
        }
        is TopLevelDestination.Message -> NavEntry(destination) {
            TvPage(destination, pageFocus) {
                TvMessageCenterScreen(state = rememberMessageCenterState(messageId = destination.messageId))
            }
        }
        is TopLevelDestination.PreferenceCenter -> NavEntry(destination) {
            TvPage(destination, pageFocus) {
                PreferenceCenterScreen(
                identifier = "app_default",
                topBar = { title, _ ->
                    PreferenceCenterDefaults.topBar(
                        title = title,
                        navIcon = null,
                        navIconDescription = null,
                        onNavigateUp = { /* No-op */ }
                    )
                }
            )
            }
        }
        is TopLevelDestination.Settings -> NavEntry(destination) {
            TvPage(destination, pageFocus) { DebugScreen() }
        }
        else -> appRouter.navigationEntry(destination)
    }
}
