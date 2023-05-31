/* Copyright Airship and Contributors */

package com.urbanairship.sample.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ClipboardAction;
import com.urbanairship.json.JsonMap;
import com.urbanairship.liveupdate.LiveUpdateManager;
import com.urbanairship.sample.R;
import com.urbanairship.sample.databinding.FragmentHomeBinding;

import java.util.concurrent.atomic.AtomicInteger;

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

        setupLiveUpdateTestButtons(binding);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view));
    }

    // TODO: Replace with something less hacky when backend is ready to send real Live Updates.
    //    Should live update stuff even be on the home screen? Could be in settings instead...
    private void setupLiveUpdateTestButtons(FragmentHomeBinding binding) {
        AtomicInteger score1 = new AtomicInteger();
        AtomicInteger score2 = new AtomicInteger();

        // Start button
        binding.startLiveUpdate.setOnClickListener(v -> {
            JsonMap content = JsonMap.newBuilder()
                                     .put("team_one_score", 0)
                                     .put("team_two_score", 0)
                                     .put("status_update", "Match start!")
                                     .build();

            LiveUpdateManager.shared().start("foxes-tigers", "sports", content);
        });

        // +1 Foxes button
        binding.updateLiveUpdate1.setOnClickListener(v -> {
            JsonMap content = JsonMap.newBuilder()
                                     .put("team_one_score", score1.getAndIncrement())
                                     .put("team_two_score", score2.get())
                                     .put("status_update", "Foxes score!")
                                     .build();

            LiveUpdateManager.shared().update("foxes-tigers", content);
        });

        // +1 Tigers button
        binding.updateLiveUpdate2.setOnClickListener(v -> {
            JsonMap content = JsonMap.newBuilder()
                                     .put("team_one_score", score1.get())
                                     .put("team_two_score", score2.getAndIncrement())
                                     .put("status_update", "Tigers score!")
                                     .build();

            LiveUpdateManager.shared().update("foxes-tigers", content);
        });

        // Stop button
        binding.stopLiveUpdate.setOnClickListener(v -> {
            int s1 = score1.get();
            int s2 = score2.get();
            String status;
            if (s1 == s2) {
                status = "It's a tie!";
            } else if (s1 > s2) {
                status = "Foxes win!";
            } else {
                status = "Tigers win!";
            }

            JsonMap content = JsonMap.newBuilder()
                                     .put("teamOneScore", s1)
                                     .put("team_two_score", s2)
                                     .put("status_update", status)
                                     .build();

            LiveUpdateManager.shared().end("foxes-tigers", content);

            score1.set(0);
            score2.set(0);
        });
    }
}
