/* Copyright Airship and Contributors */

package com.urbanairship.sample.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ClipboardAction;
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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        HomeViewModel viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        FragmentHomeBinding binding = FragmentHomeBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(viewModel);

        binding.channelId.setOnClickListener(v -> {
            ActionRunRequest.createRequest(ClipboardAction.DEFAULT_REGISTRY_NAME)
                            .setValue(binding.channelId.getText())
                            .run((arguments, result) -> {
                                Toast.makeText(getContext(), getString(R.string.toast_channel_clipboard), Toast.LENGTH_SHORT)
                                     .show();
                            });
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view));
    }

}
