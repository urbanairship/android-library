package com.urbanairship.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.fragment.app.Fragment
import androidx.navigation.compose.rememberNavController
import com.urbanairship.debug.ui.home.DebugNavHost
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

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

                    DebugNavHost(navController = navController)
                }
            }
        }
    }
}
