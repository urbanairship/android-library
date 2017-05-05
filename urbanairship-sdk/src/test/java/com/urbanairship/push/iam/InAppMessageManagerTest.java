/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Looper;
import android.view.ViewGroup;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.analytics.EventTestUtils;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.UUID;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InAppMessageManagerTest extends BaseTestCase {

    private InAppMessageManager inAppMessageManager;
    private InAppMessage message;

    private Activity mockActivity;
    private Analytics mockAnalytics;

    @Before
    public void before() {
        mockActivity = mock(Activity.class);
        when(mockActivity.getFragmentManager()).thenReturn(mock(FragmentManager.class));
        when(mockActivity.findViewById(android.R.id.content)).thenReturn(mock(ViewGroup.class));

        mockAnalytics = mock(Analytics.class);
        TestApplication.getApplication().setAnalytics(mockAnalytics);

        message = new InAppMessage.Builder()
                .setAlert("oh hi")
                .setId("id")
                .setExpiry(Long.MAX_VALUE / 1000 * 1000) // Work around for precision loss issue
                .create();

        inAppMessageManager = new InAppMessageManager(TestApplication.getApplication().preferenceDataStore, new TestActivityMonitor());
    }

    /**
     * Test setting the pending in-app message persists.
     */
    @Test
    public void testSetPendingMessage() {
        inAppMessageManager.setPendingMessage(message);

        assertEquals(message, inAppMessageManager.getPendingMessage());
    }

    /**
     * Test clearing the pending in-app message.
     */
    @Test
    public void testClearPendingMessage() {
        inAppMessageManager.setPendingMessage(message);

        // Clear it
        inAppMessageManager.setPendingMessage(null);
        assertNull(inAppMessageManager.getPendingMessage());
    }

    /**
     * Test setting display ASAP enabled.
     */
    @Test
    public void testSetDisplayAsapEnabled() {
        inAppMessageManager.setDisplayAsapEnabled(true);
        assertTrue(inAppMessageManager.isDisplayAsapEnabled());

        inAppMessageManager.setDisplayAsapEnabled(false);
        assertFalse(inAppMessageManager.isDisplayAsapEnabled());
    }

    /**
     * Test setting auto display enabled.
     */
    @Test
    public void testSetAutoDisplayEnabled() {
        inAppMessageManager.setAutoDisplayEnabled(true);
        assertTrue(inAppMessageManager.isAutoDisplayEnabled());

        inAppMessageManager.setDisplayAsapEnabled(false);
        assertFalse(inAppMessageManager.isDisplayAsapEnabled());
    }

    /**
     * Test showing the pending in-app message.
     */
    @Test
    public void testShowPendingMessage() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);
        when(mockActivity.findViewById(android.R.id.custom)).thenReturn(mock(ViewGroup.class));

        // Set and show the pending in-app message
        inAppMessageManager.setPendingMessage(message);
        assertTrue(inAppMessageManager.showPendingMessage(mockActivity, android.R.id.custom, android.R.animator.fade_in, android.R.animator.fade_out));

        // Verify a fragment was added
        verify(transaction).setCustomAnimations(android.R.animator.fade_in, 0);
        verify(transaction).add(eq(android.R.id.custom), argThat(new ArgumentMatcher<InAppMessageFragment>() {
            @Override
            public boolean matches(InAppMessageFragment fragment) {
                fragment.onCreate(null);

                return fragment.getDismissAnimation() == android.R.animator.fade_out &&
                        fragment.getMessage().equals(message);
            }
        }), eq("com.urbanairship.in_app_fragment"));

        // Verify we added a display event
        verify(mockAnalytics, times(1)).addEvent(any(DisplayEvent.class));
    }

    /**
     * Test showing the pending in-app message when its expired should result in a resolution event
     * and the pending in-app message to not be displayed.
     */
    @Test
    public void testShowExpiredPendingMessage() {

        final InAppMessage expired = new InAppMessage.Builder()
                .setExpiry(0l)
                .setId(UUID.randomUUID().toString())
                .create();

        // Set the pending in-app message
        inAppMessageManager.setPendingMessage(expired);

        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Assert false - did not display
        assertFalse(inAppMessageManager.showPendingMessage(mockActivity));

        // The pending in-app message should be removed
        assertNull(inAppMessageManager.getPendingMessage());

        // Verify the right event was added
        verify(mockAnalytics).addEvent(argThat(new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Event event) {
                if (!(event instanceof ResolutionEvent)) {
                    return false;
                }

                try {
                    EventTestUtils.validateEventValue(event, "id", expired.getId());
                    EventTestUtils.validateNestedEventValue(event, "resolution", "type", "expired");
                } catch (JSONException e) {
                    return false;
                }
                return true;
            }
        }));
    }

    /**
     * Test showing the pending in-app message when its already showing does not show a second time.
     */
    @Test
    public void testShowPendingMessageAlreadyShowing() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Set and show the pending in-app message
        inAppMessageManager.setPendingMessage(message);
        assertTrue(inAppMessageManager.showPendingMessage(mockActivity));

        // Call it again
        assertFalse(inAppMessageManager.showPendingMessage(mockActivity));

        // Verify a fragment was added only once (default is times(1))
        verify(transaction).setCustomAnimations(anyInt(), anyInt());
        verify(transaction).add(anyInt(), any(InAppMessageFragment.class), anyString());

        // Verify we only added a single display event
        verify(mockAnalytics, times(1)).addEvent(any(DisplayEvent.class));
    }

    /**
     * Test when display ASAP is enabled the manager will attempt to display the pending in-app message when its
     * set.
     */
    @Test
    public void testSetPendingMessageShowAsap() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Enable showing message ASAP
        inAppMessageManager.setDisplayAsapEnabled(true);

        // Set the current, resumed activity
        inAppMessageManager.onActivityResumed(mockActivity);

        // Set the pending in-app message
        inAppMessageManager.setPendingMessage(message);

        runMainLooperTasks();

        // Verify a fragment was added
        verify(transaction).setCustomAnimations(anyInt(), eq(0));
        verify(transaction).add(eq(android.R.id.content), any(InAppMessageFragment.class), eq("com.urbanairship.in_app_fragment"));
    }


    /**
     * Test when display ASAP is enabled the manager will attempt to display the pending in-app message when the
     * next activity is resumed.
     */
    @Test
    public void testActivityResumedShowAsap() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Set the pending in-app message before setting display ASAP enabled
        inAppMessageManager.setPendingMessage(message);

        // Enable showing message ASAP
        inAppMessageManager.setDisplayAsapEnabled(true);

        // Set the current, resumed activity
        inAppMessageManager.onActivityResumed(mockActivity);

        runMainLooperTasks();

        // Verify a fragment was added
        verify(transaction).setCustomAnimations(anyInt(), eq(0));
        verify(transaction).add(eq(android.R.id.content), any(InAppMessageFragment.class), eq("com.urbanairship.in_app_fragment"));
    }

    /**
     * Test when the app foregrounds it tries to show the pending in-app message on next activity resume.
     */
    @Test
    public void testActivityResume() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Set the pending in-app message before setting show ASAP enabled
        inAppMessageManager.setPendingMessage(message);

        // Notify foreground
        inAppMessageManager.onForeground();

        // Set the current, resumed activity
        inAppMessageManager.onActivityResumed(mockActivity);

        runMainLooperTasks();

        // Verify a fragment was added
        verify(transaction).setCustomAnimations(anyInt(), eq(0));
        verify(transaction).add(eq(android.R.id.content), any(InAppMessageFragment.class), eq("com.urbanairship.in_app_fragment"));
    }

    /**
     * Test set pending in-app message generates a replace resolution event if a previous, not shown
     * message exists.
     */
    @Test
    public void testSetPendingMessageGeneratesReplaceEvent() {
        final InAppMessage otherMessage = new InAppMessage.Builder().setId(UUID.randomUUID().toString()).create();
        // Set the pending in-app message
        inAppMessageManager.setPendingMessage(message);

        // Set another pending in-app message
        inAppMessageManager.setPendingMessage(otherMessage);

        verify(mockAnalytics).addEvent(argThat(new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Event event) {
                if (!(event instanceof ResolutionEvent)) {
                    return false;
                }

                try {
                    EventTestUtils.validateEventValue(event, "id", message.getId());
                    EventTestUtils.validateNestedEventValue(event, "resolution", "type", "replaced");
                    EventTestUtils.validateNestedEventValue(event, "resolution", "replacement_id", otherMessage.getId());
                } catch (JSONException e) {
                    return false;
                }
                return true;
            }
        }));
    }

    /**
     * Test set pending in-app message does not generate a replace resolution if the event is already
     * being displayed.
     */
    @Test
    public void testSetPendingNoReplaceEvent() {
        final InAppMessage otherMessage = new InAppMessage.Builder().setId(UUID.randomUUID().toString()).create();

        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Set the pending in-app message
        inAppMessageManager.setPendingMessage(message);

        // Show it
        assertTrue(inAppMessageManager.showPendingMessage(mockActivity));

        // Set another pending in-app message
        inAppMessageManager.setPendingMessage(otherMessage);

        verify(mockAnalytics, never()).addEvent(argThat(new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Event event) {
                return event instanceof ResolutionEvent;
            }
        }));
    }

    /**
     * Test init checks and removes a expired pending in app message.
     */
    @Test
    public void testInit() {
        final InAppMessage expired = new InAppMessage.Builder()
                .setExpiry(0l)
                .setId(UUID.randomUUID().toString())
                .create();

        // Set the pending in-app message
        inAppMessageManager.setPendingMessage(expired);

        inAppMessageManager.init();

        // The pending in-app message should be removed
        assertNull(inAppMessageManager.getPendingMessage());

        // Verify the right event was added
        verify(mockAnalytics).addEvent(argThat(new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Event event) {
                if (!(event instanceof ResolutionEvent)) {
                    return false;
                }

                try {
                    EventTestUtils.validateEventValue(event, "id", expired.getId());
                    EventTestUtils.validateNestedEventValue(event, "resolution", "type", "expired");
                } catch (JSONException e) {
                    return false;
                }
                return true;
            }
        }));
    }

    /**
     * Test showing the pending in-app message in a container that does not exist.
     */
    @Test
    public void testShowMessageInvalidContainerId() {
        // Set up a mocked transaction
        FragmentTransaction transaction = mock(StubbedFragmentTransaction.class, CALLS_REAL_METHODS);
        when(mockActivity.getFragmentManager().beginTransaction()).thenReturn(transaction);

        // Return null when finding the custom container
        when(mockActivity.findViewById(android.R.id.custom)).thenReturn(null);

        // Set the pending message
        inAppMessageManager.setPendingMessage(message);

        // Try to display it
        assertFalse(inAppMessageManager.showPendingMessage(mockActivity, android.R.id.custom, android.R.animator.fade_in, android.R.animator.fade_out));
    }

    /**
     * Helper method to run all the tasks in the main looper.
     */
    private static void runMainLooperTasks() {
        // Get the looper
        ShadowLooper looper = Shadows.shadowOf(Looper.getMainLooper());

        // Run any tasks in its queue
        looper.runToEndOfTasks();
    }
}
