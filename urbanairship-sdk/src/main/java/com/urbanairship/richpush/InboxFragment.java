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

package com.urbanairship.richpush;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Toast;

import com.urbanairship.Cancelable;
import com.urbanairship.R;
import com.urbanairship.UAirship;

import java.util.List;

/**
 * Fragment that displays the Urban Airship Message Center.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class InboxFragment extends Fragment {

    private InboxViewAdapter adapter;
    private Cancelable fetchMessagesOperation;

    /**
     * Listens for message opens
     */
    public interface OnMessageClickListener {
        void onMessageClick(RichPushMessage message, View view, int position);
        boolean onMessageLongClick(RichPushMessage message, View view, int position);
    }

    private SwipeRefreshLayout refreshLayout;
    private AbsListView listView;

    private OnMessageClickListener onMessageClickListener;
    private RichPushInbox richPushInbox;
    private boolean isManualRefreshing = false;

    private final RichPushInbox.Listener inboxListener = new RichPushInbox.Listener() {
        @Override
        public void onInboxUpdated() {
            updateRichPushMessages();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the InboxMessageAdapter
        setRetainInstance(true);

        this.richPushInbox = UAirship.shared().getInbox();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ua_fragment_inbox, container, false);
        refreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                onRefreshMessages();
            }
        });

        listView = (AbsListView) view.findViewById(R.id.list_view);
        this.adapter = createMessageViewAdapter();
        listView.setAdapter(adapter);
        listView.setEmptyView(view.findViewById(R.id.empty_message));

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onMessageClick((RichPushMessage) adapter.getItem(position), view, position);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                return onMessageLongClick((RichPushMessage) adapter.getItem(position), view, position);
            }
        });

        return view;
    }

    protected InboxViewAdapter createMessageViewAdapter() {
        return new InboxViewAdapter(getContext(), R.layout.ua_inbox_list_item);
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

        if (fetchMessagesOperation != null) {
            fetchMessagesOperation.cancel();
        }
    }

    public void onMessageClick(RichPushMessage message, View view, int position) {
        if (onMessageClickListener != null) {
            this.onMessageClickListener.onMessageClick(message, view, position);
        } else {
            richPushInbox.startMessageActivity(message.getMessageId());
        }
    }

    public boolean onMessageLongClick(RichPushMessage message, View view, int position) {
        if (onMessageClickListener != null) {
            return this.onMessageLongClick(message, view, position);
        }
        return false;
    }

    public void onRefreshMessages() {
        this.isManualRefreshing = true;

        if (fetchMessagesOperation != null) {
            fetchMessagesOperation.cancel();
        }

        fetchMessagesOperation = richPushInbox.fetchMessages(new RichPushInbox.FetchMessagesCallback() {
            @Override
            public void onFinished(boolean success) {
                if (isManualRefreshing && !success) {
                    Toast.makeText(getActivity(), "Failed to update messages!", Toast.LENGTH_LONG).show();
                }

                isManualRefreshing = false;

                if (refreshLayout != null) {
                    refreshLayout.setRefreshing(false);
                }
            }
        });


        if (refreshLayout != null) {
            refreshLayout.setRefreshing(true);
        }
    }

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.onMessageClickListener = listener;
    }

    /**
     * Grabs the latest messages from the rich push inbox, and syncs them
     * with the inbox fragment and message view pager if available
     */
    private void updateRichPushMessages() {
        List<RichPushMessage> messages = richPushInbox.getMessages();
        adapter.set(messages);
    }
}
