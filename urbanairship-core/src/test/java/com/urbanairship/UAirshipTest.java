/* Copyright Airship and Contributors */

package com.urbanairship;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.util.List;

import androidx.annotation.NonNull;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertNotNull;

public class UAirshipTest extends BaseTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private AirshipConfigOptions configOptions;
    private Looper looper;
    private Application application;

    @Before
    public void setup() {
        looper = Looper.myLooper();

        configOptions = new AirshipConfigOptions.Builder()
                .setProductionAppKey("0000000000000000000000")
                .setProductionAppSecret("0000000000000000000000")
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
            public void onAirshipReady(@NonNull UAirship airship) {
                super.onAirshipReady(airship);
                assertFalse("Take off callback should be called first", testCallback.onReadyCalled);
            }
        };

        UAirship.takeOff(application, configOptions, takeOffCallback);

        // Block until its ready
        UAirship.shared();
        Shadows.shadowOf(looper).runToEndOfTasks();

        assertTrue(testCallback.onReadyCalled);
        assertTrue(takeOffCallback.onReadyCalled);
        assertFalse(cancelCallback.onReadyCalled);

        // Verify the airship ready intent was fired
        List<Intent> intents = Shadows.shadowOf(RuntimeEnvironment.application).getBroadcastIntents();
        assertEquals(intents.size(), 1);
        assertEquals(intents.get(0).getAction(), UAirship.ACTION_AIRSHIP_READY);
        assertNull(intents.get(0).getExtras());
    }

    /**
     * Test takeOff with valid application and config options calls the correct callbacks.
     * Also tests the AIRSHIP_READY broadcast is extended.
     */
    @Test
    public void testAsyncTakeOffWithExtendedBroadcasts() {
        final TestCallback testCallback = new TestCallback();
        UAirship.shared(testCallback);

        TestCallback cancelCallback = new TestCallback();
        Cancelable cancelable = UAirship.shared(cancelCallback);
        cancelable.cancel();

        TestCallback takeOffCallback = new TestCallback() {
            @Override
            public void onAirshipReady(@NonNull UAirship airship) {
                super.onAirshipReady(airship);
                assertFalse("Take off callback should be called first", testCallback.onReadyCalled);
            }
        };

        configOptions = new AirshipConfigOptions.Builder()
                .setProductionAppKey("0000000000000000000000")
                .setProductionAppSecret("0000000000000000000000")
                .setInProduction(true)
                .setExtendedBroadcastsEnabled(true)
                .build();

        UAirship.takeOff(application, configOptions, takeOffCallback);

        // Block until its ready
        UAirship.shared();
        Shadows.shadowOf(looper).runToEndOfTasks();

        assertTrue(testCallback.onReadyCalled);
        assertTrue(takeOffCallback.onReadyCalled);
        assertFalse(cancelCallback.onReadyCalled);

        // Verify the airship ready intent was fired
        List<Intent> intents = Shadows.shadowOf(RuntimeEnvironment.application).getBroadcastIntents();
        assertEquals(intents.size(), 1);
        assertEquals(intents.get(0).getAction(), UAirship.ACTION_AIRSHIP_READY);
        Bundle extras = intents.get(0).getExtras();
        assertNotNull(extras);
        assertEquals(extras.getInt("payload_version"),1);
        assertEquals(extras.getString("app_key"),"0000000000000000000000");
        assertTrue(extras.containsKey("channel_id"));
        assertEquals(extras.getString("channel_id"), null);
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
     * Test enabling the DataCollectionOptIn Key in AirshipConfig, and check that DataCollection
     * is disabled at SDK launch
     */
    @Test
    public void testDataCollectionOptInEnabled() {
        configOptions = new AirshipConfigOptions.Builder()
                .setProductionAppKey("0000000000000000000000")
                .setProductionAppSecret("0000000000000000000000")
                .setInProduction(true)
                .setDataCollectionOptInEnabled(true)
                .build();

        UAirship.takeOff(application, configOptions);

        assertFalse(UAirship.shared().isDataCollectionEnabled());
    }

    /**
     * Test disabling the DataCollectionOptIn Key in AirshipConfig, and check that DataCollection
     * is enabled at SDK launch
     */
    @Test
    public void testDataCollectionOptInDisabled() {
        configOptions = new AirshipConfigOptions.Builder()
                .setProductionAppKey("0000000000000000000000")
                .setProductionAppSecret("0000000000000000000000")
                .setInProduction(true)
                .setDataCollectionOptInEnabled(false)
                .build();

        UAirship.takeOff(application, configOptions);

        assertTrue(UAirship.shared().isDataCollectionEnabled());
    }

    /**
     * Test enabling the DataCollection
     */
    @Test
    public void testSetDataCollectionEnabled() {
        UAirship.takeOff(application, configOptions);

        UAirship.shared().setDataCollectionEnabled(true);
        assertTrue(UAirship.shared().isDataCollectionEnabled());
    }

    /**
     * Test disabling the DataCollection
     */
    @Test
    public void testSetDataCollectionDisabled() {
        UAirship.takeOff(application, configOptions);

        UAirship.shared().setDataCollectionEnabled(false);
        assertFalse(UAirship.shared().isDataCollectionEnabled());
    }


    /**
     * Helper callback for testing.
     */
    static class TestCallback implements UAirship.OnReadyCallback {

        volatile boolean onReadyCalled = false;

        @Override
        public void onAirshipReady(@NonNull UAirship airship) {
            onReadyCalled = true;
        }

    }

}
