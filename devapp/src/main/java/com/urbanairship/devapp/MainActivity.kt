package com.urbanairship.devapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.urbanairship.automation.compose.AirshipBannerHost
import com.urbanairship.devapp.AppRouterViewModel.TopLevelDestination
import com.urbanairship.devapp.tv.TvMainActivity
import com.urbanairship.devapp.tv.isTelevision
import com.urbanairship.google.PlayServicesUtils.handleAnyPlayServicesError
import com.urbanairship.google.PlayServicesUtils.isGooglePlayStoreAvailable
import AirshipTheme

/**
 * Main application entry point.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (isTelevision()) {
            super.onCreate(savedInstanceState)
            startActivity(
                Intent(intent)
                    .setClass(this, TvMainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
            return
        }

        this.enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val appRouter: AppRouterViewModel = viewModel(
                modelClass = AppRouterViewModel::class.java,
                factory = AppRouterViewModel.factory()
            )

            val backstack = appRouter.activeBackStack.collectAsState().value
            val selectedTopLevel = appRouter.selectedTopLevel.collectAsState().value

            AirshipTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            TopLevelDestination.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = selectedTopLevel == item,
                                    onClick = { appRouter.navigate(item) },
                                    label = { Text(text = item.title) },
                                    icon = { Icon(painter = painterResource(item.icon), contentDescription = null) },
                                    alwaysShowLabel = true,
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    ) {
                        NavDisplay(
                            modifier = Modifier.fillMaxSize(),
                            backStack = backstack,
                            onBack = appRouter::pop,
                            entryDecorators = listOf(
                                // Add the default decorators for managing scenes and saving state
                                rememberSaveableStateHolderNavEntryDecorator(),
                                // Then add the view model store decorator
                                rememberViewModelStoreNavEntryDecorator()
                            ),
                            entryProvider = appRouter::navigationEntry
                        )

                        // Host for Thomas banners, displayed above the app content,
                        // within the inset-padded content area.
                        AirshipBannerHost(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            DeeplinkEffect(this, appRouter)
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
