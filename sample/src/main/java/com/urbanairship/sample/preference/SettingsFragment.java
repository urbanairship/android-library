/* Copyright Airship and Contributors */

package com.urbanairship.sample.preference;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.UALog;
import com.urbanairship.sample.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import static com.urbanairship.sample.MainActivity.TOP_LEVEL_DESTINATIONS;

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
        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view),
                new AppBarConfiguration.Builder(TOP_LEVEL_DESTINATIONS).build());
    }

    /**
     * PreferenceFragmentCompat
     */
    public static class PreferenceFragment extends PreferenceFragmentCompat {

        private static final String TAGS_KEY = "tags";
        private static final String DEBUG_KEY = "debug";

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public boolean onPreferenceTreeClick(@NonNull Preference preference) {
            View view = requireView();

            switch (preference.getKey()) {
                case TAGS_KEY:
                    Navigation.findNavController(view).navigate(R.id.tagsFragment);
                    return true;
                case DEBUG_KEY:
                    Navigation.findNavController(view).navigate(R.id.debug);
                    return true;
            }

            return super.onPreferenceTreeClick(preference);
        }

    }

}
