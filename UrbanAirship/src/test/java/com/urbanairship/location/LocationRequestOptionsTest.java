package com.urbanairship.location;

import com.urbanairship.RobolectricGradleTestRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(RobolectricGradleTestRunner.class)
public class LocationRequestOptionsTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    /**
     * Test creating a new request options throws exceptions for invalid
     * minTime.
     */
    @Test
    public void testLocationRequestOptionInvalidMinTime() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("minTime must be greater or equal to 0");
        new LocationRequestOptions.Builder().setMinTime(-1, TimeUnit.MILLISECONDS);
    }

    /**
     * Test creating a new request options throws exceptions for invalid
     * minDistance.
     */
    @Test
    public void testLocationRequestOptionInvalidMinDistance() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("minDistance must be greater or equal to 0");
        new LocationRequestOptions.Builder().setMinDistance(-1);
    }


    /**
     * Test creating a new request options throws exceptions for invalid
     * priority.
     */
    @Test
    public void testLocationRequestOptionInvalidPriority() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Priority can only be either " +
                "PRIORITY_HIGH_ACCURACY, PRIORITY_BALANCED_POWER_ACCURACY, " +
                "PRIORITY_LOW_POWER, or PRIORITY_NO_POWER");
        new LocationRequestOptions.Builder().setPriority(-1);
    }
}
