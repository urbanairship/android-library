package com.urbanairship.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.urbanairship.google.PlayServicesUtils.handleAnyPlayServicesError
import com.urbanairship.google.PlayServicesUtils.isGooglePlayStoreAvailable
import com.urbanairship.sample.debug.DebugScreen
import com.urbanairship.sample.home.HomeScreen
import com.urbanairship.sample.inbox.InboxScreen
import com.urbanairship.sample.preferencecenter.PreferenceCenterScreen
import AirshipTheme
import kotlinx.serialization.Serializable

/**
 * Main application entry point.
 */
class MainActivity : AppCompatActivity() {
    private var navController: NavController? = null

    @Serializable
    data object Home : NavKey, BottomNavItem {
        override val icon: ImageVector = Icons.Filled.Home
        override val label: String = "Home"
    }

    @Serializable
    data object Message : NavKey, BottomNavItem {
        override val icon: ImageVector = Icons.Filled.MailOutline
        override val label: String = "Message Center"
    }

    @Serializable
    data object PreferenceCenter : NavKey, BottomNavItem {
        override val icon: ImageVector = Icons.Filled.Notifications
        override val label: String = "Preference"
    }

    @Serializable
    data object Settings : NavKey, BottomNavItem {
        override val icon: ImageVector = Icons.Filled.Settings
        override val label: String = "Settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        this.enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            enableEdgeToEdge()
            val context = LocalContext.current
            val navItems = listOf(MainActivity.Home, MainActivity.Message, PreferenceCenter,
                MainActivity.Settings)
            val topLevelBackStack = remember { TopLevelBackStack<NavKey>(Home) }

            AirshipTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            navItems.forEach { item ->
                                val selected = topLevelBackStack.topLevelKey == item
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { topLevelBackStack.switchTopLevel(item) },
                                    label = { Text(text = item.label) },
                                    alwaysShowLabel = false,
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label
                                        )
                                    }
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    val screenModifier = Modifier
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .fillMaxSize()
                        NavDisplay(
                            backStack = topLevelBackStack.backStack,
                            onBack = { topLevelBackStack.removeLast() },
                            entryProvider = { key ->
                                when (key) {
                                    is Home -> NavEntry(key) {
                                        HomeScreen(backStack = topLevelBackStack, modifier = screenModifier)
                                    }
                                    is Message -> NavEntry(key) {
                                        InboxScreen(modifier = screenModifier, onMessageSelected = {})
                                    }
                                    is PreferenceCenter -> NavEntry(key) {
                                        PreferenceCenterScreen(modifier = screenModifier, context = context)
                                    }
                                    is Settings -> NavEntry(key) {
                                        DebugScreen(modifier= screenModifier)
                                    }
                                    else -> {
                                        error("Unknown route: $key")
                                    }
                                }
                            })
                }
            }
        }
    }

    interface BottomNavItem {
        val label: String
        val icon: ImageVector
    }

    class TopLevelBackStack<T : NavKey>(private val startKey: T) {

        internal var topLevelBackStacks: HashMap<T, SnapshotStateList<T>> = hashMapOf(
            startKey to mutableStateListOf(startKey)
        )

        var topLevelKey by mutableStateOf(startKey)
            private set

        val backStack = mutableStateListOf<T>(startKey)

        private fun updateBackStack() {
            backStack.clear()
            val currentStack = topLevelBackStacks[topLevelKey] ?: emptyList()

            if (topLevelKey == startKey) {
                backStack.addAll(currentStack)
            } else {
                val startStack = topLevelBackStacks[startKey] ?: emptyList()
                backStack.addAll(startStack + currentStack)
            }
        }

        internal fun switchTopLevel(key: T) {
            if (topLevelBackStacks[key] == null) {
                topLevelBackStacks[key] = mutableStateListOf(key)
            }
            topLevelKey = key
            updateBackStack()
        }

        fun removeLast() {
            val currentStack = topLevelBackStacks[topLevelKey] ?: return

            if (currentStack.size > 1) {
                currentStack.removeLastOrNull()
            } else if (topLevelKey != startKey) {
                topLevelKey = startKey
            }
            updateBackStack()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navController!!.handleDeepLink(intent)
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
}
