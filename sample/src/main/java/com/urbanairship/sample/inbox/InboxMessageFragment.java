/* Copyright Airship and Contributors */

package com.urbanairship.sample.inbox;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.UAirship;
import com.urbanairship.messagecenter.MessageFragment;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.sample.R;

import androidx.navigation.NavController;
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

        RichPushMessage message = UAirship.shared().getInbox().getMessage(getMessageId());
        if (message != null) {
            navController.getCurrentDestination().setLabel(message.getTitle());
        } else {
            navController.getCurrentDestination().setLabel(view.getContext().getString(R.string.message));
        }

        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view));
    }

}
