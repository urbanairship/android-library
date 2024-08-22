/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.res.Resources;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.AbsListView;

import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

/**
 * The default {@link AbsListView.MultiChoiceModeListener} for the {@link MessageListFragment}
 * to handle multiple selection.
 *
 * @hide
 */
public class DefaultMultiChoiceModeListener implements AbsListView.MultiChoiceModeListener {

    private final MessageListFragment messageListFragment;

    @Nullable
    private MessageCenterFragment.OnActionModeListener actionModeListener = null;

    /**
     * Default constructor.
     *
     * @param messageListFragment The {@link MessageListFragment}.
     */
    public DefaultMultiChoiceModeListener(@NonNull MessageListFragment messageListFragment) {
        this.messageListFragment = messageListFragment;
    }

    /**
     * Default constructor with {@link ActionMode} listener.
     *
     * @param messageListFragment The {@link MessageListFragment}.
     * @param actionModeListener The {@link MessageCenterFragment.OnActionModeListener}.
     */
    public DefaultMultiChoiceModeListener(@NonNull MessageListFragment messageListFragment, @Nullable MessageCenterFragment.OnActionModeListener actionModeListener) {
        this.messageListFragment = messageListFragment;
        this.actionModeListener = actionModeListener;
    }

    @Override
    public void onItemCheckedStateChanged(@NonNull ActionMode mode, int position, long id, boolean checked) {
        if (messageListFragment.getAbsListView() == null) {
            return;
        }

        int count = messageListFragment.getAbsListView().getCheckedItemCount();
        mode.setTitle(messageListFragment.getResources().getQuantityString(com.urbanairship.R.plurals.ua_selected_count, count, count));
        if (messageListFragment.getAdapter() != null) {
            messageListFragment.getAdapter().notifyDataSetChanged();
        }
        mode.invalidate();
    }

    @Override
    public boolean onCreateActionMode(@NonNull ActionMode mode, @NonNull Menu menu) {
        if (messageListFragment.getAbsListView() == null) {
            return false;
        }

        mode.getMenuInflater().inflate(R.menu.ua_mc_action_mode, menu);
        int count = messageListFragment.getAbsListView().getCheckedItemCount();
        mode.setTitle(messageListFragment.getResources().getQuantityString(com.urbanairship.R.plurals.ua_selected_count, count, count));

        boolean containsUnreadMessage = false;
        final SparseBooleanArray checked = messageListFragment.getAbsListView().getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                Message message = messageListFragment.getMessage(checked.keyAt(i));
                if (message != null && !message.isRead()) {
                    containsUnreadMessage = true;
                    break;
                }
            }
        }

        MenuItem markRead = menu.findItem(R.id.mark_read);
        markRead.setVisible(containsUnreadMessage);

        if (actionModeListener != null) {
            actionModeListener.onActionModeCreated(mode, menu);
        }

        return true;
    }

    @Override
    public boolean onPrepareActionMode(@NonNull ActionMode mode, @NonNull Menu menu) {
        if (messageListFragment.getAbsListView() == null) {
            return false;
        }

        boolean containsUnreadMessage = false;
        final SparseBooleanArray checked = messageListFragment.getAbsListView().getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                Message message = messageListFragment.getMessage(checked.keyAt(i));
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
    public boolean onActionItemClicked(@NonNull ActionMode mode, @NonNull MenuItem item) {
        AbsListView listView = messageListFragment.getAbsListView();
        if (listView == null) {
            return false;
        }
        Resources res = listView.getContext().getResources();

        if (item.getItemId() == R.id.mark_read) {
            MessageCenter.shared().getInbox().markMessagesRead(getCheckedMessageIds());
            int count = getCheckedMessageIds().size();
            listView.announceForAccessibility(
                res.getQuantityString(R.plurals.ua_mc_description_marked_read, count, count));
            mode.finish();

        } else if (item.getItemId() == R.id.delete) {
            MessageCenter.shared().getInbox().deleteMessages(getCheckedMessageIds());
            int count = getCheckedMessageIds().size();
            listView.announceForAccessibility(
                res.getQuantityString(R.plurals.ua_mc_description_deleted, count, count));
            mode.finish();

        } else if (item.getItemId() == R.id.select_all) {
            for (int i = 0; i < listView.getCount(); i++) {
                listView.setItemChecked(i, true);
            }
        }

        return true;
    }

    @Override
    public void onDestroyActionMode(@NonNull ActionMode mode) {
        if (actionModeListener != null) {
            actionModeListener.onActionModeDestroyed(mode);
        }
    }

    @NonNull
    private Set<String> getCheckedMessageIds() {
        final Set<String> messageIds = new HashSet<>();

        if (messageListFragment.getAbsListView() == null) {
            return messageIds;
        }

        final SparseBooleanArray checked = messageListFragment.getAbsListView().getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                Message message = messageListFragment.getMessage(checked.keyAt(i));
                if (message != null) {
                    messageIds.add(message.getMessageId());
                }
            }
        }

        return messageIds;
    }
}
