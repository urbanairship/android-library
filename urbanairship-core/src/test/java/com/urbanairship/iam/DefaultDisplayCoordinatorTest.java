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
        assertFalse(coordinator.isReady());

        Activity activity = new Activity();
        activityMonitor.resumeActivity(activity);

        assertTrue(coordinator.isReady());
    }

    @Test
    public void onDisplay() {
        Activity activity = new Activity();
        activityMonitor.resumeActivity(activity);

        assertTrue(coordinator.isReady());
        coordinator.onDisplayStarted(messageOne);
        assertFalse(coordinator.isReady());
    }

    @Test
    public void onDisplayFinished() {
        Activity activity = new Activity();
        activityMonitor.resumeActivity(activity);

        assertTrue(coordinator.isReady());
        coordinator.onDisplayStarted(messageTwo);

        assertFalse(coordinator.isReady());
        coordinator.onDisplayFinished(messageTwo);

        // Display should still be locked until the interval
        assertFalse(coordinator.isReady());

        // Advance the looper to free up the display lock
        mainLooper.runToEndOfTasks();

        // Display should be ready
        assertTrue(coordinator.isReady());
    }
}