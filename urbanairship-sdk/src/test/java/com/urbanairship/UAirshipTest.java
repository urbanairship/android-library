/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class UAirshipTest extends BaseTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    AirshipConfigOptions configOptions;
    ShadowLooper looper;
    Application application;

    @Before
    public void setup() {
        looper = Shadows.shadowOf(Looper.myLooper());

        configOptions = new AirshipConfigOptions.Builder()
                .setProductionAppKey("appKey")
                .setProductionAppSecret("appSecret")
                .setInProduction(true)
                .build();

        application = TestApplication.getApplication();

        // TestApplication automatically sets up airship for other tests, clean it up with land.
        UAirship.land();
    }

    @After
    public void cleanup() {
        UAirship.land();
    }

    /**
     * Test takeOff with valid application and config options calls the correct callbacks.
     */
    @Test
    public void testAsyncTakeOff() {
        final TestCallback testCallback = new TestCallback();
        UAirship.shared(testCallback);

        TestCallback cancelCallback = new TestCallback();
        Cancelable cancelable = UAirship.shared(cancelCallback);
        cancelable.cancel();

        TestCallback takeOffCallback = new TestCallback() {
            @Override
            public void onAirshipReady(UAirship airship) {
                super.onAirshipReady(airship);
                assertFalse("Take off callback should be called first", testCallback.onReadyCalled);
            }
        };

        UAirship.takeOff(application, configOptions, takeOffCallback);

        // Block until its ready
        UAirship.shared();
        looper.runToEndOfTasks();

        assertTrue(testCallback.onReadyCalled);
        assertTrue(takeOffCallback.onReadyCalled);
        assertFalse(cancelCallback.onReadyCalled);

        // Verify the airship ready intent was fired
        List<Intent> intents = ShadowApplication.getInstance().getBroadcastIntents();
        assertEquals(intents.size(), 1);
        assertEquals(intents.get(0).getAction(), UAirship.ACTION_AIRSHIP_READY);
    }

    /**
     * Test that we throw an illegal argument exception when takeoff is called
     * with a null application.
     */
    @Test
    public void testTakeOffNullApplication() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Application argument must not be null");

        UAirship.takeOff(null);
    }
    /**
     * Test that we throw an illegal state exception when shared() is called before
     * takeOff
     */
    @Test
    public void testSharedBeforeTakeOff() {
        exception.expect(IllegalStateException.class);
        exception.expectMessage("Take off must be called before shared()");
        UAirship.shared();
    }

    /**
     * Helper callback for testing.
     */
    static class TestCallback implements UAirship.OnReadyCallback {

        volatile boolean onReadyCalled = false;

        @Override
        public void onAirshipReady(UAirship airship) {
            onReadyCalled = true;
        }
    }
}
