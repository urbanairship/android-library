package com.urbanairship.devapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.scene.rememberSceneSetupNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.urbanairship.google.PlayServicesUtils.handleAnyPlayServicesError
import com.urbanairship.google.PlayServicesUtils.isGooglePlayStoreAvailable
import com.urbanairship.devapp.debug.DebugScreen
import com.urbanairship.devapp.home.HomeScreen
import com.urbanairship.devapp.inbox.InboxScreen
import com.urbanairship.devapp.preferencecenter.PreferenceCenterScreen
import AirshipTheme
import kotlinx.serialization.Serializable

/**
 * Main application entry point.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        this.enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val appRouter: AppRouterViewModel = viewModel(
                modelClass = AppRouterViewModel::class.java,
                factory = AppRouterViewModel.factory()
            )

            val activeTab = appRouter.selectedTopLevel.collectAsState().value
            val backstack = appRouter.activeBackStack.collectAsState().value

            AirshipTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            AppRouterViewModel.TopLevelDestination.entries.forEach { item ->
                                val selected = activeTab == item

                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { appRouter.navigate(item) },
                                    label = { Text(text = item.title()) },
                                    alwaysShowLabel = false,
                                    icon = {
                                        Icon(
                                            imageVector = item.icon(),
                                            contentDescription = item.title()
                                        )
                                    }
                                )

                            }
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(bottom = innerPadding.calculateBottomPadding())
                            .fillMaxSize()
                    ) {
                        NavDisplay(
                            backStack = backstack,
                            onBack = { appRouter.pop() },
                            entryDecorators = listOf(
                                // Add the default decorators for managing scenes and saving state
                                rememberSceneSetupNavEntryDecorator(),
                                rememberSavedStateNavEntryDecorator(),
                                // Then add the view model store decorator
                                rememberViewModelStoreNavEntryDecorator()
                            ),
                            entryProvider = { key ->
                                appRouter.navigationEntry(key)
                            })
                    }

                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    public override fun onResume() {
        super.onResume()

        // Handle any Google Play services errors
        if (isGooglePlayStoreAvailable(this)) {
            handleAnyPlayServicesError(this)
        }
    }

    public override fun onPause() {
        super.onPause()
    }

    fun AppRouterViewModel.TopLevelDestination.title(): String {
        return when(this) {
            AppRouterViewModel.TopLevelDestination.HOME -> "Home"
            AppRouterViewModel.TopLevelDestination.MESSAGE -> "Messages"
            AppRouterViewModel.TopLevelDestination.PREFERENCE_CENTER -> "Preferences"
            AppRouterViewModel.TopLevelDestination.SETTINGS -> "Settings"
        }
    }

    fun AppRouterViewModel.TopLevelDestination.icon(): ImageVector {
        return when(this) {
            AppRouterViewModel.TopLevelDestination.HOME -> Icons.Filled.Home
            AppRouterViewModel.TopLevelDestination.MESSAGE -> Icons.Filled.MailOutline
            AppRouterViewModel.TopLevelDestination.PREFERENCE_CENTER -> Icons.Filled.Notifications
            AppRouterViewModel.TopLevelDestination.SETTINGS -> Icons.Filled.Settings
        }
    }
}
