/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.Navigation
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.urbanairship.debug.R
import com.urbanairship.debug.deviceinfo.preferences.InAppAutomationDisplayIntervalPreference
import com.urbanairship.debug.deviceinfo.preferences.InAppAutomationDisplayIntervalPreferenceDialogFragment
import com.urbanairship.debug.extensions.setupToolbarWithNavController

/**
 * Settings fragment.
 *
 * Wraps the PreferenceFragment.
 */
class DeviceInfoFragment : androidx.fragment.app.Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.ua_fragment_device_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.preference_placeholder, PreferenceFragment())
                    .commitNow()
        }

        setupToolbarWithNavController(R.id.toolbar)
    }

    /**
     * PreferenceFragmentCompat
     */
    class PreferenceFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.ua_device_info)
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            val view = view

            view?.let {
                when (preference.key) {
                    TAGS_KEY -> Navigation.findNavController(it).navigate(R.id.deviceInfoTagsFragment)
                    TAG_GROUPS_KEY -> Navigation.findNavController(it).navigate(R.id.deviceInfoTagGroupsFragment)
                    CHANNEL_ATTRIBUTES_KEY -> Navigation.findNavController(it).navigate(R.id.deviceChannelAttributesFragment)
                    CONTACT_ATTRIBUTES_KEY -> Navigation.findNavController(it).navigate(R.id.deviceContactAttributesFragment)
                    ASSOCIATED_IDENTIFIERS_KEY -> Navigation.findNavController(it).navigate(R.id.deviceInfoAssociatedIdentifiersFragment)
                }
            }

            return super.onPreferenceTreeClick(preference)
        }

        override fun onDisplayPreferenceDialog(preference: Preference?) {
            if (preference is InAppAutomationDisplayIntervalPreference) {
                val dialogFragment = InAppAutomationDisplayIntervalPreferenceDialogFragment.newInstance(preference.key)
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(parentFragmentManager, DISPLAY_INTERVAL_TAG)
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }

        companion object {
            private const val TAGS_KEY = "tags"
            private const val TAG_GROUPS_KEY = "tagGroups"
            private const val CHANNEL_ATTRIBUTES_KEY = "channel_attributes"
            private const val CONTACT_ATTRIBUTES_KEY = "contact_attributes"
            private const val ASSOCIATED_IDENTIFIERS_KEY = "associated_identifiers"
            private const val DISPLAY_INTERVAL_TAG = "DISPLAY_INTERVAL_TAG"
        }
    }
}
