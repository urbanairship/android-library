/* Copyright Airship and Contributors */

package com.urbanairship.sample.inbox;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.messagecenter.MessageCenterFragment;
import com.urbanairship.messagecenter.ui.MessageFragment;
import com.urbanairship.sample.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

/**
 * MessageCenterFragment that supports navigation and maintains its own toolbar.
 */
public class InboxFragment extends MessageCenterFragment {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inbox, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view));
    }

    @Override
    protected void showMessageExternally(@NonNull Context context, @NonNull String messageId) {
        if (getView() != null) {
            Bundle arguments = new Bundle();
            arguments.putString(MessageFragment.MESSAGE_ID, messageId);
            Navigation.findNavController(getView()).navigate(R.id.inboxMessageFragment, arguments);
        }
    }

}
