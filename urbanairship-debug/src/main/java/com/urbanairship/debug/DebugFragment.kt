package com.urbanairship.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RestrictTo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation.findNavController
import androidx.navigation.compose.rememberNavController
import com.urbanairship.debug.ui.components.LocalIgnoreBottomPadding
import com.urbanairship.debug.ui.home.DebugNavHost
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DebugFragment : Fragment() {

    private val ignoreBottomPadding: Boolean
        get() = arguments?.getBoolean(ARG_IGNORE_BOTTOM_PADDING) == true

    private val showNavIconOnDebugHomeScreen: Boolean
        get() = arguments?.getBoolean(ARG_SHOW_NAV_ICON_ON_DEBUG_HOME_SCREEN) == true

    /** The NavController for the sample app's nav graph. */
    private val sampleNavController: NavController by lazy {
        findNavController(requireView())
    }

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
                        // Nav controller for the debug nav graph (using compose navigation).
                        val debugNavController = rememberNavController()
                        DebugNavHost(
                            navController = debugNavController,
                            showNavIconOnDebugHomeScreen = showNavIconOnDebugHomeScreen,
                            onNavigateUpFromHomeScreen = { sampleNavController.navigateUp() }
                        )
                    }
                }
            }
        }
    }

    public companion object {
        public const val ARG_IGNORE_BOTTOM_PADDING: String = "ignoreBottomPadding"
        public const val ARG_SHOW_NAV_ICON_ON_DEBUG_HOME_SCREEN: String = "showNavIconOnDebugHomeScreen"

        public fun newInstance(
            ignoreBottomPadding: Boolean = false,
            showNavIconOnDebugHomeScreen: Boolean = false
        ): DebugFragment {
            return DebugFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IGNORE_BOTTOM_PADDING, ignoreBottomPadding)
                    putBoolean(ARG_SHOW_NAV_ICON_ON_DEBUG_HOME_SCREEN, showNavIconOnDebugHomeScreen)
                }
            }
        }
    }
}
