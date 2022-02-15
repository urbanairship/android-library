/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.urbanairship.Cancelable;
import com.urbanairship.Predicate;
import com.urbanairship.util.ViewUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * Fragment that displays the Airship Message Center.
 */
public class MessageListFragment extends Fragment {

    /**
     * Interface that defines the callback when the
     * list view is ready. See {@link #getAbsListViewAsync(OnListViewReadyCallback)}.
     */
    public interface OnListViewReadyCallback {

        /**
         * Called when the list view is ready.
         *
         * @param absListView The abstract list view.
         */
        void onListViewReady(@NonNull AbsListView absListView);

    }

    private SwipeRefreshLayout refreshLayout;
    private AbsListView absListView;
    private Inbox inbox;
    private MessageViewAdapter adapter;
    private Cancelable fetchMessagesOperation;
    private String currentMessageId;
    private Predicate<Message> predicate;
    private final List<OnListViewReadyCallback> pendingCallbacks = new ArrayList<>();

    @DrawableRes
    private int placeHolder = R.drawable.ua_ic_image_placeholder;

    private final InboxListener inboxListener = new InboxListener() {
        @Override
        public void onInboxUpdated() {
            updateAdapterMessages();
        }
    };

    /**
     * Gets messages from the inbox filtered by the local predicate
     *
     * @return The filtered list of messages.
     */
    private List<Message> getMessages() {
        return inbox.getMessages(predicate);
    }

    private void updateAdapterMessages() {
        if (getAdapter() != null) {
            getAdapter().set(getMessages());
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.inbox = MessageCenter.shared().getInbox();
        updateAdapterMessages();
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
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ua_fragment_message_list, container, false);
        ensureList(view);

        if (getAbsListView() == null) {
            return view;
        }

        // Item click listener
        getAbsListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Message message = getMessage(position);
                if (message != null) {
                    MessageCenter.shared().showMessageCenter(message.getMessageId());
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureList(view);

        for (OnListViewReadyCallback callback : new ArrayList<>(pendingCallbacks)) {
            callback.onListViewReady(absListView);
        }
        pendingCallbacks.clear();
    }

    /**
     * Ensures the list view is set up.
     *
     * @param view The content view.
     */
    private void ensureList(@NonNull View view) {
        if (getContext() == null) {
            return;
        }

        if (absListView != null) {
            return;
        }

        if (view instanceof AbsListView) {
            absListView = (AbsListView) view;
        } else {
            absListView = view.findViewById(android.R.id.list);
        }

        if (absListView == null) {
            throw new RuntimeException("Your content must have a ListView whose id attribute is 'android.R.id.list'");
        }

        if (getAdapter() != null) {
            absListView.setAdapter(getAdapter());
        }

        // Pull to refresh
        refreshLayout = view.findViewById(R.id.swipe_container);
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

        if (emptyListView instanceof TextView) {
            TextView textView = (TextView) emptyListView;
            int textAppearance = attributes.getResourceId(R.styleable.MessageCenter_messageCenterEmptyMessageTextAppearance, -1);
            ViewUtils.applyTextStyle(getContext(), textView, textAppearance);

            String text = attributes.getString(R.styleable.MessageCenter_messageCenterEmptyMessageText);
            textView.setText(text);
        }

        if (absListView instanceof ListView) {
            ListView listView = (ListView) absListView;

            if (attributes.hasValue(R.styleable.MessageCenter_messageCenterDividerColor) && listView.getDivider() != null) {
                int color = attributes.getColor(R.styleable.MessageCenter_messageCenterDividerColor, Color.BLACK);
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
    protected MessageViewAdapter createMessageViewAdapter(@NonNull Context context) {
        final List<String> selectedMessageIds = new ArrayList<>();

        return new MessageViewAdapter(context, R.layout.ua_item_mc) {
            @Override
            protected void bindView(@NonNull View view, @NonNull Message message, final int position) {
                if (view instanceof MessageItemView) {
                    MessageItemView itemView = (MessageItemView) view;

                    itemView.setSelectionListener(v -> setSelection(message.getMessageId(), position));

                    itemView.updateMessage(message, placeHolder, isSelected(message));
                    itemView.setHighlighted(message.getMessageId().equals(currentMessageId));
                }
            }

            private void setSelection(String messageId, int position) {
                AbsListView list = getAbsListView();
                if (list == null) {
                    return;
                }
                boolean isChecked = !list.isItemChecked(position);
                // Update the list selections.
                list.setItemChecked(position, isChecked);
                // Also update the adapter so that view re-binds are aware of the checked state.
                if (isChecked) {
                    selectedMessageIds.add(messageId);
                } else {
                    selectedMessageIds.remove(messageId);
                }
            }

            private boolean isSelected(@NonNull Message message) {
                return selectedMessageIds.contains(message.getMessageId());
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();

        inbox.addListener(inboxListener);

        // Set latest messages
        updateAdapterMessages();

        // refresh the inbox
        inbox.fetchMessages();

        if (getAbsListView() != null) {
            getAbsListView().invalidate();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Remove listeners for message changes
        inbox.removeListener(inboxListener);

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

        fetchMessagesOperation = inbox.fetchMessages(new Inbox.FetchMessagesCallback() {
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
    @Nullable
    public AbsListView getAbsListView() {
        return absListView;
    }

    /**
     * Gets the message list view once it is ready. The callback will be called
     * on the main thread. If the list view is already ready, the callback will
     * be called immediately.
     *
     * @param callback The on ready callback.
     */
    @MainThread
    public void getAbsListViewAsync(@NonNull OnListViewReadyCallback callback) {
        if (absListView != null) {
            callback.onListViewReady(absListView);
        } else {
            pendingCallbacks.add(callback);
        }
    }

    /**
     * Returns a the {@link Message} at a given position.
     *
     * @param position The list position.
     * @return The {@link Message} at a given position.
     */
    @Nullable
    public Message getMessage(int position) {
        if (adapter != null && adapter.getCount() > position) {
            return (Message) adapter.getItem(position);
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Tear down any selection in progress
        absListView.setChoiceMode(AbsListView.CHOICE_MODE_NONE);
        absListView = null;
        refreshLayout = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pendingCallbacks.clear();
    }

    /**
     * Returns the {@link MessageViewAdapter} for the list view.
     *
     * @return The {@link MessageViewAdapter} for the list view.
     */
    @Nullable
    public MessageViewAdapter getAdapter() {
        if (adapter == null) {
            if (getContext() == null) {
                return null;
            }
            adapter = createMessageViewAdapter(getContext());
        }

        return adapter;
    }

    /**
     * Called to set the current message Id. The message will be highlighted
     * in the list.
     *
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

    void setPredicate(Predicate<Message> predicate) {
        this.predicate = predicate;
        if (getAdapter() != null) {
            updateAdapterMessages();
        }
    }

}
