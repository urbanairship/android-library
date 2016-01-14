/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.


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

package com.urbanairship.messagecenter;


import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;

import java.util.List;

/**
 * A fragment that displays messages in a view pager.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MessagePagerFragment extends Fragment {

    private final static String MESSAGE_ID = "CURRENT_MESSAGE_ID";

    private ViewPager messagePager;
    private List<RichPushMessage> messages;
    private RichPushInbox richPushInbox;
    private MessageFragmentAdapter adapter;
    private OnMessageChangedListener listener;
    private String currentMessageId;

    private final RichPushInbox.Listener inboxListener =  new RichPushInbox.Listener() {
        @Override
        public void onInboxUpdated() {
            updateRichPushMessages();
        }
    };

    /**
     * Listener for the message pager fragment.
     */
    public interface OnMessageChangedListener {
        void onMessageChanged(RichPushMessage message);
    }

    /**
     * Creates a new instance of MessagePagerFragment.
     *
     * @param messageId The initial message ID to view.
     * @return MessagePagerFragment instance.
     */
    public static MessagePagerFragment newInstance(String messageId) {
        Bundle args = new Bundle();
        args.putString(MESSAGE_ID, messageId);

        MessagePagerFragment fragment = new MessagePagerFragment();
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.richPushInbox = UAirship.shared().getInbox();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ua_fragment_message_pager, container, false);
        this.messagePager = (ViewPager) view.findViewById(R.id.message_pager);
        this.adapter = new MessageFragmentAdapter(this.getChildFragmentManager());

        messagePager.setAdapter(adapter);

        this.messagePager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                RichPushMessage message = messages.get(position);
                if (message != null) {
                    setCurrentMessage(message.getMessageId());
                }
            }
        });

        if (savedInstanceState == null) {
            if (getArguments() != null) {
                currentMessageId = getArguments().getString(MESSAGE_ID);
            }
        } else {
            currentMessageId = savedInstanceState.getString(MESSAGE_ID);
        }

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(MESSAGE_ID, currentMessageId);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Set latest messages
        updateRichPushMessages();

        // Listen for any rich push message changes
        richPushInbox.addListener(inboxListener);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Remove listeners for message changes
        richPushInbox.removeListener(inboxListener);
    }

    public String getCurrentMessageId() {
        return currentMessageId;
    }

    public void setOnMessageChangedListener(OnMessageChangedListener listener) {
        this.listener = listener;
    }

    /**
     * Grabs the latest messages from the rich push inbox, and syncs them
     * with the {@link RichPushInbox} and message view pager if available
     */
    private void updateRichPushMessages() {
        this.messages = UAirship.shared().getInbox().getMessages();
        adapter.setRichPushMessages(messages);

        // Restore the position in the message list if the message still exists
        setCurrentMessage(currentMessageId);
    }

    /**
     * Sets the current message to view
     *
     * @param messageId The message's ID to view
     */
    public void setCurrentMessage(String messageId) {
        RichPushMessage message = richPushInbox.getMessage(messageId);

        if (message == null) {
            if (messages.size() == 0) {
                currentMessageId = null;

                if (listener != null) {
                    listener.onMessageChanged(null);
                }

                return;
            }

            int index = Math.min(messages.size() - 1, messagePager.getCurrentItem());
            message = messages.get(index);
        }

        message.markRead();
        currentMessageId = message.getMessageId();

        if (listener != null) {
            listener.onMessageChanged(message);
        }

        if (messagePager.getCurrentItem() != messages.indexOf(message)) {
            messagePager.setCurrentItem(messages.indexOf(message), false);
        }
    }
}
