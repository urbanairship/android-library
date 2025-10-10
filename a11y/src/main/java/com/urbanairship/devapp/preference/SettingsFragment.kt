/* Copyright Airship and Contributors */
package com.urbanairship.devapp.preference

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.urbanairship.preferencecenter.ui.PreferenceCenterFragment
import com.urbanairship.devapp.MainActivity.APP_BAR_CONFIGURATION
import com.urbanairship.devapp.R

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setupWithNavController(findNavController(), APP_BAR_CONFIGURATION)

        val prefCenterId = arguments?.getString("pref_center_id") ?: "app_default"

        if (savedInstanceState == null) {
            val fragment = PreferenceCenterFragment.create(prefCenterId).apply {
                setOnDisplayPreferenceCenterListener(OnDisplayListener(this, toolbar))
            }

            childFragmentManager.commitNow {
                setReorderingAllowed(true)
                replace(R.id.fragment_container_view, fragment)
            }
        } else {
            val fragment = (childFragmentManager.findFragmentById(R.id.fragment_container_view) as PreferenceCenterFragment)
            // Reset our listener on the restored fragment.
            fragment.setOnDisplayPreferenceCenterListener(OnDisplayListener(fragment, toolbar))
        }
    }

    private class OnDisplayListener(
        private val fragment: PreferenceCenterFragment,
        private val toolbar: Toolbar
    ) : PreferenceCenterFragment.OnDisplayPreferenceCenterListener {
        override fun onDisplayPreferenceCenter(title: String?, description: String?): Boolean {
            toolbar.title = title
            if (description != null) {
                fragment.showHeaderItem(title = null, description = description)
            }
            return true
        }
    }
}
