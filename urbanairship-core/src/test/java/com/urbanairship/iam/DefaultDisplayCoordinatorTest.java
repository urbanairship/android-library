package com.urbanairship.iam;

import android.os.Looper;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultDisplayCoordinatorTest extends BaseTestCase {

    private DefaultDisplayCoordinator coordinator;
    private InAppMessage messageOne;
    private InAppMessage messageTwo;
    private Looper mainLooper;

    @Before
    public void setup() {
        coordinator = new DefaultDisplayCoordinator();
        mainLooper = Looper.getMainLooper();

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
        assertTrue(coordinator.isReady());
    }

    @Test
    public void onDisplay() {
        assertTrue(coordinator.isReady());
        coordinator.onDisplayStarted(messageOne);
        assertFalse(coordinator.isReady());
    }

    @Test
        public void onDisplayFinished() { ;
        assertTrue(coordinator.isReady());
        coordinator.onDisplayStarted(messageTwo);

        assertFalse(coordinator.isReady());
        coordinator.onDisplayFinished(messageTwo);

        // Display should still be locked until the interval
        assertFalse(coordinator.isReady());

        // Advance the looper to free up the display lock
        Shadows.shadowOf(mainLooper).runToEndOfTasks();

        // Display should be ready
        assertTrue(coordinator.isReady());
    }

}