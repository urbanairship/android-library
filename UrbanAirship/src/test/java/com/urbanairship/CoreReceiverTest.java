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

package com.urbanairship;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.iam.InAppMessage;
import com.urbanairship.push.iam.InAppMessageManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class CoreReceiverTest {

    Context context;

    @Before
    public void before() {
        context = mock(Context.class);
        when(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(mock(NotificationManager.class));
    }
    /**
     * Test when a notification is opened it clears the pending in-app message.
     */
    @Test
    public void testOpenProxyClearPendingInAppMessage() {
        InAppMessage inAppMessage = new InAppMessage.Builder()
                .setAlert("oh hi")
                .setExpiry(1000l)
                .create();

        // Set the pending in-app message
        InAppMessageManager inAppMessageManager = UAirship.shared().getInAppMessageManager();
        inAppMessageManager.setPendingMessage(inAppMessage);

        // Create the push message with the in-app message
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_IN_APP_MESSAGE, inAppMessage.toJsonValue().toString());
        PushMessage message = new PushMessage(pushBundle);

        // Create the intent
        Intent intent = new Intent().putExtra(PushManager.EXTRA_PUSH_MESSAGE, message);

        // Call the proxy
        CoreReceiver.handleNotificationOpenedProxy(context, intent);

        assertNull(inAppMessageManager.getPendingMessage());
    }


    /**
     * Test when a notification is opened it clears the pending in-app message
     */
    @Test
    public void testButtonClickProxyClearPendingInAppNotification() {
        InAppMessage inAppMessage = new InAppMessage.Builder()
                .setAlert("oh hi")
                .setExpiry(1000l)
                .create();

        // Set the pending in-app message
        InAppMessageManager inAppMessageManager = UAirship.shared().getInAppMessageManager();
        inAppMessageManager.setPendingMessage(inAppMessage);

        // Create the push message with the in-app message
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_IN_APP_MESSAGE, inAppMessage.toJsonValue().toString());
        PushMessage message = new PushMessage(pushBundle);

        // Create the intent
        Intent intent = new Intent()
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE, message)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "button id");

        // Call the proxy
        CoreReceiver.handleNotificationButtonOpenedProxy(context, intent);

        assertNull(inAppMessageManager.getPendingMessage());
    }
}
