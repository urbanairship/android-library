/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.messagecenter;

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
public class MessageCenterFragment extends Fragment {

    private static final String START_MESSAGE_ID = "START_MESSAGE_ID";

    private static final String STATE_CURRENT_MESSAGE_ID = "STATE_CURRENT_MESSAGE_ID";
    private static final String STATE_CURRENT_MESSAGE_POSITION = "STATE_CURRENT_MESSAGE_POSITION";
    private static final String STATE_ABS_LIST_VIEW = "STATE_ABS_LIST_VIEW";
    private static final String STATE_PENDING_MESSAGE_ID = "STATE_PENDING_MESSAGE_ID";

    private RichPushInbox.Predicate predicate;

    private MessageListFragment messageListFragment;
    private boolean isTwoPane;
    private boolean isViewConfigured;

    private String currentMessageId;
    private int currentMessagePosition = -1;
    private String pendingMessageId;

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
    public static MessageCenterFragment newInstance(String messageId) {
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
            pendingMessageId = savedInstanceState.getString(STATE_PENDING_MESSAGE_ID, null);
        }
    }

    /**
     * Subclasses can override to replace with their own layout. If doing so, the
     * returned view hierarchy <em>must</em> must contain a place holder view with ID
     * {@code R.id.message_list_container}.
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
        configureView(view);
        return view;
    }

    @CallSuper
    @Override
    public void onViewCreated(View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        configureView(view);

        messageListFragment.setPredicate(predicate);

        if (savedInstanceState == null && getArguments() != null && getArguments().containsKey(START_MESSAGE_ID)) {
            pendingMessageId = getArguments().getString(START_MESSAGE_ID);
        }

        // Work around Android bug - https://code.google.com/p/android/issues/detail?id=200059
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_ABS_LIST_VIEW)) {
            messageListFragment.getAbsListViewAsync(new MessageListFragment.OnListViewReadyCallback() {
                @Override
                public void onListViewReady(AbsListView absListView) {
                    absListView.onRestoreInstanceState(savedInstanceState.getParcelable(STATE_ABS_LIST_VIEW));
                }
            });
        }
    }

    /**
     * Configures the content view.
     *
     * @param view The content view.
     */
    private void configureView(View view) {
        if (isViewConfigured) {
            return;
        }
        isViewConfigured = true;

        if (view.findViewById(R.id.message_list_container) == null) {
            throw new RuntimeException("Content must have a place holder view whose id attribute is 'R.id.message_list_container'");
        }

        messageListFragment = new MessageListFragment();
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.message_list_container, messageListFragment, "messageList")
                .commit();

        // The presence of a message_container indicates we are running in a split mode
        if (view.findViewById(R.id.message_container) != null) {
            isTwoPane = true;

            // Color the linear layout divider if we are running on JELLY_BEAN or newer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                LinearLayout layoutContainer = view.findViewById(R.id.container);
                TypedArray attributes = getActivity().getTheme().obtainStyledAttributes(null, R.styleable.MessageCenter, R.attr.messageCenterStyle, R.style.MessageCenter);
                int color = attributes.getColor(R.styleable.MessageCenter_messageCenterDividerColor, -1);
                if (color != -1) {
                    DrawableCompat.setTint(layoutContainer.getDividerDrawable(), color);
                    DrawableCompat.setTintMode(layoutContainer.getDividerDrawable(), PorterDuff.Mode.SRC);
                }

                attributes.recycle();
            }

            if (messageListFragment != null && currentMessageId != null) {
                messageListFragment.setCurrentMessage(currentMessageId);
            }
        } else {
            isTwoPane = false;
        }

        configureMessageListFragment(messageListFragment);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isViewConfigured = false;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(STATE_CURRENT_MESSAGE_ID, currentMessageId);
        savedInstanceState.putInt(STATE_CURRENT_MESSAGE_POSITION, currentMessagePosition);
        savedInstanceState.putString(STATE_PENDING_MESSAGE_ID, pendingMessageId);

        // Work around Android bug - https://code.google.com/p/android/issues/detail?id=200059
        if (messageListFragment != null && messageListFragment.getAbsListView() != null) {
            savedInstanceState.putParcelable(STATE_ABS_LIST_VIEW, messageListFragment.getAbsListView().onSaveInstanceState());
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Called to configure the messageListFragment.
     *
     * @param messageListFragment The message list fragment.
     */
    protected void configureMessageListFragment(final MessageListFragment messageListFragment) {

        messageListFragment.getAbsListViewAsync(new MessageListFragment.OnListViewReadyCallback() {
            @Override
            public void onListViewReady(AbsListView absListView) {
                absListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        RichPushMessage message = messageListFragment.getMessage(position);
                        if (message != null) {
                            showMessage(message.getMessageId());
                        }
                    }
                });

                absListView.setMultiChoiceModeListener(new DefaultMultiChoiceModeListener(messageListFragment));
                absListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);

                // Work around Android bug - https://code.google.com/p/android/issues/detail?id=200059
                absListView.setSaveEnabled(false);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isTwoPane) {
            UAirship.shared().getInbox().addListener(inboxListener);
        }

        updateCurrentMessage();

        if (pendingMessageId != null) {
            showMessage(pendingMessageId);
            pendingMessageId = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        UAirship.shared().getInbox().removeListener(inboxListener);
    }

    /**
     * Gets messages from the inbox filtered by the local predicate
     *
     * @return The filtered list of messages.
     */
    private List<RichPushMessage> getMessages() {
        return UAirship.shared().getInbox().getMessages(predicate);
    }

    /**
     * Sets the message ID to display.
     *
     * @param messageId The message ID.
     */
    public void setMessageID(String messageId) {
        if (isResumed()) {
            showMessage(messageId);
        } else {
            this.pendingMessageId = messageId;
        }
    }

    /**
     * Displays a message.
     *
     * @param messageId The message ID.
     */
    protected void showMessage(String messageId) {
        RichPushMessage message = UAirship.shared().getInbox().getMessage(messageId);
        if (message == null) {
            currentMessagePosition = -1;
        } else {
            currentMessagePosition = getMessages().indexOf(message);
        }

        this.currentMessageId = messageId;

        if (messageListFragment == null) {
            return;
        }

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

        } else if (messageId != null) {
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

        if (isTwoPane && currentMessagePosition != -1 && !messages.contains(message)) {
            if (messages.size() == 0) {
                currentMessageId = null;
                currentMessagePosition = -1;
            } else {
                currentMessagePosition = Math.min(messages.size() - 1, currentMessagePosition);
                currentMessageId = messages.get(currentMessagePosition).getMessageId();
            }

            if (isTwoPane) {
                showMessage(currentMessageId);
            }
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
