/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.MenuItem;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * Displays the Airship Message Center using {@link MessageCenterFragment}.
 */
public class MessageCenterActivity extends ThemedActivity {

    private MessageCenterFragment messageCenterFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("MessageCenterActivity - unable to create activity, takeOff not called.");
            finish();
            return;
        }

        setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            messageCenterFragment = MessageCenterFragment.newInstance(MessageCenter.parseMessageId(getIntent()));
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


    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        String messageId = MessageCenter.parseMessageId(intent);
        if (messageId != null) {
            messageCenterFragment.setMessageID(messageId);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
        }
        return false;
    }

}


