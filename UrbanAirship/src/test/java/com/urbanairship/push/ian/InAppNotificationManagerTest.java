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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Build;
import android.os.Looper;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;
import com.urbanairship.analytics.Analytics;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class InAppNotificationManagerTest {

    private InAppNotificationManager inAppNotificationManager;
    private InAppNotification notification;

    private Activity mockActivity;
    private Analytics mockAnalytics;

    @Before
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void before() {
        mockActivity = mock(Activity.class);
        when(mockActivity.getFragmentManager()).thenReturn(mock(FragmentManager.class));

        mockAnalytics = mock(Analytics.class, CALLS_REAL_METHODS);
        TestApplication.getApplication().setAnalytics(mockAnalytics);

        notification = new InAppNotification.Builder()
                .setExpiry(10000l)
                .setAlert("oh hi")
                .setId("id")
                .create();

        inAppNotificationManager = new InAppNotificationManager(TestApplication.getApplication().preferenceDataStore);
    }

    /**
     * Test setting the pending notification persists.
     */
    @Test
    public void testSetPendingNotification() {
        inAppNotificationManager.setPendingNotification(notification);

        assertEquals(notification, inAppNotificationManager.getPendingNotification());
    }

    /**
     * Test clearing the pending notification.
     */
    @Test
    public void testClearPendingNotification() {
        inAppNotificationManager.setPendingNotification(notification);

        // Clear it
        inAppNotificationManager.setPendingNotification(null);
        assertNull(inAppNotificationManager.getPendingNotification());
    }

    /**
     * Test setting display ASAP enabled.
     */
    @Test
    public void testSetDisplayAsapEnabled() {
        inAppNotificationManager.setDisplayAsapEnabled(true);
        assertTrue(inAppNotificationManager.isDisplayAsapEnabled());

        inAppNotificationManager.setDisplayAsapEnabled(false);
        assertFalse(inAppNotificationManager.isDisplayAsapEnabled());
    }

    /**
     * Test setting auto display enabled.
     */
    @Test
    public void testSetAutoDisplayEnabled() {
        inAppNotificationManager.setAutoDisplayEnabled(true);
        assertTrue(inAppNotificationManager.isAutoDisplayEnabled());

        inAppNotificationManager.setDisplayAsapEnabled(false);
        assertFalse(inAppNotificationManager.isDisplayAsapEnabled());
    }

    /**
     * Test showing the pending notification
     */
    @Test
    @Config(emulateSdk = 18)
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void testShowPendingNotification() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Set and show the pending notification
        inAppNotificationManager.setPendingNotification(notification);
        assertTrue(inAppNotificationManager.showPendingNotification(mockActivity, android.R.id.custom, android.R.animator.fade_in, android.R.animator.fade_out));

        // Verify a fragment was added
        verify(transaction).setCustomAnimations(android.R.animator.fade_in, 0);
        verify(transaction).add(eq(android.R.id.custom), argThat(new ArgumentMatcher<InAppNotificationFragment>() {
            @Override
            public boolean matches(Object o) {
                if (o instanceof InAppNotificationFragment) {
                    InAppNotificationFragment fragment = (InAppNotificationFragment) o;
                    fragment.onCreate(null);

                    return fragment.getDismissAnimation() == android.R.animator.fade_out &&
                            fragment.getNotification().equals(notification);
                }
                return false;
            }
        }), eq("com.urbanairship.in_app_fragment"));

        // Verify we added a display event
        verify(mockAnalytics, times(1)).addEvent(any(DisplayEvent.class));
    }

    /**
     * Test showing the pending notification when its already showing does not show a second time.
     */
    @Test
    @Config(emulateSdk = 18)
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void testShowPendingNotificationAlreadyShowing() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Set and show the pending notification
        inAppNotificationManager.setPendingNotification(notification);
        assertTrue(inAppNotificationManager.showPendingNotification(mockActivity));

        // Call it again
        assertFalse(inAppNotificationManager.showPendingNotification(mockActivity));

        // Verify a fragment was added only once (default is times(1))
        verify(transaction).setCustomAnimations(anyInt(), anyInt());
        verify(transaction).add(anyInt(), any(InAppNotificationFragment.class), anyString());

        // Verify we only added a single display event
        verify(mockAnalytics, times(1)).addEvent(any(DisplayEvent.class));
    }

    /**
     * Test when display ASAP is enabled the manager will attempt to display the pending notification when its
     * set.
     */
    @Test
    @Config(emulateSdk = 18)
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void testSetPendingNotificationShowAsap() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Enable showing notification ASAP
        inAppNotificationManager.setDisplayAsapEnabled(true);

        // Set the current, resumed activity
        inAppNotificationManager.onActivityResumed(mockActivity);

        // Set the pending notification
        inAppNotificationManager.setPendingNotification(notification);

        runMainLooperTasks();

        // Verify a fragment was added
        verify(transaction).setCustomAnimations(anyInt(), eq(0));
        verify(transaction).add(eq(android.R.id.content), any(InAppNotificationFragment.class), eq("com.urbanairship.in_app_fragment"));
    }


    /**
     * Test when display ASAP is enabled the manager will attempt to display the pending notification when the
     * next activity is resumed.
     */
    @Test
    @Config(emulateSdk = 18)
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void testActivityResumedShowAsap() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Set the pending notification before setting display ASAP enabled
        inAppNotificationManager.setPendingNotification(notification);

        // Enable showing notification ASAP
        inAppNotificationManager.setDisplayAsapEnabled(true);

        // Set the current, resumed activity
        inAppNotificationManager.onActivityResumed(mockActivity);

        runMainLooperTasks();

        // Verify a fragment was added
        verify(transaction).setCustomAnimations(anyInt(), eq(0));
        verify(transaction).add(eq(android.R.id.content), any(InAppNotificationFragment.class), eq("com.urbanairship.in_app_fragment"));
    }

    /**
     * Test when the app foregrounds it tries to show the pending notification on next activity resume.
     */
    @Test
    @Config(emulateSdk = 18)
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void testActivityResume() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Set the pending notification before setting show ASAP enabled
        inAppNotificationManager.setPendingNotification(notification);

        // Notify foreground
        inAppNotificationManager.onForeground();

        // Set the current, resumed activity
        inAppNotificationManager.onActivityResumed(mockActivity);

        runMainLooperTasks();

        // Verify a fragment was added
        verify(transaction).setCustomAnimations(anyInt(), eq(0));
        verify(transaction).add(eq(android.R.id.content), any(InAppNotificationFragment.class), eq("com.urbanairship.in_app_fragment"));
    }

    /**
     * Test when an InAppNotificationFragment resumes and its no the last resumed fragment it will
     * be dismissed.
     */
    @Test
    @Config(emulateSdk = 18)
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void testOnInAppNotificationFragmentResumed() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Set and show the pending notification
        inAppNotificationManager.setPendingNotification(notification);

        // Show it
        assertTrue(inAppNotificationManager.showPendingNotification(mockActivity, android.R.id.custom, android.R.animator.fade_in, android.R.animator.fade_out));

        // Try to resume a different fragment
        InAppNotificationFragment fragment = mock(InAppNotificationFragment.class);
        inAppNotificationManager.onInAppNotificationFragmentResumed(fragment);

        // Should dismiss it since its not the current fragment
        verify(fragment).dismiss(false);
    }

    /**
     * Test finishing the pending notification clears it.
     */
    @Test
    public void testOnInAppNotificationFinished() {
        // Set and show the pending notification
        inAppNotificationManager.setPendingNotification(notification);

        // Notify the pending notification is finished
        inAppNotificationManager.onInAppNotificationFinished(notification);

        assertNull(inAppNotificationManager.getPendingNotification());
    }

    /**
     * Helper method to run all the tasks in the main looper.
     */
    private static void runMainLooperTasks() {
        // Get the looper
        ShadowLooper looper = Robolectric.shadowOf(Looper.getMainLooper());

        // Run any tasks in its queue
        looper.runToEndOfTasks();
    }
}
