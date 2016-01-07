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
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.urbanairship.Cancelable;
import com.urbanairship.R;
import com.urbanairship.UAirship;

import java.util.Date;

/**
 * Fragment that displays the Urban Airship Message Center.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class InboxFragment extends Fragment {

    private SwipeRefreshLayout refreshLayout;
    private AbsListView absListView;
    private RichPushInbox richPushInbox;
    private boolean isManualRefreshing = false;
    private InboxViewAdapter adapter;
    private Cancelable fetchMessagesOperation;
    private ImageLoader imageLoader;
    private String currentMessageId;

    private class MessageViewHolder {
        TextView titleView;
        TextView dateView;
        ImageView imageView;
        CheckBox checkBox;
    }

    private final RichPushInbox.Listener inboxListener = new RichPushInbox.Listener() {
        @Override
        public void onInboxUpdated() {
            adapter.set(richPushInbox.getMessages());
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        this.richPushInbox = UAirship.shared().getInbox();
        this.adapter = createMessageViewAdapter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ua_fragment_inbox, container, false);
        refreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);

        if (refreshLayout != null) {
            refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    onRefreshMessages();
                }
            });
        }

        absListView = (AbsListView) view.findViewById(R.id.list_view);

        absListView.setAdapter(adapter);
        absListView.setEmptyView(view.findViewById(R.id.empty_message));

        absListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                UAirship.shared().getInbox().startMessageActivity(getMessage(position).getMessageId());
            }
        });


        onAbsListViewCreated(absListView);

        return view;
    }

    /**
     * Called when the {@link AbsListView} is created.
     *
     * @param listView The {@link AbsListView}.
     */
    protected void onAbsListViewCreated(AbsListView listView) {

    }

    /**
     * Called when the {@link InboxViewAdapter} needs to be created
     * for the {@link AbsListView}.
     *
     * @return A {@link InboxViewAdapter} for the list view.
     */
    protected InboxViewAdapter createMessageViewAdapter() {
        imageLoader = new ImageLoader(getContext());
        return new InboxViewAdapter(getContext(), R.layout.ua_item_inbox_icon) {
            @Override
            protected void bindView(View view, RichPushMessage message, final int position) {

                MessageViewHolder viewHolder = (MessageViewHolder) view.getTag();
                if (viewHolder == null) {
                    viewHolder = new MessageViewHolder();
                    viewHolder.titleView = (TextView) view.findViewById(R.id.title);
                    viewHolder.dateView = (TextView) view.findViewById(R.id.date);
                    viewHolder.imageView = (ImageView) view.findViewById(R.id.image);
                    viewHolder.checkBox = (CheckBox) view.findViewById(R.id.checkbox);
                    view.setTag(viewHolder);
                }

                if (viewHolder.titleView != null) {
                    viewHolder.titleView.setText(message.getTitle());

                    if (message.isRead()) {
                        viewHolder.titleView.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
                    } else {
                        viewHolder.titleView.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
                    }
                }

                if (viewHolder.dateView != null) {
                    Date date = message.getSentDate();
                    viewHolder.dateView.setText(DateFormat.getDateFormat(getActivity()).format(date));

                }

                if (viewHolder.imageView != null) {
                    imageLoader.load(message.getListIconUrl(), R.drawable.ua_ic_image_placeholder, viewHolder.imageView);
                }

                if (viewHolder.checkBox != null) {
                    final CheckBox checkBox = viewHolder.checkBox;
                    checkBox.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            getAbsListView().setItemChecked(position, checkBox.isChecked());
                        }
                    });

                    checkBox.setChecked(getAbsListView().isItemChecked(position));
                }

                if (message.getMessageId().equals(currentMessageId)) {
                    view.setBackgroundResource(R.drawable.ua_item_inbox_background_highlighted);
                } else {
                    view.setBackgroundResource(R.drawable.ua_item_inbox_background);
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();

        richPushInbox.addListener(inboxListener);

        // Set latest messages
        adapter.set(richPushInbox.getMessages());

        getAbsListView().invalidate();
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

    /**
     * Called when the messages list is refreshing.
     */
    private void onRefreshMessages() {
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


    /**
     * Returns the {@link AbsListView} for the fragment.
     *
     * @return The {@link AbsListView}.
     */
    public AbsListView getAbsListView() {
        if (absListView == null && getView() != null) {
            absListView = (AbsListView) getView().findViewById(R.id.list_view);
        }

        return absListView;
    }

    /**
     * Returns a the {@link RichPushMessage} at a given position.
     *
     * @param position The list position.
     * @return The {@link RichPushMessage} at a given position.
     */
    public RichPushMessage getMessage(int position) {
        if (adapter.getCount() > position) {
            return (RichPushMessage) adapter.getItem(position);
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Tear down any selection in progress
        absListView.setChoiceMode(AbsListView.CHOICE_MODE_NONE);
    }

    /**
     * Returns the {@link InboxViewAdapter} for the list view.
     *
     * @return The {@link InboxViewAdapter} for the list view.
     */
    public InboxViewAdapter getAdapter() {
        return adapter;
    }

    /**
     * Sets the current message ID to be highlighted.
     *
     * @param messageId The current message ID or {@code null} to clear the message.
     */
    void setCurrentMessageId(String messageId) {
        currentMessageId = messageId;
        if (getAdapter() != null) {
            getAdapter().notifyDataSetChanged();
        }
    }
}
