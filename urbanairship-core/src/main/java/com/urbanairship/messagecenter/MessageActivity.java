/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.activity.ThemedActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;

/**
 * Manages the message view pager and display messages
 */
public class MessageActivity extends ThemedActivity {

    private static final String FRAGMENT_TAG = "MessageFragment";

    private String messageId;

    private final InboxListener updateMessageListener = new InboxListener() {
        @Override
        public void onInboxUpdated() {
            if (messageId != null) {
                updateTitle(messageId);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("MessageActivity - unable to create activity, takeOff not called.");
            finish();
            return;
        }

        setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            messageId = MessageCenter.parseMessageId(getIntent());
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("messageId", messageId);
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
        MessageCenter.shared().getInbox().addListener(updateMessageListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MessageCenter.shared().getInbox().removeListener(updateMessageListener);
    }

    /**
     * Updates the title from the given message Id.
     *
     * @param messageId The message Id.
     */
    private void updateTitle(@Nullable String messageId) {
        Message message = MessageCenter.shared().getInbox().getMessage(messageId);
        if (message == null) {
            setTitle(null);
        } else {
            setTitle(message.getTitle());
        }
    }

    @SuppressLint("UnknownNullness")
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String newMessageId = MessageCenter.parseMessageId(intent);
        if (newMessageId != null) {
            messageId = newMessageId;
            loadMessage();
        }
    }

    @Override
    @SuppressLint("UnknownNullness")
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
        }
        return false;
    }

}
