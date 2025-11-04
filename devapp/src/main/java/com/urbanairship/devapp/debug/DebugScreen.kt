package com.urbanairship.devapp.debug

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavController.OnDestinationChangedListener
import androidx.navigation.NavDestination
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.SavedState
import com.urbanairship.UALog
import com.urbanairship.debug.ui.home.DebugNavHost
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    DisposableEffect(Unit) {
        val copy = RouteStore.savedStack()

        if (!copy.isEmpty()) {
            scope.launch {
                delay(10.milliseconds)
                copy.forEach(navController::navigate)
            }
        }

        val listener = object : OnDestinationChangedListener {
            override fun onDestinationChanged(
                controller: NavController, destination: NavDestination, arguments: SavedState?
            ) {
                RouteStore.onNewEntry(destination.route)
            }
        }

        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    Scaffold(modifier = modifier) { _ ->
        DebugNavHost(
            navController = navController,
            onNavigateUpFromHomeScreen = {}
        )
    }
}

private class RouteStore {
    companion object {
        private var stack: MutableList<String> = mutableListOf()
        fun onNewEntry(entry: String?) {
            if (entry == null) {
                return
            }

            val index = stack.indexOf(entry)
            if (index >= 0) {
                stack = stack.subList(index, stack.size - 1)
            } else {
                stack.add(entry)
            }
        }

        fun savedStack(clear: Boolean = true): List<String> {
            val result = stack.toList()
            if (clear) {
                stack.clear()
            }
            return result
        }
    }
}
