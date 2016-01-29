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

package com.urbanairship.messagecenter;

import android.annotation.TargetApi;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.urbanairship.Cancelable;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.util.ViewUtils;

/**
 * Fragment that displays the Urban Airship Message Center.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MessageListFragment extends Fragment {

    private SwipeRefreshLayout refreshLayout;
    private AbsListView absListView;
    private RichPushInbox richPushInbox;
    private MessageViewAdapter adapter;
    private Cancelable fetchMessagesOperation;
    private ImageLoader imageLoader;
    private String currentMessageId;

    @DrawableRes
    private int placeHolder = R.drawable.ua_ic_image_placeholder;

    private final RichPushInbox.Listener inboxListener = new RichPushInbox.Listener() {
        @Override
        public void onInboxUpdated() {
            adapter.set(richPushInbox.getMessages());
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.richPushInbox = UAirship.shared().getInbox();
        this.adapter = createMessageViewAdapter();
    }

    /**
     * Subclasses can override to replace with their own layout.  If doing so, the
     * returned view hierarchy <em>must</em> have an AbsListView (GridView or ListView) whose id
     * is {@code android.R.id.list}.
     *
     * @param inflater The LayoutInflater object that can be used to inflate
     * any views in the fragment,
     * @param container If non-null, this is the parent view that the fragment's
     * UI should be attached to.  The fragment should not add the view itself,
     * but this can be used to generate the LayoutParams of the view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     * @return Return the View for the fragment's UI, or null.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ua_fragment_message_list, container, false);
        ensureList(view);

        // Item click listener
        getAbsListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RichPushMessage message = getMessage(position);
                if (message != null) {
                    UAirship.shared().getInbox().startMessageActivity(message.getMessageId());
                }
            }
        });

        // Empty list view
        View emptyListView = view.findViewById(android.R.id.empty);
        if (emptyListView != null) {
            absListView.setEmptyView(emptyListView);
        }

        return view;
    }

    @CallSuper
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureList(view);
    }

    /**
     * Ensures the list view is set up.
     *
     * @param view The content view.
     */
    private void ensureList(@NonNull View view) {
        if (absListView != null) {
            return;
        }

        if (view instanceof AbsListView) {
            absListView = (AbsListView) view;
        } else {
            absListView = (AbsListView) view.findViewById(android.R.id.list);
        }

        if (absListView == null) {
            throw new RuntimeException("Your content must have a ListView whose id attribute is 'android.R.id.list'");
        }

        absListView.setAdapter(adapter);

        // Pull to refresh
        refreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        if (refreshLayout != null) {
            refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    onRefreshMessages();
                }
            });
        }

        View emptyListView = view.findViewById(android.R.id.empty);

        // Style
        TypedArray attributes = getContext()
                .getTheme()
                .obtainStyledAttributes(null, R.styleable.MessageCenter, R.attr.messageCenterStyle, R.style.MessageCenter);

        if (emptyListView != null && emptyListView instanceof TextView) {
            TextView textView = (TextView) emptyListView;
            int textAppearance = attributes.getResourceId(R.styleable.MessageCenter_messageCenterEmptyMessageTextAppearance, -1);
            Typeface typeface = ViewUtils.createTypeface(getContext(), textAppearance);
            ViewUtils.applyTextStyle(getContext(), textView, textAppearance, typeface);

            String text = attributes.getString(R.styleable.MessageCenter_messageCenterEmptyMessageText);
            textView.setText(text);
        }

        if (absListView instanceof ListView) {
            ListView listView = (ListView) absListView;

            int color = attributes.getColor(R.styleable.MessageCenter_messageCenterDividerColor, -1);
            if (color != -1) {
                DrawableCompat.setTint(listView.getDivider(), color);
                DrawableCompat.setTintMode(listView.getDivider(), PorterDuff.Mode.SRC);
            }
        }

        placeHolder = attributes.getResourceId(R.styleable.MessageCenter_messageCenterItemIconPlaceholder, placeHolder);

        attributes.recycle();
    }

    /**
     * Called when the {@link MessageViewAdapter} needs to be created
     * for the {@link AbsListView}.
     *
     * @return A {@link MessageViewAdapter} for the list view.
     */
    @NonNull
    protected MessageViewAdapter createMessageViewAdapter() {
        imageLoader = new ImageLoader(getContext());
        return new MessageViewAdapter(getContext(), R.layout.ua_item_mc) {
            @Override
            protected void bindView(View view, RichPushMessage message, final int position) {
                if (view instanceof MessageItemView) {
                    MessageItemView itemView = (MessageItemView) view;

                    itemView.updateMessage(message, placeHolder, imageLoader);
                    itemView.setHighlighted(message.getMessageId().equals(currentMessageId));
                    itemView.setSelectionListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            getAbsListView().setItemChecked(position, !getAbsListView().isItemChecked(position));
                        }
                    });
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
        if (fetchMessagesOperation != null) {
            fetchMessagesOperation.cancel();
        }

        fetchMessagesOperation = richPushInbox.fetchMessages(new RichPushInbox.FetchMessagesCallback() {
            @Override
            public void onFinished(boolean success) {
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
     * Returns the {@link MessageViewAdapter} for the list view.
     *
     * @return The {@link MessageViewAdapter} for the list view.
     */
    public MessageViewAdapter getAdapter() {
        return adapter;
    }

    /**
     * Called to set the current message Id. The message will be highlighted
     * in the list.
     * @param messageId The message ID or null to clear it.
     */
    void setCurrentMessage(@Nullable String messageId) {
        if (currentMessageId == null && messageId == null) {
            return;
        }

        if (currentMessageId != null && currentMessageId.equals(messageId)) {
            return;
        }

        currentMessageId = messageId;
        if (getAdapter() != null) {
            getAdapter().notifyDataSetChanged();
        }
    }
}
