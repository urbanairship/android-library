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
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;

/**
 * Manages the message view pager and display messages
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MessageActivity extends ThemedActivity {

    private static final String FRAGMENT_TAG = "MessageFragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setDisplayHomeAsUpEnabled(true);

        String messageId = null;

        // Handle the "com.urbanairship.VIEW_RICH_PUSH_MESSAGE" intent action with the message
        // ID encoded in the intent's data in the form of "message:<MESSAGE_ID>
        if (getIntent() != null && getIntent().getData() != null && RichPushInbox.VIEW_MESSAGE_INTENT_ACTION.equals(getIntent().getAction())) {
            messageId = getIntent().getData().getSchemeSpecificPart();
        }

        RichPushMessage message = UAirship.shared().getInbox().getMessage(messageId);
        if (message == null) {
            finish();
            return;
        }

        MessageFragment messageFragment = (MessageFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (messageFragment == null) {
            messageFragment = MessageFragment.newInstance(messageId);
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, messageFragment, FRAGMENT_TAG)
                    .commit();
        }


        setTitle(message.getTitle());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
        }
        return false;
    }
}
