/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Intent;
import android.os.Bundle;
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

    private String messageId;

    private RichPushInbox.Listener updateMessageListener = new RichPushInbox.Listener() {
        @Override
        public void onInboxUpdated() {
            if (messageId != null) {
                updateTitle(messageId);
            }
        }
    };

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

        if (savedInstanceState == null) {
            messageId = parseMessageId(getIntent());
        } else {
            messageId = savedInstanceState.getString("messageId");
        }


        if (messageId == null) {
            finish();
            return;
        }

        loadMessage();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("messageId", messageId);
    }

    @Nullable
    private String parseMessageId(Intent intent) {
        if (intent == null || intent.getData() == null || intent.getAction() == null) {
            return null;
        }

        String messageId = null;

        // Handle the "com.urbanairship.VIEW_RICH_PUSH_MESSAGE" intent action with the message
        // ID encoded in the intent's data in the form of "message:<MESSAGE_ID>
        if (RichPushInbox.VIEW_MESSAGE_INTENT_ACTION.equals(intent.getAction())) {
            messageId = intent.getData().getSchemeSpecificPart();
        }

        return messageId;
    }

    /**
     * Loads the message.
     */
    private void loadMessage() {
        if (messageId == null) {
            return;
        }

        MessageFragment previousMessageFragment = (MessageFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (previousMessageFragment == null || !messageId.equals(previousMessageFragment.getMessageId())) {

            FragmentTransaction transaction = getSupportFragmentManager()
                    .beginTransaction();

            if (previousMessageFragment != null) {
                transaction.remove(previousMessageFragment);
            }

            transaction.add(android.R.id.content, MessageFragment.newInstance(messageId), FRAGMENT_TAG)
                       .commitNow();
        }

        updateTitle(messageId);
    }


    @Override
    protected void onStart() {
        super.onStart();
        UAirship.shared().getInbox().addListener(updateMessageListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        UAirship.shared().getInbox().removeListener(updateMessageListener);
    }

    /**
     * Updates the title from the given message Id.
     *
     * @param messageId The message Id.
     */
    private void updateTitle(String messageId) {
        RichPushMessage message = UAirship.shared().getInbox().getMessage(messageId);
        if (message == null) {
            setTitle(null);
        } else {
            setTitle(message.getTitle());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        String newMessageId = parseMessageId(intent);
        if (newMessageId != null) {
            messageId = newMessageId;
            loadMessage();
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
