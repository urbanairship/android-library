package com.urbanairship.devapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.urbanairship.devapp.AppRouterViewModel.TopLevelDestination
import com.urbanairship.google.PlayServicesUtils.handleAnyPlayServicesError
import com.urbanairship.google.PlayServicesUtils.isGooglePlayStoreAvailable
import AirshipTheme

/**
 * Main application entry point.
 */
class MainActivity : AppCompatActivity() {

    fun TopLevelDestination.title(): String = when(this) {
        TopLevelDestination.Home -> "Home"
        is TopLevelDestination.Message -> "Messages"
        TopLevelDestination.PreferenceCenter -> "Preferences"
        TopLevelDestination.Settings -> "Settings"
    }

    fun TopLevelDestination.icon(): ImageVector = when(this) {
        TopLevelDestination.Home -> Icons.Filled.Home
        is TopLevelDestination.Message -> Icons.Filled.MailOutline
        TopLevelDestination.PreferenceCenter -> Icons.Filled.Notifications
        TopLevelDestination.Settings -> Icons.Filled.Settings
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        this.enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val appRouter: AppRouterViewModel = viewModel(
                modelClass = AppRouterViewModel::class.java,
                factory = AppRouterViewModel.factory()
            )

            DeeplinkManager.shared.setAppRouter(appRouter)

            val activeTab = appRouter.selectedTopLevel.collectAsState().value
            val backstack = appRouter.activeBackStack.collectAsState().value

            AirshipTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            TopLevelDestination.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = activeTab == item,
                                    onClick = { appRouter.navigate(item) },
                                    label = { Text(text = item.title()) },
                                    icon = { Icon(item.icon(), contentDescription = null) },
                                    alwaysShowLabel = true,
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    NavDisplay(
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                        backStack = backstack,
                        onBack = { appRouter.pop() },
                        entryDecorators = listOf(
                            // Add the default decorators for managing scenes and saving state
                            rememberSaveableStateHolderNavEntryDecorator(),
                            // Then add the view model store decorator
                            rememberViewModelStoreNavEntryDecorator()
                        ),
                        entryProvider = appRouter::navigationEntry
                    )
                }
            }

            // Listen for new intents that may contain deep links
            DisposableEffect(appRouter) {
                DeeplinkManager.shared.handleDeeplink(intent)

                val listener = Consumer<Intent> {
                    DeeplinkManager.shared.handleDeeplink(it)
                }

                addOnNewIntentListener(listener)
                onDispose { removeOnNewIntentListener(listener) }
            }
        }
    }

    public override fun onResume() {
        super.onResume()

        // Handle any Google Play services errors
        if (isGooglePlayStoreAvailable(this)) {
            handleAnyPlayServicesError(this)
        }
    }
}
