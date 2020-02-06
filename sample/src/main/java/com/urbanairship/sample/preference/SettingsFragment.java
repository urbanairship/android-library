/* Copyright Airship and Contributors */

package com.urbanairship.sample.preference;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.sample.R;

import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

/**
 * Settings fragment.
 *
 * Wraps the PreferenceFragment.
 */
public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                                     .replace(R.id.preference_placeholder, new PreferenceFragment())
                                     .commitNow();
        }

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view));
    }

    /**
     * PreferenceFragmentCompat
     */
    public static class PreferenceFragment extends PreferenceFragmentCompat {

        private static final String TAGS_KEY = "tags";

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            View view = getView();
            if (view != null && TAGS_KEY.equals(preference.getKey())) {
                Navigation.findNavController(view).navigate(R.id.tagsFragment);
            }

            return super.onPreferenceTreeClick(preference);
        }

    }

}
