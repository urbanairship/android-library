/* Copyright Airship and Contributors */

package com.urbanairship.sample.preference;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.sample.R;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import static java.util.Objects.requireNonNull;

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
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            setupLocaleOverridePreference();
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            View view = getView();
            if (view != null && TAGS_KEY.equals(preference.getKey())) {
                Navigation.findNavController(view).navigate(R.id.tagsFragment);
            }

            return super.onPreferenceTreeClick(preference);
        }

        private void setupLocaleOverridePreference() {
            ListPreference localeOverride = requireNonNull(findPreference("locale_override"));

            localeOverride.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue == "default") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                    UAirship.shared().setLocaleOverride(Locale.forLanguageTag((String) newValue));
                } else {
                    LocaleListCompat override = LocaleListCompat.forLanguageTags((String) newValue);
                    AppCompatDelegate.setApplicationLocales(override);
                    UAirship.shared().setLocaleOverride(Locale.forLanguageTag((String) newValue));
                }
                return true;
            });
        }

    }

}
