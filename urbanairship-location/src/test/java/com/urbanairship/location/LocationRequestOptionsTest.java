/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.os.Parcel;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
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
        LocationRequestOptions.newBuilder().setMinTime(-1, TimeUnit.MILLISECONDS);
    }

    /**
     * Test creating a new request options throws exceptions for invalid
     * minDistance.
     */
    @Test
    public void testLocationRequestOptionInvalidMinDistance() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("minDistance must be greater or equal to 0");
        LocationRequestOptions.newBuilder().setMinDistance(-1);
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
        LocationRequestOptions.newBuilder().setPriority(-1);
    }

    /**
     * Test creating a JsonValue from a LocationRequestOptions instance.
     */
    @Test
    public void testToJsonValue() {
        LocationRequestOptions options = LocationRequestOptions.newBuilder()
                                                               .setPriority(LocationRequestOptions.PRIORITY_LOW_POWER)
                                                               .setMinDistance(44.4f)
                                                               .setMinTime(1111, TimeUnit.MILLISECONDS)
                                                               .build();

        JsonValue value = options.toJsonValue();
        assertEquals(44.4f, value.getMap().get(LocationRequestOptions.MIN_DISTANCE_KEY).getNumber().floatValue());
        assertEquals(1111, value.getMap().get(LocationRequestOptions.MIN_TIME_KEY).getLong(0));
        assertEquals(LocationRequestOptions.PRIORITY_LOW_POWER, value.getMap().get(LocationRequestOptions.PRIORITY_KEY).getInt(-1));
    }

    /**
     * Test creating a LocationRequestOptions from a JsonString.
     */
    @Test
    public void testParseJson() throws JsonException {
        LocationRequestOptions original = LocationRequestOptions.newBuilder()
                                                                .setPriority(LocationRequestOptions.PRIORITY_LOW_POWER)
                                                                .setMinDistance(44.4f)
                                                                .setMinTime(1111, TimeUnit.MILLISECONDS)
                                                                .build();

        LocationRequestOptions fromJson = LocationRequestOptions.fromJson(original.toJsonValue());
        assertEquals(original, fromJson);
    }

    /**
     * Test saving and reading LocationRequestOptions from a parcel.
     */
    @Test
    public void testParcelable() {
        LocationRequestOptions original = LocationRequestOptions.newBuilder()
                                                                .setPriority(LocationRequestOptions.PRIORITY_LOW_POWER)
                                                                .setMinDistance(44.4f)
                                                                .setMinTime(1111, TimeUnit.MILLISECONDS)
                                                                .build();

        // Write the options to the parcel
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);

        // Reset the parcel so we can read it
        parcel.setDataPosition(0);

        // Create the options from the parcel
        LocationRequestOptions fromParcel = LocationRequestOptions.CREATOR.createFromParcel(parcel);

        // Validate the data
        assertEquals(original, fromParcel);
    }

}
