package com.urbanairship.sample.preferencecenter

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.urbanairship.preferencecenter.ui.PreferenceCenterFragment
import com.urbanairship.sample.R

/**
 * Preference Center fragment that supports navigation and maintains its own toolbar.
 */
internal class PrefCenterFragment : Fragment(R.layout.fragment_pref_center) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar: Toolbar = view.findViewById(R.id.toolbar)

        val prefCenterId = arguments?.getString("pref_center_id") ?: "app_default"

        if (savedInstanceState == null) {
            val fragment = PreferenceCenterFragment.create(prefCenterId).apply {
                setOnDisplayPreferenceCenterListener(OnDisplayListener(this, toolbar))
            }

            childFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, fragment)
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
