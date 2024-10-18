/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import com.urbanairship.Autopilot;
import com.urbanairship.UALog;
import com.urbanairship.UAirship;
import com.urbanairship.activity.ThemedActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import kotlin.Deprecated;

/**
 * Displays the Airship Message Center using {@link MessageCenterFragment}.
 *
 * @deprecated Use {@link com.urbanairship.messagecenter.ui.MessageCenterActivity} instead.
 */
@Deprecated(message = "Replace with ui.MessageCenterActivity")
public class MessageCenterActivity extends ThemedActivity {

    @Nullable
    private MessageCenterFragment messageCenterFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            UALog.e("MessageCenterActivity - unable to create activity, takeOff not called.");
            finish();
            return;
        }

        setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState != null) {
            messageCenterFragment = (MessageCenterFragment) getSupportFragmentManager().findFragmentByTag("MESSAGE_CENTER_FRAGMENT");
        }

         if (messageCenterFragment == null) {
             messageCenterFragment = MessageCenterFragment.newInstance(MessageCenter.parseMessageId(getIntent()));
             getSupportFragmentManager()
                     .beginTransaction()
                     .add(android.R.id.content, messageCenterFragment, "MESSAGE_CENTER_FRAGMENT")
                     .commitNow();
         }

        // Apply the default message center predicate
        messageCenterFragment.setPredicate(MessageCenter.shared().getPredicate());
    }


    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
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
