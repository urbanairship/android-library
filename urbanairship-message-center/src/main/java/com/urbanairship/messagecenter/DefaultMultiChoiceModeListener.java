/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.res.Resources;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;

import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * The default {@link AbsListView.MultiChoiceModeListener} for the {@link MessageListFragment}
 * to handle multiple selection.
 *
 * @hide
 */
public class DefaultMultiChoiceModeListener implements AbsListView.MultiChoiceModeListener {

    private final MessageListFragment messageListFragment;

    /**
     * Default constructor.
     *
     * @param messageListFragment The {@link MessageListFragment}.
     */
    public DefaultMultiChoiceModeListener(@NonNull MessageListFragment messageListFragment) {
        this.messageListFragment = messageListFragment;
    }

    @Override
    public void onItemCheckedStateChanged(@NonNull ActionMode mode, int position, long id, boolean checked) {
        return;
    }

    @Override
    public boolean onCreateActionMode(@NonNull ActionMode mode, @NonNull Menu menu) {
        return false;
    }

    @Override
    public boolean onPrepareActionMode(@NonNull ActionMode mode, @NonNull Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(@NonNull ActionMode mode, @NonNull MenuItem item) {
        return false;
    }

    @Override
    public void onDestroyActionMode(@NonNull ActionMode mode) {
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
