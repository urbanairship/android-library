/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushInbox;

/**
 * Displays the Urban Airship Message Center using {@link MessageCenterFragment}.
 */
public class MessageCenterActivity extends ThemedActivity {

    private MessageCenterFragment messageCenterFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("MessageCenterActivity - unable to create activity, takeOff not called.");
            finish();
            return;
        }

        setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            messageCenterFragment = MessageCenterFragment.newInstance(getMessageId(getIntent()));
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, messageCenterFragment, "MESSAGE_CENTER_FRAGMENT")
                    .commitNow();
        } else {
            messageCenterFragment = (MessageCenterFragment) getSupportFragmentManager().findFragmentByTag("MESSAGE_CENTER_FRAGMENT");
        }

        // Apply the default message center predicate
        messageCenterFragment.setPredicate(UAirship.shared().getMessageCenter().getPredicate());
    }

    /**
     * Gets the message ID from an intent.
     *
     * @param intent The intent.
     * @return The message ID if its available on the intent, otherwise {@code null}.
     */
    private String getMessageId(Intent intent) {
        if (intent == null || intent.getData() == null || intent.getAction() == null) {
            return null;
        }

        switch (intent.getAction()) {
            case RichPushInbox.VIEW_INBOX_INTENT_ACTION:
            case RichPushInbox.VIEW_MESSAGE_INTENT_ACTION:
                return intent.getData().getSchemeSpecificPart();

            default:
                return null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        String messageId = getMessageId(intent);
        if (messageId != null) {
            messageCenterFragment.setMessageID(messageId);
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


