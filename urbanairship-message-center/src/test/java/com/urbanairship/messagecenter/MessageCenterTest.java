/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import static com.urbanairship.PrivacyManager.FEATURE_MESSAGE_CENTER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.job.JobInfo;
import com.urbanairship.push.PushListener;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.robolectric.shadows.ShadowApplication;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link MessageCenter}.
 */
@RunWith(AndroidJUnit4.class)
public class MessageCenterTest {

    private MessageCenter messageCenter;
    private ShadowApplication shadowApplication;
    private PrivacyManager privacyManager;
    private AirshipChannel channel;
    private PushManager pushManager;
    private Inbox inbox;
    private PushListener pushListener;
    private PrivacyManager.Listener privacyManagerListener;
    private Context context;
    MessageCenter.OnShowMessageCenterListener onShowMessageCenterListener = mock(MessageCenter.OnShowMessageCenterListener.class);


    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        PreferenceDataStore dataStore = PreferenceDataStore.inMemoryStore(context);

        privacyManager = mock(PrivacyManager.class);
        channel = mock(AirshipChannel.class);
        inbox = mock(Inbox.class);
        pushManager = mock(PushManager.class);
        this.messageCenter = new MessageCenter(context, dataStore, privacyManager, inbox, pushManager);
        shadowApplication = shadowOf((Application) context);

        when(privacyManager.isEnabled(FEATURE_MESSAGE_CENTER)).thenReturn(true);

        ArgumentCaptor<PushListener> pushListenerArgumentCaptor = ArgumentCaptor.forClass(PushListener.class);
        messageCenter.init();
        verify(pushManager).addInternalPushListener(pushListenerArgumentCaptor.capture());
        pushListener = pushListenerArgumentCaptor.getValue();

        ArgumentCaptor<PrivacyManager.Listener> privacyListenerArgumentCaptor = ArgumentCaptor.forClass(PrivacyManager.Listener.class);
        verify(privacyManager).addListener(privacyListenerArgumentCaptor.capture());
        privacyManagerListener = privacyListenerArgumentCaptor.getValue();
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

    @Test
    public void testPrivacyManagerListenerUpdatesEnabledState() {
        // Clear setup invocations
        Mockito.clearInvocations(pushManager);
        Mockito.clearInvocations(inbox);

        when(privacyManager.isEnabled(FEATURE_MESSAGE_CENTER)).thenReturn(false);
        privacyManagerListener.onEnabledFeaturesChanged();

        verify(inbox, timeout(100).times(1)).setEnabled(eq(false));
        verify(inbox, timeout(100).times(1)).updateEnabledState();
    }

    @Test
    public void testUpdateEnabledStateWhenEnabled() {
        // Clear setup invocations
        Mockito.clearInvocations(pushManager);
        Mockito.clearInvocations(inbox);

        when(privacyManager.isEnabled(FEATURE_MESSAGE_CENTER)).thenReturn(true);

        messageCenter.updateInboxEnabledState();

        verify(inbox).setEnabled(eq(true));
        verify(inbox).updateEnabledState();

        // Verify that we didn't re-add the push listener because message center was already started.
        verify(pushManager, never()).addInternalPushListener(any(PushListener.class));
    }

    @Test
    public void testUpdateEnabledStateWhenDisabled() {
        // Clear setup invocations
        Mockito.clearInvocations(pushManager);
        Mockito.clearInvocations(inbox);

        when(privacyManager.isEnabled(FEATURE_MESSAGE_CENTER)).thenReturn(false);

        messageCenter.updateInboxEnabledState();

        verify(inbox).setEnabled(eq(false));
        verify(inbox).updateEnabledState();

        // Verify that MessageCenter was torn down
        verify(inbox).tearDown();
        verify(pushManager).removePushListener(any(PushListener.class));
    }

    @Test
    public void testShowMessageCenterWhenDisabled() {
        when(privacyManager.isEnabled(FEATURE_MESSAGE_CENTER)).thenReturn(false);

        messageCenter.showMessageCenter();
        Intent intent = shadowApplication.getNextStartedActivity();

        assertNull(intent);
    }

    @Test
    public void testPerformJobWhenEnabled() {
        when(privacyManager.isEnabled(FEATURE_MESSAGE_CENTER)).thenReturn(true);

        UAirship airship = mock(UAirship.class);
        JobInfo jobInfo = mock(JobInfo.class);

        int result = messageCenter.onPerformJob(airship, jobInfo);
        assertEquals(JobInfo.JOB_FINISHED, result);

        verify(inbox).onPerformJob(eq(airship), eq(jobInfo));
    }

    @Test
    public void testPerformJobWhenDisabled() {
        when(privacyManager.isEnabled(FEATURE_MESSAGE_CENTER)).thenReturn(false);

        int result = messageCenter.onPerformJob(mock(UAirship.class), mock(JobInfo.class));
        assertEquals(JobInfo.JOB_FINISHED, result);

        verify(inbox, never()).onPerformJob(any(UAirship.class), any(JobInfo.class));
    }

    @Test
    public void testDeepLinkMessageCenter() {
        this.messageCenter.setOnShowMessageCenterListener(onShowMessageCenterListener);

        Uri deepLink = Uri.parse("uairship://message_center");
        assertTrue(messageCenter.onAirshipDeepLink(deepLink));

        verify(onShowMessageCenterListener).onShowMessageCenter(null);
    }

    @Test
    public void testDeepLinkMessageCenterTrailingSlash() {
        this.messageCenter.setOnShowMessageCenterListener(onShowMessageCenterListener);

        Uri deepLink = Uri.parse("uairship://message_center/");
        assertTrue(messageCenter.onAirshipDeepLink(deepLink));

        verify(onShowMessageCenterListener).onShowMessageCenter(null);
    }

    @Test
    public void testDeepLinkMessage() {
        this.messageCenter.setOnShowMessageCenterListener(onShowMessageCenterListener);

        Uri deepLink = Uri.parse("uairship://message_center/cool-message");
        assertTrue(messageCenter.onAirshipDeepLink(deepLink));

        verify(onShowMessageCenterListener).onShowMessageCenter("cool-message");
    }

    @Test
    public void testDeepLinkMessageTrailingSlash() {
        this.messageCenter.setOnShowMessageCenterListener(onShowMessageCenterListener);

        Uri deepLink = Uri.parse("uairship://message_center/cool-message/");
        assertTrue(messageCenter.onAirshipDeepLink(deepLink));

        verify(onShowMessageCenterListener).onShowMessageCenter("cool-message");
    }

    @Test
    public void testInvalidDeepLinks() {
        this.messageCenter.setOnShowMessageCenterListener(onShowMessageCenterListener);

        Uri wrongHost = Uri.parse("uairship://what/cool-message/");
        assertFalse(messageCenter.onAirshipDeepLink(wrongHost));

        Uri wrongArgs = Uri.parse("uairship://message_center/cool-message/what");
        assertFalse(messageCenter.onAirshipDeepLink(wrongArgs));

        verify(onShowMessageCenterListener, never()).onShowMessageCenter(ArgumentMatchers.<String>any());
    }
}
