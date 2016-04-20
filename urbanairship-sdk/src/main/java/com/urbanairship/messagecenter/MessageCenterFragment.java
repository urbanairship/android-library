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
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.v4.app.Fragment;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.util.ViewUtils;

import java.util.List;

/**
 * The Urban Airship Message Center. The message list will be displayed using the {@link MessageListFragment},
 * and messages will be displayed either in a split view using {@link MessageFragment} or by
 * triggering {@link RichPushInbox#startMessageActivity(String)}.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MessageCenterFragment extends Fragment {

    private static final String START_MESSAGE_ID = "START_MESSAGE_ID";

    private static final String STATE_CURRENT_MESSAGE_ID = "STATE_CURRENT_MESSAGE_ID";
    private static final String STATE_CURRENT_MESSAGE_POSITION = "STATE_CURRENT_MESSAGE_POSITION";
    private static final String STATE_ABS_LIST_VIEW = "STATE_ABS_LIST_VIEW";

    private RichPushInbox.Predicate predicate;

    private MessageListFragment messageListFragment;
    private boolean isTwoPane;

    private String currentMessageId;
    private int currentMessagePosition;

    private final RichPushInbox.Listener inboxListener = new RichPushInbox.Listener() {
        @Override
        public void onInboxUpdated() {
            updateCurrentMessage();
        }
    };

    /**
     * Creates a new {@link MessageCenterFragment}
     *
     * @param messageId The message's ID to display.
     * @return {@link MessageCenterFragment} instance.
     */
    static MessageCenterFragment newInstance(String messageId) {
        MessageCenterFragment message = new MessageCenterFragment();
        Bundle arguments = new Bundle();
        arguments.putString(START_MESSAGE_ID, messageId);
        message.setArguments(arguments);
        return message;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            currentMessagePosition = savedInstanceState.getInt(STATE_CURRENT_MESSAGE_POSITION, -1);
            currentMessageId = savedInstanceState.getString(STATE_CURRENT_MESSAGE_ID, null);
        }

    }

    /**
     * Subclasses can override to replace with their own layout.  If doing so, the
     * returned view hierarchy <em>must</em> have contain a {@link MessageListFragment} whose id
     * is {@code R.id.message_list_fragment}.
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
        View view = inflater.inflate(R.layout.ua_fragment_mc, container, false);
        ensureView(view);
        return view;
    }

    @CallSuper
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureView(view);

        messageListFragment.setPredicate(predicate);

        if (savedInstanceState == null && getArguments() != null && getArguments().containsKey(START_MESSAGE_ID)) {
            showMessage(getArguments().getString(START_MESSAGE_ID));
        }

        // Work around Android bug - https://code.google.com/p/android/issues/detail?id=200059
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_ABS_LIST_VIEW) && messageListFragment.getAbsListView() != null) {
            messageListFragment.getAbsListView().onRestoreInstanceState(savedInstanceState.getParcelable(STATE_ABS_LIST_VIEW));
        }
    }

    /**
     * Ensures that the content view contains a message list fragment.
     *
     * @param view The content view.
     */
    private void ensureView(View view) {
        if (messageListFragment != null) {
            return;
        }

        messageListFragment = (MessageListFragment) getChildFragmentManager().findFragmentById(R.id.message_list_fragment);
        if (messageListFragment == null) {
            throw new RuntimeException("Your content must have a MessageListFragment whose id attribute is 'R.id.message_list_fragment'");
        }

        // The presence of a message_container indicates we are running in a split mode
        if (view.findViewById(R.id.message_container) != null) {
            isTwoPane = true;

            // Color the linear layout divider if we are running on JELLY_BEAN or newer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                LinearLayout layoutContainer = (LinearLayout) view.findViewById(R.id.container);
                TypedArray attributes = getActivity().getTheme().obtainStyledAttributes(null, R.styleable.MessageCenter, R.attr.messageCenterStyle, R.style.MessageCenter);
                int color = attributes.getColor(R.styleable.MessageCenter_messageCenterDividerColor, -1);
                if (color != -1) {
                    DrawableCompat.setTint(layoutContainer.getDividerDrawable(), color);
                    DrawableCompat.setTintMode(layoutContainer.getDividerDrawable(), PorterDuff.Mode.SRC);
                }

                attributes.recycle();
            }
        } else {
            isTwoPane = false;
        }

        configureMessageListFragment(messageListFragment);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(STATE_CURRENT_MESSAGE_ID, currentMessageId);
        savedInstanceState.putInt(STATE_CURRENT_MESSAGE_POSITION, currentMessagePosition);

        // Work around Android bug - https://code.google.com/p/android/issues/detail?id=200059
        if (messageListFragment != null && messageListFragment.getAbsListView() != null) {
            savedInstanceState.putParcelable(STATE_ABS_LIST_VIEW, messageListFragment.getAbsListView().onSaveInstanceState());
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Called to configure the messageListFragment.
     *
     * @param messageListFragment The messsage list fragment.
     */
    protected void configureMessageListFragment(final MessageListFragment messageListFragment) {
        messageListFragment.getAbsListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RichPushMessage message = messageListFragment.getMessage(position);
                if (message != null) {
                    showMessage(message.getMessageId());
                }
            }
        });

        messageListFragment.getAbsListView().setMultiChoiceModeListener(new DefaultMultiChoiceModeListener(messageListFragment));
        messageListFragment.getAbsListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);

        // Work around Android bug - https://code.google.com/p/android/issues/detail?id=200059
        messageListFragment.getAbsListView().setSaveEnabled(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isTwoPane) {
            UAirship.shared().getInbox().addListener(inboxListener);
        }

        updateCurrentMessage();
    }

    @Override
    public void onPause() {
        super.onPause();
        UAirship.shared().getInbox().removeListener(inboxListener);
    }

    /**
     * Gets messages from the inbox filtered by the local predicate
     * @return The filtered list of messages.
     */
    private List<RichPushMessage> getMessages() {
        return UAirship.shared().getInbox().getMessages(predicate);
    }

    /**
     * Displays a message.
     *
     * @param messageId The message ID.
     */
    protected void showMessage(String messageId) {
        RichPushMessage message = UAirship.shared().getInbox().getMessage(messageId);
        if (message == null) {
            return;
        }

        currentMessageId = messageId;
        currentMessagePosition = getMessages().indexOf(message);

        if (isTwoPane) {
            String tag = messageId == null ? "EMPTY_MESSAGE" : messageId;
            if (getChildFragmentManager().findFragmentByTag(tag) != null) {
                // Already displaying
                return;
            }

            Fragment fragment = messageId == null ? new NoMessageSelectedFragment() : MessageFragment.newInstance(messageId);
            getChildFragmentManager().beginTransaction()
                                     .replace(R.id.message_container, fragment, tag)
                                     .commit();

            messageListFragment.setCurrentMessage(messageId);

        } else {
            Intent intent = new Intent()
                    .setPackage(getContext().getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .setData(Uri.fromParts(RichPushInbox.MESSAGE_DATA_SCHEME, messageId, null));

            // Try VIEW_MESSAGE_INTENT_ACTION first
            intent.setAction(RichPushInbox.VIEW_MESSAGE_INTENT_ACTION);

            if (intent.resolveActivity(getContext().getPackageManager()) == null) {
                // Fallback to our MessageActivity
                intent.setClass(getContext(), MessageActivity.class);
            }

            getContext().startActivity(intent);
        }
    }

    private void updateCurrentMessage() {
        RichPushMessage message = UAirship.shared().getInbox().getMessage(currentMessageId);
        List<RichPushMessage> messages = getMessages();

        if (currentMessageId != null && !messages.contains(message)) {
            if (messages.size() == 0) {
                currentMessageId = null;
                currentMessagePosition = -1;
            } else {
                currentMessagePosition = Math.min(messages.size() - 1, currentMessagePosition);
                currentMessageId = messages.get(currentMessagePosition).getMessageId();
            }
        }

        if (isTwoPane) {
            messageListFragment.setCurrentMessage(currentMessageId);
            showMessage(currentMessageId);
        } else {
            messageListFragment.setCurrentMessage(null);
        }
    }

    /**
     * Sets the predicate to use for filtering messages. If unset, the default @link{MessageCenter}
     * predicate will be used.
     *
     * @param predicate A predicate for filtering messages.
     */
    public void setPredicate(RichPushInbox.Predicate predicate) {
        this.predicate = predicate;
    }

    /**
     * Fragment that displays instead of a message in split view when no message has been selected.
     */
    public static class NoMessageSelectedFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.ua_fragment_no_message_selected, container, false);
            View emptyListView = view.findViewById(android.R.id.empty);


            if (emptyListView != null && emptyListView instanceof TextView) {

                TypedArray attributes = getContext()
                        .getTheme()
                        .obtainStyledAttributes(null, R.styleable.MessageCenter, R.attr.messageCenterStyle, R.style.MessageCenter);

                TextView textView = (TextView) emptyListView;
                int textAppearance = attributes.getResourceId(R.styleable.MessageCenter_messageNotSelectedTextAppearance, -1);
                Typeface typeface = ViewUtils.createTypeface(getContext(), textAppearance);

                ViewUtils.applyTextStyle(getContext(), textView, textAppearance, typeface);

                String text = attributes.getString(R.styleable.MessageCenter_messageNotSelectedText);
                textView.setText(text);

                attributes.recycle();
            }

            return view;
        }
    }

}
