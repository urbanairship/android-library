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
    @NonNull
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

        bindSportsLiveUpdate(binding);
        bindSportsLiveUpdateAsync(binding);

        return binding.getRoot();
    }

    public void bindSportsLiveUpdate(@NonNull FragmentHomeBinding binding) {
        AtomicInteger score1 = new AtomicInteger(0);
        AtomicInteger score2 = new AtomicInteger(0);

        binding.luStart.setOnClickListener(v -> {
            score1.set(0);
            score2.set(0);

            LiveUpdateManager.shared().start(
                    "sports",
                    "sports",
                    JsonMap.newBuilder()
                           .put("team_one_image", "https://content.sportslogos.net/logos/28/127/full/1458.gif")
                           .put("team_one_score", score1.getAndIncrement())
                           .put("team_two_image", "https://content.sportslogos.net/logos/28/116/full/1439.gif")
                           .put("team_two_score", score2.getAndIncrement())
                           .build()
            );
        });

        binding.luUpdate.setOnClickListener(v -> {
            LiveUpdateManager.shared().update(
                    "sports",
                    JsonMap.newBuilder()
                           .put("team_one_image", "https://content.sportslogos.net/logos/28/127/full/1458.gif")
                           .put("team_one_score", score1.getAndIncrement())
                           .put("team_two_image", "https://content.sportslogos.net/logos/28/116/full/1439.gif")
                           .put("team_two_score", score2.getAndIncrement())
                           .build()
            );
        });

        binding.luEnd.setOnClickListener(v -> {
            LiveUpdateManager.shared().end("sports");
        });
    }

    public void bindSportsLiveUpdateAsync(@NonNull FragmentHomeBinding binding) {
        AtomicInteger score1 = new AtomicInteger(0);
        AtomicInteger score2 = new AtomicInteger(0);

        binding.luAsyncStart.setOnClickListener(v -> {
            score1.set(0);
            score2.set(0);

            LiveUpdateManager.shared().start(
                    "sports-async",
                    "sports-async",
                    JsonMap.newBuilder()
                           .put("team_one_image", "https://content.sportslogos.net/logos/28/127/full/1458.gif")
                           .put("team_one_score", score1.getAndIncrement())
                           .put("team_two_image", "https://content.sportslogos.net/logos/28/116/full/1439.gif")
                           .put("team_two_score", score2.getAndIncrement())
                           .build()
            );
        });

        binding.luAsyncUpdate.setOnClickListener(v -> {
            LiveUpdateManager.shared().update(
                    "sports-async",
                    JsonMap.newBuilder()
                           .put("team_one_image", "https://content.sportslogos.net/logos/28/116/full/1439.gif")
                           .put("team_one_score", score1.getAndIncrement())
                           .put("team_two_image", "https://content.sportslogos.net/logos/28/127/full/1458.gif")
                           .put("team_two_score", score2.getAndIncrement())
                           .build()
            );
        });

        binding.luAsyncEnd.setOnClickListener(v -> {
            LiveUpdateManager.shared().end("sports-async");
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view));
    }
}
