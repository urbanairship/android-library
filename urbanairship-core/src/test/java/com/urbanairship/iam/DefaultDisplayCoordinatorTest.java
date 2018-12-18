package com.urbanairship.iam;

import android.app.Activity;
import android.os.Looper;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;

public class DefaultDisplayCoordinatorTest extends BaseTestCase {

    private TestActivityMonitor activityMonitor;
    private DefaultDisplayCoordinator coordinator;
    private InAppMessage messageOne;
    private InAppMessage messageTwo;
    private ShadowLooper mainLooper;

    @Before
    public void setup() {
        activityMonitor = new TestActivityMonitor();
        coordinator = new DefaultDisplayCoordinator(activityMonitor);
        mainLooper = Shadows.shadowOf(Looper.getMainLooper());

        messageOne = InAppMessage.newBuilder()
                .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                .setId("message id one")
                .build();

        messageTwo = InAppMessage.newBuilder()
                .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                .setId("message id two")
                .build();
    }

    @Test
    public void isReady() {
        assertTrue(coordinator.isReady(messageOne, false));
        assertTrue(coordinator.isReady(messageOne, true));
        assertTrue(coordinator.isReady(messageTwo, false));
        assertTrue(coordinator.isReady(messageTwo, true));
    }

    @Test
    public void onDisplayStarted() {
        Activity activity = new Activity();
        coordinator.onDisplayStarted(activity, messageOne);

        assertFalse(coordinator.isReady(messageOne, false));
        assertFalse(coordinator.isReady(messageOne, true));
        assertFalse(coordinator.isReady(messageTwo, false));
        assertFalse(coordinator.isReady(messageTwo, true));
    }

    @Test
    public void onAllowDisplayNoCurrentMessage() {
        Activity activity = new Activity();
        assertTrue(coordinator.onAllowDisplay(activity, messageOne));

        assertFalse(coordinator.onAllowDisplay(activity, messageTwo));

        assertFalse(coordinator.isReady(messageOne, false));
        assertFalse(coordinator.isReady(messageOne, true));
        assertFalse(coordinator.isReady(messageTwo, false));
        assertFalse(coordinator.isReady(messageTwo, true));
    }

    @Test
    public void onAllowDisplayCurrentMessage() {
        Activity activity = new Activity();
        coordinator.onDisplayStarted(activity, messageTwo);
        assertTrue(coordinator.onAllowDisplay(activity, messageTwo));

        assertFalse(coordinator.onAllowDisplay(activity, messageOne));

        assertFalse(coordinator.isReady(messageOne, false));
        assertFalse(coordinator.isReady(messageOne, true));
        assertFalse(coordinator.isReady(messageTwo, false));
        assertFalse(coordinator.isReady(messageTwo, true));
    }

    @Test
    public void onDisplayFinished() {
        Activity activity = new Activity();
        coordinator.onDisplayStarted(activity, messageTwo);
        coordinator.onDisplayFinished(messageTwo);

        // Display should still be locked until the interval
        assertFalse(coordinator.isReady(messageOne, false));
        assertFalse(coordinator.isReady(messageTwo, false));

        // Message redisplays should bypass the lock
        assertTrue(coordinator.isReady(messageOne, true));
        assertTrue(coordinator.isReady(messageTwo, true));

        // Advance the looper to free up the display lock
        mainLooper.runToEndOfTasks();

        // All messages should be ready
        assertTrue(coordinator.isReady(messageOne, false));
        assertTrue(coordinator.isReady(messageOne, true));
        assertTrue(coordinator.isReady(messageTwo, false));
        assertTrue(coordinator.isReady(messageTwo, true));
    }

    @Test
    public void activityStopped() {
        // Resume an activity
        Activity activity = new Activity();
        activityMonitor.startActivity(activity);
        activityMonitor.resumeActivity(activity);

        // Display message one
        assertTrue(coordinator.isReady(messageOne, false));
        coordinator.onDisplayStarted(activity, messageOne);

        // Display should be locked with an active message
        assertFalse(coordinator.isReady(messageOne, false));
        assertFalse(coordinator.isReady(messageOne, true));
        assertFalse(coordinator.isReady(messageTwo, false));
        assertFalse(coordinator.isReady(messageTwo, true));

        // Stop the activity
        activityMonitor.pauseActivity(activity);
        activityMonitor.stopActivity(activity);

        // Display should still be locked until the interval
        assertFalse(coordinator.isReady(messageOne, false));
        assertFalse(coordinator.isReady(messageTwo, false));

        // Message redisplays should bypass the lock
        assertTrue(coordinator.isReady(messageOne, true));
        assertTrue(coordinator.isReady(messageTwo, true));

        // Advance the looper to free up the display lock
        mainLooper.runToEndOfTasks();

        // All messages should be ready
        assertTrue(coordinator.isReady(messageOne, false));
        assertTrue(coordinator.isReady(messageOne, true));
        assertTrue(coordinator.isReady(messageTwo, false));
        assertTrue(coordinator.isReady(messageTwo, true));
    }
}