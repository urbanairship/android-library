/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.content.Context;
import android.content.Intent;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class InstallReceiverTest extends BaseTestCase {

    InstallReceiver receiver;
    Context context;
    Analytics mockAnalytics;

    @Before
    public void setup() {
        receiver = new InstallReceiver();
        context = TestApplication.getApplication();

        mockAnalytics = mock(Analytics.class);
        TestApplication.getApplication().setAnalytics(mockAnalytics);
    }

    /**
     * Test the referrer action creates an install attribution event.
     */
    @Test
    public void testCreateInstallAttributionEvent() {
        Intent intent = new Intent("com.android.vending.INSTALL_REFERRER")
                .putExtra("referrer", "some value");

        receiver.onReceive(context, intent);

        verify(mockAnalytics).addEvent(argThat(new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Event event) {
                if (!(event instanceof InstallAttributionEvent)) {
                    return false;
                }

                return "some value".equals(event.getEventData().opt("google_play_referrer").getString());
            }
        }));
    }

    /**
     * Test missing referrer does not create an install attribution event.
     */
    @Test
    public void testMissingReferrer() {
        Intent intent = new Intent("com.android.vending.INSTALL_REFERRER");

        receiver.onReceive(context, intent);

        verifyZeroInteractions(mockAnalytics);
    }

    /**
     * Test invalid referrer action does not create an install attribution event.
     */
    @Test
    public void testInvalidAction() {
        Intent intent = new Intent("action")
                .putExtra("referrer", "some value");

        receiver.onReceive(context, intent);

        verifyZeroInteractions(mockAnalytics);
    }
}
