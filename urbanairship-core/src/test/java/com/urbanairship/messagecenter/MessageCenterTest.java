/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Intent;
import android.net.Uri;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowApplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

/**
 * Tests for {@link MessageCenter}.
 */
public class MessageCenterTest extends BaseTestCase {

    private MessageCenter messageCenter;
    private ShadowApplication shadowApplication;

    @Before
    public void setup() {
        this.messageCenter = new MessageCenter(getApplication(), getApplication().preferenceDataStore);
        shadowApplication = shadowOf(getApplication());
    }

    @Test
    public void testShowMessageCenter() {
        this.messageCenter.showMessageCenter();

        Intent intent = shadowApplication.getNextStartedActivity();

        assertEquals(MessageCenter.VIEW_MESSAGE_CENTER_INTENT_ACTION, intent.getAction());
        assertEquals(getApplication().getPackageName(), intent.getPackage());
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
        assertEquals(getApplication().getPackageName(), intent.getPackage());
    }

    @Test
    public void testShowMessage() {
        this.messageCenter.showMessageCenter("id");

        Intent intent = shadowApplication.getNextStartedActivity();

        assertEquals(MessageCenter.VIEW_MESSAGE_INTENT_ACTION, intent.getAction());
        assertEquals("message:id", intent.getData().toString());
        assertEquals(getApplication().getPackageName(), intent.getPackage());
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
        assertEquals(getApplication().getPackageName(), intent.getPackage());
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

}
