/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.sample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ClipboardAction;
import com.urbanairship.actions.ShareAction;
import com.urbanairship.util.UAStringUtil;

/**
 * Fragment that displays the channel ID.
 */
public class HomeFragment extends Fragment {

    private TextView channelID;
    private Button shareButton;
    private Button copyButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Channel ID
        channelID = (TextView) view.findViewById(R.id.channel_id);

        shareButton = (Button) view.findViewById(R.id.share_button);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActionRunRequest.createRequest(ShareAction.DEFAULT_REGISTRY_NAME)
                        .setValue(UAirship.shared().getPushManager().getChannelId())
                        .run();
            }
        });

        copyButton = (Button) view.findViewById(R.id.copy_button);
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActionRunRequest.createRequest(ClipboardAction.DEFAULT_REGISTRY_NAME)
                                .setValue(UAirship.shared().getPushManager().getChannelId())
                                .run(new ActionCompletionCallback() {
                                    @Override
                                    public void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result) {
                                        Toast.makeText(getContext(), getString(R.string.toast_channel_clipboard), Toast.LENGTH_SHORT).show();
                                    }
                                });
            }
        });


        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Register a local broadcast manager to listen for ACTION_UPDATE_CHANNEL
        LocalBroadcastManager locationBroadcastManager = LocalBroadcastManager.getInstance(getContext());

        // Use local broadcast manager to receive registration events to update the channel
        IntentFilter channelIdUpdateFilter;
        channelIdUpdateFilter = new IntentFilter();
        channelIdUpdateFilter.addAction(SampleAirshipReceiver.ACTION_UPDATE_CHANNEL);
        locationBroadcastManager.registerReceiver(channelIdUpdateReceiver, channelIdUpdateFilter);

        // Update the channel field
        refreshChannelId();
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager locationBroadcastManager = LocalBroadcastManager.getInstance(getContext());
        locationBroadcastManager.unregisterReceiver(channelIdUpdateReceiver);
    }

    private final BroadcastReceiver channelIdUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshChannelId();
        }
    };

    private void refreshChannelId() {
        String channelIdString = UAirship.shared().getPushManager().getChannelId();
        channelIdString = UAStringUtil.isEmpty(channelIdString) ? "" : channelIdString;

        // fill in channel ID text
        if (!channelIdString.equals(channelID.getText())) {
            channelID.setText(channelIdString);
        }

        if (channelIdString.isEmpty()) {
            copyButton.setEnabled(false);
            shareButton.setEnabled(false);
        } else {
            copyButton.setEnabled(true);
            shareButton.setEnabled(true);
        }
    }
}
