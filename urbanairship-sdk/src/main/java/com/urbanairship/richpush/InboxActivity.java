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
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;

import com.urbanairship.R;
import com.urbanairship.UAirship;

/**
 * Displays the Urban Airship Message Center using {@link InboxFragment}.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class InboxActivity extends FragmentActivity {

    InboxFragment inboxFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TypedArray a = obtainStyledAttributes(R.styleable.Theme);
        if (a.hasValue(R.styleable.Theme_inboxActivityStyle)) {
            setTheme(a.getResourceId(R.styleable.Theme_inboxActivityStyle, -1));
        } else {
            setTheme(R.style.InboxActivityStyle);
        }

        a.recycle();

        super.onCreate(savedInstanceState);
        super.setContentView(R.layout.ua_activity_inbox);

        if (Build.VERSION.SDK_INT >= 14 && getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }

        final MessagePagerFragment messagePagerFragment = (MessagePagerFragment) getSupportFragmentManager().findFragmentById(R.id.message_pager_fragment);
        inboxFragment = (InboxFragment) getSupportFragmentManager().findFragmentById(R.id.inbox_fragment);
        inboxFragment.getAbsListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                RichPushMessage message = inboxFragment.getMessage(position);

                if (messagePagerFragment != null) {
                    messagePagerFragment.setCurrentMessage(message.getMessageId());
                } else {
                    UAirship.shared().getInbox().startMessageActivity(message.getMessageId());
                }
            }
        });

        inboxFragment.getAbsListView().setMultiChoiceModeListener(new InboxMultiChoiceModeListener(inboxFragment));
        inboxFragment.getAbsListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
        }
        return false;
    }
}


