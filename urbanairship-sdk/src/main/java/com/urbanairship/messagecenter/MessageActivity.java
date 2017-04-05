/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentTransaction;
import android.view.MenuItem;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;

/**
 * Manages the message view pager and display messages
 */
public class MessageActivity extends ThemedActivity {

    private static final String FRAGMENT_TAG = "MessageFragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("MessageActivity - unable to create activity, takeOff not called.");
            finish();
            return;
        }

        setDisplayHomeAsUpEnabled(true);


        RichPushMessage message = getMessage(getIntent());

        if (message == null) {
            finish();
            return;
        }

        loadMessage(message);
    }

    @Nullable
    private RichPushMessage getMessage(Intent intent) {
        if (intent == null || intent.getData() == null || intent.getAction() == null) {
            return null;
        }

        String messageId = null;

        // Handle the "com.urbanairship.VIEW_RICH_PUSH_MESSAGE" intent action with the message
        // ID encoded in the intent's data in the form of "message:<MESSAGE_ID>
        if (RichPushInbox.VIEW_MESSAGE_INTENT_ACTION.equals(intent.getAction())) {
            messageId = intent.getData().getSchemeSpecificPart();
        }

        return UAirship.shared().getInbox().getMessage(messageId);
    }

    private void loadMessage(@NonNull RichPushMessage message) {

        MessageFragment previousMessageFragment = (MessageFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (previousMessageFragment == null || !message.getMessageId().equals(previousMessageFragment.getMessageId())) {

            FragmentTransaction transaction = getSupportFragmentManager()
                    .beginTransaction();

            if (previousMessageFragment != null) {
                transaction.remove(previousMessageFragment);
            }

            transaction.add(android.R.id.content, MessageFragment.newInstance(message.getMessageId()), FRAGMENT_TAG)
                       .commitNow();
        }

        setTitle(message.getTitle());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        RichPushMessage message = getMessage(intent);
        if (message != null) {
            loadMessage(message);
        }
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
