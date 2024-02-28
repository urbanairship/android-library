/* Copyright Airship and Contributors */

package com.urbanairship.sample.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.urbanairship.UAirship;

import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.sample.R;
import com.urbanairship.sample.databinding.FragmentHomeBinding;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

/**
 * Fragment that displays the channel ID.
 */
public class HomeFragment extends Fragment {
    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        HomeViewModel viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        FragmentHomeBinding binding = FragmentHomeBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(viewModel);

        bindThomasButtons(binding);
        bindIAAButtons(binding);

        return binding.getRoot();
    }

    public void bindThomasButtons(@NonNull FragmentHomeBinding binding) {
        binding.tBanner.setOnClickListener(v -> {
            trackAndToast("as_t_banner");
        });

        binding.tModal.setOnClickListener(v -> {
            trackAndToast("as_t_modal");
        });

        binding.tFullscreen.setOnClickListener(v -> {
            trackAndToast("as_t_fullscreen");
        });

        binding.tStory.setOnClickListener(v -> {
            trackAndToast("as_t_story");
        });

        binding.tSurvey.setOnClickListener(v -> {
            trackAndToast("as_t_survey");
        });
    }

    public void trackAndToast(String eventName) {
        CustomEvent event = new CustomEvent.Builder(eventName)
                .build();
        event.track();

        if (getContext() != null) {
            Toast.makeText(getContext(), "Custom event tracked: " + eventName, Toast.LENGTH_SHORT).show();
        }
    }

    public void bindIAAButtons(@NonNull FragmentHomeBinding binding) {
        binding.iaaBanner.setOnClickListener(v -> {
            trackAndToast("as_iaa_banner");
        });

        binding.iaaModal.setOnClickListener(v -> {
            trackAndToast("as_iaa_modal");
        });

        binding.iaaFullscreen.setOnClickListener(v -> {
            trackAndToast("as_iaa_fullscreen");
        });

        binding.iaaHtml.setOnClickListener(v -> {
            trackAndToast("as_iaa_html");
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view));
    }
}
