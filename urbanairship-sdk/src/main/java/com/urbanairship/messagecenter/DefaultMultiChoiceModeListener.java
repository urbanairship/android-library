/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.messagecenter;

import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;

import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushMessage;

import java.util.HashSet;
import java.util.Set;

/**
 * The default {@link android.widget.AbsListView.MultiChoiceModeListener} for the {@link MessageListFragment}
 * to handle multiple selection.
 */
public class DefaultMultiChoiceModeListener implements AbsListView.MultiChoiceModeListener {

    private final MessageListFragment messageListFragment;

    /**
     * Default constructor.
     * @param messageListFragment The {@link MessageListFragment}.
     */
    public DefaultMultiChoiceModeListener(MessageListFragment messageListFragment) {
        this.messageListFragment = messageListFragment;
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        int count = messageListFragment.getAbsListView().getCheckedItemCount();
        mode.setTitle(messageListFragment.getResources().getQuantityString(R.plurals.ua_selected_count, count, count));
        messageListFragment.getAdapter().notifyDataSetChanged();
        mode.invalidate();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.ua_mc_action_mode, menu);
        int count = messageListFragment.getAbsListView().getCheckedItemCount();
        mode.setTitle(messageListFragment.getResources().getQuantityString(R.plurals.ua_selected_count, count, count));

        boolean containsUnreadMessage = false;
        final SparseBooleanArray checked = messageListFragment.getAbsListView().getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                RichPushMessage message = messageListFragment.getMessage(checked.keyAt(i));
                if (message != null && !message.isRead()) {
                    containsUnreadMessage = true;
                    break;
                }
            }
        }

        MenuItem markRead = menu.findItem(R.id.mark_read);
        markRead.setVisible(containsUnreadMessage);

        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        boolean containsUnreadMessage = false;
        final SparseBooleanArray checked = messageListFragment.getAbsListView().getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                RichPushMessage message = messageListFragment.getMessage(checked.keyAt(i));
                if (message != null && !message.isRead()) {
                    containsUnreadMessage = true;
                    break;
                }
            }
        }

        MenuItem markRead = menu.findItem(R.id.mark_read);
        markRead.setVisible(containsUnreadMessage);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.mark_read) {
            UAirship.shared().getInbox().markMessagesRead(getCheckedMessageIds());
            mode.finish();

        } else if (item.getItemId() == R.id.delete) {
            UAirship.shared().getInbox().deleteMessages(getCheckedMessageIds());
            mode.finish();

        } else if (item.getItemId() == R.id.select_all) {
            int count = messageListFragment.getAbsListView().getCount();
            for (int i = 0; i < count; i++) {
                messageListFragment.getAbsListView().setItemChecked(i, true);
            }
        }

        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {

    }

    private Set<String> getCheckedMessageIds() {
        final SparseBooleanArray checked = messageListFragment.getAbsListView().getCheckedItemPositions();
        final Set<String> messageIds = new HashSet<>();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                RichPushMessage message = messageListFragment.getMessage(checked.keyAt(i));
                if (message != null) {
                    messageIds.add(message.getMessageId());
                }
            }
        }

        return messageIds;
    }
}
