package com.urbanairship.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.fragment.app.Fragment
import androidx.navigation.compose.rememberNavController
import com.urbanairship.debug.ui.home.DebugNavHost
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

internal val LocalIgnoreBottomPadding: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

public class DebugFragment : Fragment() {

    private val ignoreBottomPadding: Boolean
        get() = arguments?.getBoolean(ARG_IGNORE_BOTTOM_PADDING) ?: false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // Provide the ignoreBottomPadding value to the composition.
                // This lets us control whether we apply or ignore bottom padding in the shared
                // DebugScreen composable.
                CompositionLocalProvider(LocalIgnoreBottomPadding provides ignoreBottomPadding) {
                    AirshipDebugTheme {
                        val navController = rememberNavController()
                        DebugNavHost(navController = navController)
                    }
                }
            }
        }
    }

    public companion object {
        public const val ARG_IGNORE_BOTTOM_PADDING: String = "ignoreBottomPadding"

        @JvmStatic
        public fun newInstance(
            ignoreBottomPadding: Boolean = false
        ): DebugFragment {
            return DebugFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IGNORE_BOTTOM_PADDING, ignoreBottomPadding)
                }
            }
        }
    }
}
