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
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;

import com.urbanairship.R;
import com.urbanairship.UAirship;

import java.util.HashSet;
import java.util.Set;

/**
 * The default {@link android.widget.AbsListView.MultiChoiceModeListener} for the {@link InboxFragment}
 * to handle multiple selection.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class InboxMultiChoiceModeListener implements AbsListView.MultiChoiceModeListener {

    private final InboxFragment inboxFragment;

    /**
     * Default constructor.
     * @param inboxFragment The {@link InboxFragment}.
     */
    public InboxMultiChoiceModeListener(InboxFragment inboxFragment) {
        this.inboxFragment = inboxFragment;
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        int count = inboxFragment.getAbsListView().getCheckedItemCount();
        mode.setTitle(inboxFragment.getResources().getQuantityString(R.plurals.ua_selected_count, count, count));
        inboxFragment.getAbsListView().getAdapter();
        mode.invalidate();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.ua_mc_action_mode, menu);
        int count = inboxFragment.getAbsListView().getCheckedItemCount();
        mode.setTitle(inboxFragment.getResources().getQuantityString(R.plurals.ua_selected_count, count, count));

        boolean containsUnreadMessage = false;
        final SparseBooleanArray checked = inboxFragment.getAbsListView().getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                RichPushMessage message = inboxFragment.getMessage(checked.keyAt(i));
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
        final SparseBooleanArray checked = inboxFragment.getAbsListView().getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                RichPushMessage message = inboxFragment.getMessage(checked.keyAt(i));
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
            int count = inboxFragment.getAbsListView().getCount();
            for (int i = 0; i < count; i++) {
                inboxFragment.getAbsListView().setItemChecked(i, true);
            }
        }

        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {

    }

    private Set<String> getCheckedMessageIds() {
        final SparseBooleanArray checked = inboxFragment.getAbsListView().getCheckedItemPositions();
        final Set<String> messageIds = new HashSet<>();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i)) {
                RichPushMessage message = inboxFragment.getMessage(checked.keyAt(i));
                if (message != null) {
                    messageIds.add(message.getMessageId());
                }
            }
        }

        return messageIds;
    }
}