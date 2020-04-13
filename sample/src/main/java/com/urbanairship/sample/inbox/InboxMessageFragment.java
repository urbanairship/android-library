/* Copyright Airship and Contributors */

package com.urbanairship.sample.inbox;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.messagecenter.Message;
import com.urbanairship.messagecenter.MessageCenter;
import com.urbanairship.messagecenter.MessageFragment;
import com.urbanairship.sample.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

/**
 * MessageFragment that supports navigation and maintains its own toolbar.
 */
public class InboxMessageFragment extends MessageFragment {


    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inbox_message, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Toolbar toolbar = view.findViewById(R.id.toolbar);

        NavController navController =  Navigation.findNavController(view);

        Message message = MessageCenter.shared().getInbox().getMessage(getMessageId());

        NavDestination navDestination = navController.getCurrentDestination();
        if (navDestination != null) {
            if (message != null) {
                navController.getCurrentDestination().setLabel(message.getTitle());
            } else {
                navController.getCurrentDestination().setLabel(view.getContext().getString(R.string.message));
            }
        }

        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view));
    }

}
