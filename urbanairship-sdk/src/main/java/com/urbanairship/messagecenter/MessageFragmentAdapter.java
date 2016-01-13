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

package com.urbanairship.messagecenter;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import com.urbanairship.richpush.RichPushMessage;

import java.util.List;

/**
 * Pager adapter that manages the message fragments. Activities that attach
 * this fragment must implement MessageFragmentAdapter.Listener.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MessageFragmentAdapter extends FragmentStatePagerAdapter {

    private List<RichPushMessage> messages;

    public MessageFragmentAdapter(FragmentManager manager) {
        super(manager);
    }

    @Override
    public Fragment getItem(int position) {
        if (messages == null || position >= messages.size()) {
            return null;
        }
        String messageId = messages.get(position).getMessageId();
        return MessageFragment.newInstance(messageId);
    }

    @Override
    public int getCount() {
        if (messages == null) {
            return 0;
        }
        return messages.size();
    }

    @Override
    public int getItemPosition(Object object) {
        return POSITION_NONE;
    }

    /**
     * Set the list of rich push messages
     * @param messages The current list of rich push messages to display
     */
    public void setRichPushMessages(List<RichPushMessage> messages) {
        this.messages = messages;
        this.notifyDataSetChanged();
    }
}
