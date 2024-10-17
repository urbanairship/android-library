/* Copyright Airship and Contributors */

package com.urbanairship.sample.inbox;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.PendingResult;
import com.urbanairship.messagecenter.Message;
import com.urbanairship.messagecenter.MessageCenter;
import com.urbanairship.messagecenter.ui.view.MessageView;
import com.urbanairship.sample.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

/**
 * MessageFragment that supports navigation and maintains its own toolbar.
 */
public class InboxMessageFragment extends Fragment {

    private static final String ARG_MESSAGE_ID = "messageReporting";

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

        Bundle args = getArguments();
        if (args == null || !args.containsKey(ARG_MESSAGE_ID)) {
            throw new IllegalStateException("Argument 'ARG_MESSAGE_ID' is required to display a message!");
        }

        String messageId = args.getString(ARG_MESSAGE_ID);

        MessageView messageView = view.findViewById(R.id.message_view);
        messageView.setMessageId(messageId);

        PendingResult<Message> pendingResult = MessageCenter.shared().getInbox().getMessagePendingResult(messageId);

        pendingResult.addResultCallback(message -> {
            NavDestination navDestination = navController.getCurrentDestination();
            if (navDestination != null) {
                if (message != null) {
                    navController.getCurrentDestination().setLabel(message.getTitle());
                } else {
                    navController.getCurrentDestination().setLabel(view.getContext().getString(R.string.message));
                }
            }
        });

        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view));
    }
}
