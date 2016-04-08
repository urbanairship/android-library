/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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
            public boolean matches(Object argument) {
                if (!(argument instanceof InstallAttributionEvent)) {
                    return false;
                }

                InstallAttributionEvent event = (InstallAttributionEvent) argument;
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
