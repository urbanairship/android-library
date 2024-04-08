package com.urbanairship.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.urbanairship.debug.ui.TopLevelScreens
import com.urbanairship.debug.ui.components.TopAppBar
import com.urbanairship.debug.ui.home.DebugNavHost
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import com.urbanairship.debug2.R
import kotlinx.coroutines.flow.map

public class DebugFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AirshipDebugTheme {
                    val navController = rememberNavController()

                    val navBarTitleRes = navController.currentBackStackEntryFlow
                        .map { TopLevelScreens.forRoute(it.destination.route) }
                        .map { it?.titleRes ?: R.string.ua_debug_label}
                        .collectAsStateWithLifecycle(initialValue = R.string.ua_debug_label)

                    Scaffold(
                        topBar = {
                            AirshipDebug.TopAppBar(
                                title = stringResource(id = navBarTitleRes.value)
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { padding ->
                        DebugNavHost(
                            navController = navController,
                            modifier = Modifier.fillMaxSize()
                                .padding(padding)
                        )
                    }
                }
            }
        }
    }
}