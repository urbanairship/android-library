/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.os.Parcel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class ActionValueTest extends BaseTestCase {

    /**
     * Test saving and reading a ActionValue from a parcel.
     */
    @Test
    public void testParcelable() throws JsonException {

        JsonValue jsonValue = JsonValue.wrap(new Object[] { "value", 1, "another-value" });
        ActionValue actionValue = new ActionValue(jsonValue);

        // Write the push message to a parcel
        Parcel parcel = Parcel.obtain();
        actionValue.writeToParcel(parcel, 0);

        // Reset the parcel so we can read it
        parcel.setDataPosition(0);

        // Create the message from the parcel
        ActionValue fromParcel = ActionValue.CREATOR.createFromParcel(parcel);

        // Validate the data
        assertEquals(actionValue, fromParcel);
    }
}
