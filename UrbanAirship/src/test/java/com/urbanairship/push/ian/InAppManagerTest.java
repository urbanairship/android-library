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

package com.urbanairship.push.ian;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

@RunWith(RobolectricGradleTestRunner.class)
public class InAppManagerTest {

    private InAppManager inAppManager;

    @Before
    public void before() {
        inAppManager = new InAppManager(TestApplication.getApplication().preferenceDataStore);
    }

    @Test
    public void testSetPendingNotification() {
        InAppNotification notification = new InAppNotification.Builder().setExpiry(10000l).setAlert("oh hi").create();
        inAppManager.setPendingNotification(notification);

        assertEquals(notification, inAppManager.getPendingNotification());
    }

    @Test
    public void testClearPendingNotification() {
        InAppNotification notification = new InAppNotification.Builder().setExpiry(10000l).setAlert("oh hi").create();
        inAppManager.setPendingNotification(notification);

        // Clear it
        inAppManager.setPendingNotification(null);
        assertNull(inAppManager.getPendingNotification());
    }

}
