/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.push.PushListener;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.shadows.ShadowApplication;

import java.util.HashMap;
import java.util.Map;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

/**
 * Tests for {@link MessageCenter}.
 */
@RunWith(AndroidJUnit4.class)
public class MessageCenterTest {

    private MessageCenter messageCenter;
    private ShadowApplication shadowApplication;
    private AirshipChannel channel;
    private PushManager pushManager;
    private Inbox inbox;
    private PushListener pushListener;
    private Context context;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        PreferenceDataStore dataStore = new PreferenceDataStore(context);

        channel = mock(AirshipChannel.class);
        inbox = mock(Inbox.class);
        pushManager = mock(PushManager.class);
        this.messageCenter = new MessageCenter(context, dataStore, inbox, pushManager);
        shadowApplication = shadowOf((Application) context);

        ArgumentCaptor<PushListener> pushListenerArgumentCaptor = ArgumentCaptor.forClass(PushListener.class);
        messageCenter.init();
        verify(pushManager).addInternalPushListener(pushListenerArgumentCaptor.capture());
        pushListener = pushListenerArgumentCaptor.getValue();
    }

    @Test
    public void testShowMessageCenter() {
        this.messageCenter.showMessageCenter();

        Intent intent = shadowApplication.getNextStartedActivity();

        assertEquals(MessageCenter.VIEW_MESSAGE_CENTER_INTENT_ACTION, intent.getAction());
        assertEquals(context.getPackageName(), intent.getPackage());
    }

    @Test
    public void testShowMessageCenterListener() {
        MessageCenter.OnShowMessageCenterListener listener = mock(MessageCenter.OnShowMessageCenterListener.class);
        when(listener.onShowMessageCenter(null)).thenReturn(true);
        this.messageCenter.setOnShowMessageCenterListener(listener);

        this.messageCenter.showMessageCenter();
        verify(listener).onShowMessageCenter(null);

        assertNull(shadowApplication.getNextStartedActivity());
    }

    @Test
    public void testShowMessageCenterListenerDefaultBehavior() {
        MessageCenter.OnShowMessageCenterListener listener = mock(MessageCenter.OnShowMessageCenterListener.class);
        when(listener.onShowMessageCenter(null)).thenReturn(false);
        this.messageCenter.setOnShowMessageCenterListener(listener);

        this.messageCenter.showMessageCenter();

        Intent intent = shadowApplication.getNextStartedActivity();

        assertEquals(MessageCenter.VIEW_MESSAGE_CENTER_INTENT_ACTION, intent.getAction());
        assertEquals(context.getPackageName(), intent.getPackage());
    }

    @Test
    public void testShowMessage() {
        this.messageCenter.showMessageCenter("id");

        Intent intent = shadowApplication.getNextStartedActivity();

        assertEquals(MessageCenter.VIEW_MESSAGE_INTENT_ACTION, intent.getAction());
        assertEquals("message:id", intent.getData().toString());
        assertEquals(context.getPackageName(), intent.getPackage());
    }

    @Test
    public void testShowMessageListener() {
        MessageCenter.OnShowMessageCenterListener listener = mock(MessageCenter.OnShowMessageCenterListener.class);
        when(listener.onShowMessageCenter("id")).thenReturn(true);
        this.messageCenter.setOnShowMessageCenterListener(listener);


        this.messageCenter.showMessageCenter("id");
        verify(listener).onShowMessageCenter("id");

        assertNull(shadowApplication.getNextStartedActivity());
    }

    @Test
    public void testShowMessageListenerDefaultBehavior() {
        MessageCenter.OnShowMessageCenterListener listener = mock(MessageCenter.OnShowMessageCenterListener.class);
        when(listener.onShowMessageCenter("id")).thenReturn(false);
        this.messageCenter.setOnShowMessageCenterListener(listener);

        this.messageCenter.showMessageCenter("id");

        Intent intent = shadowApplication.getNextStartedActivity();

        assertEquals(MessageCenter.VIEW_MESSAGE_INTENT_ACTION, intent.getAction());
        assertEquals("message:id", intent.getData().toString());
        assertEquals(context.getPackageName(), intent.getPackage());
    }


    @Test
    public void testParseMessageId() {
        Intent intent = new Intent(MessageCenter.VIEW_MESSAGE_INTENT_ACTION, Uri.parse("message:cool"));
        assertEquals("cool", MessageCenter.parseMessageId(intent));

        intent.setAction(MessageCenter.VIEW_MESSAGE_CENTER_INTENT_ACTION);
        assertEquals("cool", MessageCenter.parseMessageId(intent));

        intent.setAction("SOME OTHER ACTION");
        assertNull(MessageCenter.parseMessageId(intent));

        intent.setAction(MessageCenter.VIEW_MESSAGE_CENTER_INTENT_ACTION);
        intent.setData(Uri.parse("WHAT"));
        assertNull(MessageCenter.parseMessageId(intent));
    }

    @Test
    public void testPushListener() {
        Map<String, String> pushData = new HashMap<>();
        pushData.put(PushMessage.EXTRA_RICH_PUSH_ID, "messageID");
        PushMessage message = new PushMessage(pushData);

        when(inbox.getMessage("messageID")).thenReturn(null);

        pushListener.onPushReceived(message, true);

        verify(inbox).fetchMessages();
    }

    @Test
    public void testUrlConfigUpdateCallback() {
        messageCenter.onUrlConfigUpdated();

        verify(inbox).dispatchUpdateUserJob(true);
    }
}
