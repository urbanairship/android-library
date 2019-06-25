/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.graphics.drawable.DrawableCompat;
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
 * The Airship Message Center. The message list will be displayed using the {@link MessageListFragment},
 * and messages will be displayed either in a split view using {@link MessageFragment} or by
 * triggering {@link RichPushInbox#startMessageActivity(String)}.
 */
public class MessageCenterFragment extends Fragment {

    // State
    private static final String STATE_CURRENT_MESSAGE_ID = "currentMessageId";
    private static final String STATE_CURRENT_MESSAGE_POSITION = "currentMessagePosition";
    private static final String STATE_ABS_LIST_VIEW = "listView";
    private static final String STATE_PENDING_MESSAGE_ID = "pendingMessageId";

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
    @NonNull
    public static MessageCenterFragment newInstance(@Nullable String messageId) {
        MessageCenterFragment message = new MessageCenterFragment();
        Bundle arguments = new Bundle();
        arguments.putString(MessageFragment.MESSAGE_ID, messageId);
        message.setArguments(arguments);
        return message;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            currentMessagePosition = savedInstanceState.getInt(STATE_CURRENT_MESSAGE_POSITION, -1);
            currentMessageId = savedInstanceState.getString(STATE_CURRENT_MESSAGE_ID, null);
            pendingMessageId = savedInstanceState.getString(STATE_PENDING_MESSAGE_ID, null);
        } else if (getArguments() != null) {
            pendingMessageId = getArguments().getString(MessageFragment.MESSAGE_ID);
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
     * @return Return the View for the fragment's UI.
     */
    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ua_fragment_mc, container, false);
        configureView(view);
        return view;
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        configureView(view);

        messageListFragment.setPredicate(predicate);

        // Work around Android bug - https://code.google.com/p/android/issues/detail?id=200059
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_ABS_LIST_VIEW)) {
            messageListFragment.getAbsListViewAsync(new MessageListFragment.OnListViewReadyCallback() {
                @Override
                public void onListViewReady(@NonNull AbsListView absListView) {
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
    private void configureView(@NonNull View view) {
        if (getActivity() == null) {
            return;
        }

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
            LinearLayout layoutContainer = view.findViewById(R.id.container);
            TypedArray attributes = getActivity().getTheme().obtainStyledAttributes(null, R.styleable.MessageCenter, R.attr.messageCenterStyle, R.style.MessageCenter);
            if (attributes.hasValue(R.styleable.MessageCenter_messageCenterDividerColor)) {
                int color = attributes.getColor(R.styleable.MessageCenter_messageCenterDividerColor, Color.BLACK);
                DrawableCompat.setTint(layoutContainer.getDividerDrawable(), color);
                DrawableCompat.setTintMode(layoutContainer.getDividerDrawable(), PorterDuff.Mode.SRC);
            }

            attributes.recycle();

            if (currentMessageId != null) {
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
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
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
    protected void configureMessageListFragment(@NonNull final MessageListFragment messageListFragment) {
        messageListFragment.getAbsListViewAsync(new MessageListFragment.OnListViewReadyCallback() {
            @Override
            public void onListViewReady(@NonNull AbsListView absListView) {
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
    @NonNull
    private List<RichPushMessage> getMessages() {
        return UAirship.shared().getInbox().getMessages(predicate);
    }

    /**
     * Sets the message ID to display.
     *
     * @param messageId The message ID.
     */
    public void setMessageID(@Nullable String messageId) {
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
    protected void showMessage(@Nullable String messageId) {
        if (getContext() == null) {
            return;
        }

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
            showMessageExternally(getContext(), messageId);
        }
    }

    /**
     * Called to display a message in single pane mode.
     *
     * @param context The context.
     * @param messageId The message Id.
     */
    protected void showMessageExternally(@NonNull Context context, @NonNull String messageId) {

        Intent intent = new Intent()
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(Uri.fromParts(MessageCenter.MESSAGE_DATA_SCHEME, messageId, null));

        // Try VIEW_MESSAGE_INTENT_ACTION first
        intent.setAction(MessageCenter.VIEW_MESSAGE_INTENT_ACTION);

        if (intent.resolveActivity(context.getPackageManager()) == null) {
            // Fallback to our MessageActivity
            intent.setClass(context, MessageActivity.class);
        }

        context.startActivity(intent);
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
    public void setPredicate(@Nullable RichPushInbox.Predicate predicate) {
        this.predicate = predicate;
    }

    /**
     * Fragment that displays instead of a message in split view when no message has been selected.
     */
    public static class NoMessageSelectedFragment extends Fragment {

        @NonNull
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            Context context = inflater.getContext();

            View view = inflater.inflate(R.layout.ua_fragment_no_message_selected, container, false);
            View emptyListView = view.findViewById(android.R.id.empty);

            if (emptyListView instanceof TextView) {
                TypedArray attributes = context
                        .getTheme()
                        .obtainStyledAttributes(null, R.styleable.MessageCenter, R.attr.messageCenterStyle, R.style.MessageCenter);

                TextView textView = (TextView) emptyListView;
                int textAppearance = attributes.getResourceId(R.styleable.MessageCenter_messageNotSelectedTextAppearance, 0);

                ViewUtils.applyTextStyle(inflater.getContext(), textView, textAppearance);

                String text = attributes.getString(R.styleable.MessageCenter_messageNotSelectedText);
                textView.setText(text);

                attributes.recycle();
            }

            return view;
        }

    }

}
